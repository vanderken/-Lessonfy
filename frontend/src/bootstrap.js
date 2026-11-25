import { clearCurrentUser, getCurrentUser } from "./api.js";
import { getSavedTheme } from "./theme.js";

/**
 * Runs BEFORE React's first render.
 *
 * - A normal page open (typed URL, bookmark, new tab) always starts at login,
 *   so a remembered user never drops straight onto the dashboard.
 * - A browser REFRESH keeps the signed-in user, the current route and the
 *   saved theme. This fixes the old behaviour where refreshing changed the
 *   page and theme and sent the user back to login.
 *
 * The theme is also applied here so there is no light/dark flash on refresh.
 */
export function bootstrapSession() {
  let user = null;
  try {
    user = getCurrentUser();
    const entry = window.performance?.getEntriesByType?.("navigation")?.[0];
    const isReload = Boolean(entry && entry.type === "reload");

    if (!isReload && user) {
      clearCurrentUser();
      user = null;
    }

    const savedTheme = isReload && user ? getSavedTheme(user.id) : null;
    if (savedTheme) {
      document.documentElement.setAttribute("data-theme", savedTheme);
    } else {
      // Login/register follow the system theme, as they always have.
      document.documentElement.removeAttribute("data-theme");
    }
  } catch {
    // If browser timing APIs are unavailable, start at login safely.
    if (user) clearCurrentUser();
    document.documentElement.removeAttribute("data-theme");
  }
}
