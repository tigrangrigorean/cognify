import test from "node:test";
import assert from "node:assert/strict";
import { readPublic } from "../lib/public-content.mjs";
test("Server CMS fetch never caches stale public content", async () => {
  global.fetch = async (url, options) => {
    assert.ok(url.endsWith("/api/public/content"));
    assert.equal(options.cache, "no-store");
    assert.ok(options.signal);
    return Response.json({ seoTitle: "Updated title" });
  };
  assert.deepEqual(await readPublic("content"), { seoTitle: "Updated title" });
});
test("Server CMS rejects a failed backend instead of treating errors as content", async () => {
  global.fetch = async () =>
    Response.json({ detail: "Unavailable" }, { status: 503 });
  await assert.rejects(readPublic("blocks"), /Public content unavailable/);
});
