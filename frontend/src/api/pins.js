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

// Pins = highlighted text. They are stored in core_pin on the server,
// keyed to the logged-in user's session (no userId params anywhere).
export async function fetchPins({ lessonId } = {}) {
  const path = lessonId != null ? `/api/pins?lessonId=${lessonId}` : "/api/pins";
  const res = await apiFetch(path, {
    headers: { Accept: "application/json" },
  });

  if (!res.ok) {
    const err = new Error(await readErrorMessage(res));
    err.status = res.status;
    throw err;
  }
  return res.json();
}

// pin: { lessonId, sectionId, selectedText, startPosition, endPosition,
//        paragraphIndex, collectionIds: [categoryId] }
export async function createPin(pin) {
  const res = await apiFetch("/api/pins", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify(pin),
  });

  if (!res.ok) {
    const err = new Error(await readErrorMessage(res));
    err.status = res.status;
    throw err;
  }
  return res.json().catch(() => ({}));
}

export async function deletePin(pinId) {
  const res = await apiFetch(`/api/pins/${pinId}`, {
    method: "DELETE",
    headers: { Accept: "application/json" },
  });

  if (!res.ok) {
    const err = new Error(await readErrorMessage(res));
    err.status = res.status;
    throw err;
  }
}
