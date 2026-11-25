const BASE = import.meta.env.VITE_API_URL ?? "";
let rememberedToken = "";

export function endpoint(path) {
  const normalized = path.startsWith("/") ? path : `/${path}`;
  if (!BASE) return normalized;
  return `${BASE.replace(/\/$/, "")}${normalized}`;
}

function readCsrfCookie() {
  const prefix = "XSRF-TOKEN=";
  const item = document.cookie.split(";").map((part) => part.trim()).find((part) => part.startsWith(prefix));
  if (!item) return "";
  try {
    return decodeURIComponent(item.slice(prefix.length));
  } catch {
    return item.slice(prefix.length);
  }
}

async function ensureCsrfToken() {
  if (readCsrfCookie() || rememberedToken) return;
  try {
    const res = await fetch(endpoint("/api/csrf"), { credentials: "include" });
    if (!res.ok) return;
    const data = await res.json().catch(() => null);
    if (data?.token) rememberedToken = data.token;
  } catch {
    // The next request will still fail with a clear 403 if CSRF is unavailable.
  }
}

/** Fetch wrapper that always uses the session cookie and CSRF header. */
export async function apiFetch(path, init = {}) {
  const url = typeof path === "string" ? endpoint(path) : path;
  const isCsrfRequest = String(url).includes("/api/csrf");
  if (!isCsrfRequest) await ensureCsrfToken();

  const headers = new Headers(init.headers || {});
  const method = (init.method || "GET").toUpperCase();
  const token = readCsrfCookie() || rememberedToken;
  if (token && !["GET", "HEAD", "OPTIONS"].includes(method)) {
    headers.set("X-XSRF-TOKEN", token);
  }

  return fetch(url, { ...init, headers, credentials: "include" });
}
