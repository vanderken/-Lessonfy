// Set to false once the Spring Boot endpoints exist.
const MOCK = true;

function csrfToken() {
  const m = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
  return m ? decodeURIComponent(m[1]) : "";
}

export async function post(path, body) {
  if (!MOCK) {
    const res = await fetch("/api" + path, {
      method: "POST",
      credentials: "include", // send the session cookie
      headers: {
        "Content-Type": "application/json",
        "X-XSRF-TOKEN": csrfToken(), // matches CookieCsrfTokenRepository
      },
      body: JSON.stringify(body),
    });
    if (!res.ok) {
      const data = await res.json().catch(() => ({}));
      throw new Error(data.message || "Something went wrong. Try again.");
    }
    return res.json().catch(() => ({}));
  }

  // ---- mock behaviour for UI testing ----
  await new Promise((r) => setTimeout(r, 900));
  if (path === "/auth/login" && body.password === "wrongpass")
    throw new Error("Email or password is incorrect.");
  if (path === "/auth/register" && body.email === "taken@example.com")
    throw new Error("An account with this email already exists. Try signing in.");
  return { email: body.email, name: body.name };
}

export const register = (data) => post("/auth/register", data);
export const login = (data) => post("/auth/login", data);