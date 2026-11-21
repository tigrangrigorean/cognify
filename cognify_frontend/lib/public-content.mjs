export async function readPublic(path) {
  const response = await fetch(
    `${process.env.BACKEND_URL || "http://localhost:8080"}/api/public/${path}`,
    { cache: "no-store", signal: AbortSignal.timeout(3000) },
  );
  if (!response.ok) throw Error("Public content unavailable");
  return response.json();
}
