import test from "node:test";
import assert from "node:assert/strict";
import { pendingOperation } from "../services/pending-operation.mjs";
function storage() {
  const map = new Map();
  global.sessionStorage = {
    getItem: (k) => map.get(k),
    setItem: (k, v) => map.set(k, v),
    removeItem: (k) => map.delete(k),
  };
  return map;
}
test("A financial retry reuses its key until completion, without persisting reasons", async () => {
  const map = storage();
  const first = await pendingOperation("/adjust", {
    reason: "private reason",
    amount: 100,
  });
  const retry = await pendingOperation("/adjust", {
    reason: "private reason",
    amount: 100,
  });
  assert.equal(first.requestKey, retry.requestKey);
  assert.equal(JSON.stringify([...map]).includes("private reason"), false);
  first.complete();
  const next = await pendingOperation("/adjust", {
    reason: "private reason",
    amount: 100,
  });
  assert.notEqual(first.requestKey, next.requestKey);
});
test("Different amounts and owners have independent financial request IDs", async () => {
  storage();
  const a = await pendingOperation("/owner-a", { amount: 100 });
  const b = await pendingOperation("/owner-b", { amount: 100 });
  const c = await pendingOperation("/owner-a", { amount: 200 });
  assert.notEqual(a.requestKey, b.requestKey);
  assert.notEqual(a.requestKey, c.requestKey);
});

test("Late completion of an old operation cannot clear a newer retry key", async () => {
  storage();
  const old = await pendingOperation("/payments", { packageId: "one" });
  old.complete();
  const next = await pendingOperation("/payments", { packageId: "one" });
  old.complete();
  const retry = await pendingOperation("/payments", { packageId: "one" });
  assert.equal(next.requestKey, retry.requestKey);
});
