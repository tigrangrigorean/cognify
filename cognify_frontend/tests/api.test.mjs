import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
const source = await readFile(
  new URL("../services/api.js", import.meta.url),
  "utf8",
);
async function fresh() {
  return import(
    "data:text/javascript;base64," +
      Buffer.from(source + "\n//" + Math.random()).toString("base64")
  );
}
const response = (data, status = 200) =>
  new Response(data === null ? null : JSON.stringify(data), { status });

test("API sends bearer token and preserves request body", async () => {
  const { api, setToken } = await fresh();
  setToken("test-token");
  global.fetch = async (url, options) => {
    assert.equal(url, "/api/documents");
    assert.equal(options.headers.Authorization, "Bearer test-token");
    assert.equal(options.method, "POST");
    assert.equal(options.body, "{}");
    return response({ id: "document" });
  };
  assert.deepEqual(await api("/documents", { method: "POST", body: "{}" }), {
    id: "document",
  });
});
test("API displays backend validation errors", async () => {
  const { api } = await fresh();
  global.fetch = async () =>
    response({ detail: "Invalid request fields" }, 400);
  await assert.rejects(api("/documents"), /Invalid request fields/);
});
test("Concurrent expired requests rotate refresh token once", async () => {
  const { api, setToken } = await fresh();
  setToken("expired");
  let rotations = 0;
  global.fetch = async (url, opts) => {
    if (url === "/api/auth/refresh") {
      rotations++;
      await new Promise((resolve) => setTimeout(resolve, 10));
      assert.equal(opts.body, "{}");
      assert.equal(opts.headers["X-Cognify-CSRF"], "1");
      return response({ accessToken: "fresh" });
    }
    return opts.headers.Authorization === "Bearer fresh"
      ? response({ ok: true })
      : response({}, 401);
  };
  const result = await Promise.all([api("/me"), api("/documents")]);
  assert.equal(rotations, 1);
  assert.deepEqual(result, [{ ok: true }, { ok: true }]);
});
test("204 response is accepted", async () => {
  const { api } = await fresh();
  global.fetch = async () => response(null, 204);
  assert.equal(await api("/documents/x", { method: "DELETE" }), null);
});

test("Concurrent session restoration rotates only once", async () => {
  const { resumeSession } = await fresh();
  let rotations = 0;
  global.fetch = async () => {
    rotations++;
    await new Promise((resolve) => setTimeout(resolve, 10));
    return response({ accessToken: "restored" });
  };
  await Promise.all([resumeSession(), resumeSession()]);
  assert.equal(rotations, 1);
});

test("Binary DOCX download refreshes authentication and preserves bytes", async () => {
  const { downloadBlob, setToken } = await fresh();
  setToken("expired");
  const bytes = new Uint8Array([80, 75, 3, 4, 0, 255]);
  global.fetch = async (url, opts) => {
    if (url === "/api/auth/refresh")
      return response({ accessToken: "renewed" });
    assert.equal(url, "/api/documents/example/export.docx");
    return opts.headers.Authorization === "Bearer renewed"
      ? new Response(bytes, {
          headers: {
            "Content-Type":
              "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          },
        })
      : response({}, 401);
  };
  assert.deepEqual(
    new Uint8Array(
      await (
        await downloadBlob("/documents/example/export.docx")
      ).arrayBuffer(),
    ),
    bytes,
  );
});
