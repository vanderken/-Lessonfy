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

export async function fetchLessons({ search = "", page = 0, size = 10 }, signal) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (search) params.set("search", search);

  const res = await apiFetch(`/api/lessons?${params}`, {
    headers: { Accept: "application/json" },
    signal,
  });

  if (!res.ok) {
    const err = new Error(await readErrorMessage(res));
    err.status = res.status;
    throw err;
  }
  return res.json();
}

export async function fetchLesson(lessonId) {
  const res = await apiFetch(`/api/lessons/${lessonId}`, {
    headers: { Accept: "application/json" },
  });

  if (!res.ok) {
    const err = new Error(await readErrorMessage(res));
    err.status = res.status;
    throw err;
  }
  return res.json();
}

export async function createLesson(lesson) {
  const res = await apiFetch("/api/lessons", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify(lesson),
  });

  if (!res.ok) {
    const err = new Error(await readErrorMessage(res));
    err.status = res.status;
    throw err;
  }
  return res.json().catch(() => ({}));
}

export async function updateLesson(lessonId, lesson) {
  const res = await apiFetch(`/api/lessons/${lessonId}`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify(lesson),
  });

  if (!res.ok) {
    const err = new Error(await readErrorMessage(res));
    err.status = res.status;
    throw err;
  }
  return res.json().catch(() => ({}));
}

export async function deleteLesson(lessonId) {
  const res = await apiFetch(`/api/lessons/${lessonId}`, {
    method: "DELETE",
    headers: { Accept: "application/json" },
  });

  if (!res.ok) {
    const err = new Error(await readErrorMessage(res));
    err.status = res.status;
    throw err;
  }
}
