import { apiFetch } from "./http.js";

async function readErrorMessage(res) {
  const text = await res.text().catch(() => "");
  if (!text) return `Request failed (${res.status})`;
  try {
    const data = JSON.parse(text);
    if (data && typeof data.message === "string" && data.message) return data.message;
  } catch {
    // fall through
  }
  return text;
}

// Highlight categories are stored as collections in core_collection
// on the server, keyed to the logged-in user's session.
export async function fetchCollections() {
  const res = await apiFetch("/api/collections", {
    headers: { Accept: "application/json" },
  });

  if (!res.ok) {
    const err = new Error(await readErrorMessage(res));
    err.status = res.status;
    throw err;
  }
  return res.json();
}

// collection: { name, color }
export async function createCollection(collection) {
  const res = await apiFetch("/api/collections", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify(collection),
  });

  if (!res.ok) {
    const err = new Error(await readErrorMessage(res));
    err.status = res.status;
    throw err;
  }
  return res.json().catch(() => ({}));
}
