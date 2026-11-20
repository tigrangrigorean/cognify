const COOKIE = "cognify_refresh";
const actions = new Set([
  "register",
  "login",
  "refresh",
  "logout",
  "forgot-password",
  "reset-password",
]);
export async function handleAuth(request, action, options = {}) {
  const origin =
    options.origin ?? process.env.AUTH_ORIGIN ?? new URL(request.url).origin;
  const backend =
    options.backend ?? process.env.BACKEND_URL ?? "http://localhost:8080";
  const secure = options.secure ?? process.env.NODE_ENV === "production";
  const upstream = options.fetch ?? fetch;
  const cookie = (token, maxAge) =>
    `${COOKIE}=${token}; Path=/api/auth; HttpOnly; SameSite=Strict; Max-Age=${maxAge}${secure ? "; Secure" : ""}`;
  const reply = (body, status = 200, setCookie) =>
    new Response(body === null ? null : JSON.stringify(body), {
      status,
      headers: {
        "Content-Type": "application/json",
        "Cache-Control": "no-store",
        ...(setCookie ? { "Set-Cookie": setCookie } : {}),
      },
    });
  if (!actions.has(action))
    return reply({ detail: "Unknown authentication action" }, 404);
  // Custom header + exact Origin check protect cookie-authenticated mutations and login CSRF.
  if (
    request.headers.get("Origin") !== origin ||
    request.headers.get("X-Cognify-CSRF") !== "1" ||
    !request.headers.get("Content-Type")?.startsWith("application/json")
  )
    return reply({ detail: "Invalid request origin" }, 403);
  const refreshToken = (request.headers.get("Cookie") || "")
    .split(";")
    .map((x) => x.trim())
    .find((x) => x.startsWith(COOKIE + "="))
    ?.slice(COOKIE.length + 1);
  let body;
  if (action === "refresh" || action === "logout") {
    if (!refreshToken)
      return reply(
        action === "logout" ? null : { detail: "Session expired" },
        action === "logout" ? 200 : 401,
        cookie("", 0),
      );
    body = { refreshToken };
  } else {
    try {
      body = await request.json();
    } catch {
      return reply({ detail: "Invalid JSON" }, 400);
    }
  }
  try {
    const result = await upstream(backend + "/api/auth/" + action, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
      cache: "no-store",
      signal: AbortSignal.timeout(15000),
    });
    const raw = await result.text();
    const data = raw ? JSON.parse(raw) : null;
    if (!result.ok)
      return reply(
        { detail: data?.detail || "Authentication failed" },
        result.status,
        result.status === 401 && action === "refresh"
          ? cookie("", 0)
          : undefined,
      );
    if (action === "logout" || action === "reset-password")
      return reply(null, 200, cookie("", 0));
    if (action === "forgot-password") return reply(data);
    if (
      !data?.accessToken ||
      !data?.refreshToken ||
      !/^[A-Za-z0-9_-]+$/.test(data.refreshToken)
    )
      return reply({ detail: "Invalid authentication response" }, 502);
    // Never expose the refresh credential in browser-readable JSON.
    return reply(
      { accessToken: data.accessToken, name: data.name },
      200,
      cookie(data.refreshToken, 604800),
    );
  } catch {
    return reply({ detail: "Authentication service unavailable" }, 502);
  }
}
