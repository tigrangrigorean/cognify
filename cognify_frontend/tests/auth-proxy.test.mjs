import test from "node:test";
import assert from "node:assert/strict";
import { handleAuth } from "../lib/auth-proxy.mjs";
const origin = "https://cognify.example";
function req(body = {}, extra = {}) {
  return new Request(origin + "/api/auth/login", {
    method: "POST",
    headers: {
      Origin: origin,
      "Content-Type": "application/json",
      "X-Cognify-CSRF": "1",
      ...extra,
    },
    body: JSON.stringify(body),
  });
}
const payload = {
  accessToken: "access",
  refreshToken: "secret_refresh",
  name: "Անի",
};
test("Login sets Secure HttpOnly cookie and strips refresh token", async () => {
  const r = await handleAuth(req({ email: "test" }), "login", {
    origin,
    secure: true,
    fetch: async () => Response.json(payload),
  });
  assert.equal(r.status, 200);
  assert.deepEqual(await r.json(), { accessToken: "access", name: "Անի" });
  const cookie = r.headers.get("set-cookie");
  for (const expected of [
    "HttpOnly",
    "Secure",
    "SameSite=Strict",
    "Path=/api/auth",
  ])
    assert.ok(cookie.includes(expected));
  assert.equal(r.headers.get("cache-control"), "no-store");
});
test("Cross-origin and missing CSRF headers never reach backend", async () => {
  for (const extra of [
    { Origin: "https://evil.example" },
    { "X-Cognify-CSRF": "" },
  ]) {
    const r = await handleAuth(req({}, extra), "login", {
      origin,
      fetch: () => {
        throw new Error("Must not be reached");
      },
    });
    assert.equal(r.status, 403);
  }
});
test("Refresh uses cookie, ignoring frontend-supplied token", async () => {
  const r = await handleAuth(
    req(
      { refreshToken: "attacker" },
      { Cookie: "cognify_refresh=cookie_secret" },
    ),
    "refresh",
    {
      origin,
      fetch: async (url, options) => {
        assert.deepEqual(JSON.parse(options.body), {
          refreshToken: "cookie_secret",
        });
        return Response.json(payload);
      },
    },
  );
  assert.equal(r.status, 200);
  assert.equal((await r.json()).refreshToken, undefined);
});
test("Revoked refresh clears cookie; upstream outage does not silently log out", async () => {
  const request = () => req({}, { Cookie: "cognify_refresh=old" });
  const revoked = await handleAuth(request(), "refresh", {
    origin,
    fetch: async () => new Response(null, { status: 401 }),
  });
  assert.equal(revoked.status, 401);
  assert.match(revoked.headers.get("set-cookie"), /Max-Age=0/);
  const outage = await handleAuth(request(), "refresh", {
    origin,
    fetch: async () => {
      throw Error("timeout");
    },
  });
  assert.equal(outage.status, 502);
  assert.equal(outage.headers.get("set-cookie"), null);
});
test("Logout revokes upstream and clears cookie", async () => {
  let invoked = false;
  const r = await handleAuth(
    req({}, { Cookie: "cognify_refresh=old" }),
    "logout",
    {
      origin,
      fetch: async () => {
        invoked = true;
        return new Response(null, { status: 200 });
      },
    },
  );
  assert.equal(r.status, 200);
  assert.ok(invoked);
  assert.match(r.headers.get("set-cookie"), /Max-Age=0/);
});

test("Recovery forwards generic success without requiring auth tokens", async () => {
  const r = await handleAuth(
    req({ email: "test@example.com" }),
    "forgot-password",
    {
      origin,
      fetch: async () => Response.json({ message: "Check your email" }),
    },
  );
  assert.equal(r.status, 200);
  assert.equal(r.headers.get("set-cookie"), null);
  assert.equal((await r.json()).message, "Check your email");
});
test("Unavailable mail returns explicit 503", async () => {
  const r = await handleAuth(
    req({ email: "test@example.com" }),
    "forgot-password",
    {
      origin,
      fetch: async () =>
        Response.json({ detail: "Mail unavailable" }, { status: 503 }),
    },
  );
  assert.equal(r.status, 503);
  assert.equal((await r.json()).detail, "Mail unavailable");
});
test("Successful password reset clears old session cookie", async () => {
  const r = await handleAuth(
    req({ token: "reset", newPassword: "long-password" }),
    "reset-password",
    { origin, fetch: async () => new Response(null, { status: 200 }) },
  );
  assert.equal(r.status, 200);
  assert.match(r.headers.get("set-cookie"), /Max-Age=0/);
});
