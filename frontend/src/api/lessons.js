const BASE = import.meta.env.VITE_API_URL ?? "";

export async function fetchLessons({ search = "", page = 0, size = 10 }, signal) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (search) params.set("search", search);

  const res = await fetch(`${BASE}/api/lessons?${params}`, {
    credentials: "include",
    headers: { Accept: "application/json" },
    signal,
  });

  if (!res.ok) {
    const err = new Error(`Request failed (${res.status})`);
    err.status = res.status;
    throw err;
  }
  return res.json();
}