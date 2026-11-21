// Preserve financial request IDs across a timeout/reload without storing form data.
export async function pendingOperation(scope, payload) {
  const digest = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(JSON.stringify([scope, payload])),
  );
  const storageKey =
    "cognify:pending:" +
    Array.from(new Uint8Array(digest), (n) =>
      n.toString(16).padStart(2, "0"),
    ).join("");
  let requestKey = sessionStorage.getItem(storageKey);
  if (!requestKey) {
    requestKey = crypto.randomUUID();
    sessionStorage.setItem(storageKey, requestKey);
  }
  return {
    requestKey,
    complete() {
      if (sessionStorage.getItem(storageKey) === requestKey)
        sessionStorage.removeItem(storageKey);
    },
  };
}
