const BASE = import.meta.env.VITE_API_URL ?? "";

function csrfToken() {
  const m = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
  return m ? decodeURIComponent(m[1]) : "";
}

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

export async function createLesson(lesson) {
  const res = await fetch(`${BASE}/api/lessons`, {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      "X-XSRF-TOKEN": csrfToken(),
    },
    body: JSON.stringify(lesson),
  });

  if (!res.ok) {
    const err = new Error(`Request failed (${res.status})`);
    err.status = res.status;
    throw err;
  }
  return res.json().catch(() => ({}));
}