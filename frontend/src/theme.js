export const THEMES = [
  { id: "sunrise", label: "Sunrise", tone: "light", description: "Warm and energizing" },
  { id: "sage", label: "Sage", tone: "light", description: "Calm and focused" },
  { id: "cloud", label: "Cloud", tone: "light", description: "Clean and bright" },
  { id: "dusk", label: "Dusk", tone: "dark", description: "Warm and relaxed" },
  { id: "cobalt", label: "Cobalt", tone: "dark", description: "Deep and focused" },
  { id: "midnight", label: "Midnight", tone: "dark", description: "Easy on the eyes" },
];

const THEME_IDS = new Set(THEMES.map((theme) => theme.id));
const LEGACY_THEME_MAP = {
  light: "cloud",
  dark: "midnight",
  ocean: "cloud",
  lavender: "dusk",
};

export function normalizeThemeId(themeId) {
  if (THEME_IDS.has(themeId)) return themeId;
  if (themeId && LEGACY_THEME_MAP[themeId]) return LEGACY_THEME_MAP[themeId];
  return "cloud";
}

/**
 * Themes are stored PER USER so one user's choice never leaks into another
 * user's dashboard. The theme is also only ever applied by in-app pages
 * (dashboard, lesson pages) — never on the login/register screens.
 */
function themeStorageKey(userId) {
  return userId != null ? `lessonfy-theme-${userId}` : "lessonfy-theme-guest";
}

export function applyTheme(themeId, userId) {
  const id = normalizeThemeId(themeId);
  document.documentElement.setAttribute("data-theme", id);
  try {
    localStorage.setItem(themeStorageKey(userId), id);
  } catch {
    // ignore
  }
  // Let any open page or dialog know that the theme changed immediately.
  if (typeof window !== "undefined") {
    window.dispatchEvent(new CustomEvent("lessonfy-theme-change", { detail: { themeId: id, userId } }));
  }
  return id;
}

export function getSavedTheme(userId) {
  try {
    const saved = localStorage.getItem(themeStorageKey(userId));
    return saved ? normalizeThemeId(saved) : null;
  } catch {
    return null;
  }
}

export function getInitialTheme(userId) {
  return getSavedTheme(userId) || "cloud";
}
