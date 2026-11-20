let accessToken = "";
let refreshInFlight;
export function setToken(token) {
  accessToken = token;
}

async function refresh() {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      const refreshToken = sessionStorage.getItem("refresh");
      if (!refreshToken) throw new Error("Մուտք գործեք կրկին։");
      const response = await fetch("/api/auth/refresh", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken }),
      });
      if (!response.ok) {
        sessionStorage.removeItem("refresh");
        accessToken = "";
        throw new Error("Մուտք գործեք կրկին։");
      }
      const data = await response.json();
      accessToken = data.accessToken;
      sessionStorage.setItem("refresh", data.refreshToken);
    })().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

export async function api(path, options = {}, retry = true) {
  const response = await fetch("/api" + path, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(accessToken ? { Authorization: "Bearer " + accessToken } : {}),
      ...options.headers,
    },
  });
  if (response.status === 401 && retry && !path.startsWith("/auth/")) {
    await refresh();
    return api(path, options, false);
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
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

export async function resumeSession() {
  return refresh();
}
