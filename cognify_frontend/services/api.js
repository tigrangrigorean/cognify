let accessToken = "";
let refreshInFlight;
export function setToken(token) {
  accessToken = token;
}

async function refresh() {
  if (!refreshInFlight) {
    const rotate = async () => {
      const response = await fetch("/api/auth/refresh", {
        method: "POST",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json", "X-Cognify-CSRF": "1" },
        body: "{}",
      });
      if (!response.ok) {
        accessToken = "";
        throw new Error("Մուտք գործեք կրկին։");
      }
      const data = await response.json();
      accessToken = data.accessToken;
    };
    refreshInFlight = (
      typeof navigator !== "undefined" && navigator.locks
        ? navigator.locks.request("cognify-refresh", rotate)
        : rotate()
    ).finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

async function request(path, options = {}, retry = true) {
  const response = await fetch("/api" + path, {
    ...options,
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
      ...(path.startsWith("/auth/") ? { "X-Cognify-CSRF": "1" } : {}),
      ...(accessToken ? { Authorization: "Bearer " + accessToken } : {}),
      ...options.headers,
    },
  });
  if (response.status === 401 && retry && !path.startsWith("/auth/")) {
    await refresh();
    return request(path, options, false);
  }
  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    throw new Error(
      data.detail ||
        (response.status === 401
          ? "Մուտք գործեք կրկին։"
          : "Հարցումը չհաջողվեց։ Փորձեք կրկին։"),
    );
  }
  return response;
}

export async function resumeSession() {
  return refresh();
}

export async function api(path, options = {}) {
  const response = await request(path, options);
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}
export async function downloadBlob(path) {
  return (await request(path)).blob();
}
