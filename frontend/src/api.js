import { apiFetch } from "./api/http.js";

const MOCK = false;
const USER_STORAGE_KEY = "lessonfy-user";

export function getCurrentUser() {
  try {
    const raw = localStorage.getItem(USER_STORAGE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function saveCurrentUser(user) {
  if (!user) {
    localStorage.removeItem(USER_STORAGE_KEY);
    return;
  }
  localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(user));
}

export function clearCurrentUser() {
  localStorage.removeItem(USER_STORAGE_KEY);
}

export function getCurrentUserId() {
  const user = getCurrentUser();
  return user && user.id != null ? Number(user.id) : null;
}

async function readErrorMessage(res) {
  const text = await res.text();
  if (!text) return res.statusText || "Something went wrong. Try again.";

  try {
    const data = JSON.parse(text);
    if (data && data.message) return data.message;
  } catch {
    // fall through to plain text below
  }

  return text;
}

export async function post(path, body) {
  if (!MOCK) {
    const res = await apiFetch("/api" + path, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(body),
    });
    if (!res.ok) {
      throw new Error(await readErrorMessage(res));
    }
    return res.json().catch(() => ({}));
  }

  // ---- mock behaviour for UI testing ----
  await new Promise((r) => setTimeout(r, 900));
  if (path === "/auth/login" && body.password === "wrongpass")
    throw new Error("Email or password is incorrect.");
  if (path === "/auth/register" && body.email === "taken@example.com")
    throw new Error("An account with this email already exists. Try signing in.");
  return {
    id: 1,
    email: body.email,
    name: [body.firstName, body.lastName].filter(Boolean).join(" ") || body.name || "User",
    firstName: body.firstName || "",
    lastName: body.lastName || "",
  };
}

export async function fetchSettings() {
  if (getCurrentUserId() == null) throw new Error("Authentication required.");

  const res = await apiFetch("/api/settings", {
    headers: { Accept: "application/json" },
  });

  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.message || "Unable to load settings.");
  }

  return res.json();
}

export async function saveSettings(settings) {
  if (getCurrentUserId() == null) throw new Error("Authentication required.");

  const res = await apiFetch("/api/settings", {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify(settings),
  });

  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.message || "Unable to save settings.");
  }

  return res.json();
}

export async function changePassword({ currentPassword, newPassword }) {
  if (getCurrentUserId() == null) throw new Error("Authentication required.");

  const res = await apiFetch("/api/account/password", {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify({ currentPassword, newPassword }),
  });

  if (!res.ok) {
    throw new Error(await readErrorMessage(res));
  }
}

export const register = (data) => post("/auth/register", data);
export const login = (data) => post("/auth/login", data);