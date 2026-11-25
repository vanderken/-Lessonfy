import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { clearCurrentUser, fetchSettings, getCurrentUserId, saveSettings } from "../api.js";
import { fetchLesson } from "../api/lessons";
import { createPin, deletePin as deletePinApi, fetchPins } from "../api/pins";
import { createCollection, fetchCollections } from "../api/collections";
import { THEMES, applyTheme, getInitialTheme, getSavedTheme, normalizeThemeId } from "../theme.js";

// The two built-in highlight categories are stored as collections on the
// server too ("Study this" / "Save this for later"). They are matched by
// name so existing collections are reused instead of duplicated.
const BUILT_IN_CATEGORIES = [
  { key: "study", label: "Study this", color: "#FFE45C" },
  { key: "later", label: "Save this for later", color: "#8EDBA4" },
];

const BUILT_IN_CATEGORY_BY_NAME = new Map(
  BUILT_IN_CATEGORIES.map((category) => [category.label.toLowerCase(), category])
);

const DEFAULT_CATEGORIES = BUILT_IN_CATEGORIES.map((category) => ({
  id: category.key,
  label: category.label,
  color: category.color,
  builtIn: true,
  builtInKey: category.key,
}));

const HIGHLIGHTS_STORAGE_PREFIX = "lessonfy-highlights";
const SOUND_PREFS_KEY = "lessonfy-sound-prefs";

function getHighlightsStorageKey(userId, lessonId) {
  if (userId == null || lessonId == null) return null;
  return `${HIGHLIGHTS_STORAGE_PREFIX}:${userId}:${lessonId}`;
}

// Turns a server collection row into a highlight category.
function toCategory(collection) {
  const builtIn = BUILT_IN_CATEGORY_BY_NAME.get(String(collection.name || "").toLowerCase());
  return {
    id: String(collection.id),
    label: collection.name,
    color: collection.color || builtIn?.color || "#F4A9C8",
    builtIn: Boolean(builtIn),
    builtInKey: builtIn?.key ?? null,
  };
}

// Turns a server pin into a highlight mark. sectionIndex is derived from the
// lesson's section ids because pins store the database section id.
function pinToMark(pin, sectionIdToIndex, categoryList) {
  const sectionIndex = sectionIdToIndex.get(Number(pin.sectionId));
  if (sectionIndex == null) return null;
  const collectionId = Array.isArray(pin.collectionIds) && pin.collectionIds.length > 0
    ? String(pin.collectionIds[0])
    : null;
  const category = categoryList.find((item) => item.id === collectionId);
  if (!category) return null;
  const start = Number(pin.startPosition ?? 0);
  return {
    id: pin.id,
    sectionId: Number(pin.sectionId),
    sectionIndex,
    paragraphIndex: Number(pin.paragraphIndex ?? 0),
    start,
    end: Number(pin.endPosition ?? start),
    text: pin.selectedText || "",
    categoryId: category.id,
    categoryLabel: category.label,
    color: category.color,
  };
}

// One-time migration: highlights that were only saved in localStorage (the old
// behavior) are pushed to the server as pins, then the local copy is removed.
async function migrateLegacyHighlights(userId, lessonIdParam, lessonData, categoryList) {
  const storageKey = getHighlightsStorageKey(userId, lessonIdParam);
  if (!storageKey) return { marks: [], categories: categoryList, fullyMigrated: true };

  let parsed = null;
  try {
    const raw = localStorage.getItem(storageKey);
    if (raw) parsed = JSON.parse(raw);
  } catch {
    parsed = null;
  }
  const legacyMarks = Array.isArray(parsed?.marks) ? parsed.marks : [];
  const legacyCustomCategories = Array.isArray(parsed?.customCategories) ? parsed.customCategories : [];

  if (legacyMarks.length === 0 && legacyCustomCategories.length === 0) {
    localStorage.removeItem(storageKey);
    return { marks: [], categories: categoryList, fullyMigrated: true };
  }

  let categories = [...categoryList];

  // Find the collection for a category label, creating it on the server if the
  // user never had it. 409 (name already taken) → reuse the existing collection.
  const collectionIdFor = async (label, fallbackColor) => {
    const trimmed = String(label || "").trim();
    if (!trimmed) return null;
    const existing = categories.find((item) => item.label.toLowerCase() === trimmed.toLowerCase());
    if (existing) return existing.id;
    try {
      const created = await createCollection({ name: trimmed, color: fallbackColor || "#F4A9C8" });
      const category = toCategory(created);
      if (!categories.some((item) => item.id === category.id)) categories = [...categories, category];
      return category.id;
    } catch (err) {
      if (err.status === 409) {
        try {
          const all = await fetchCollections();
          const match = all.find((item) => String(item.name).toLowerCase() === trimmed.toLowerCase());
          if (match) {
            const category = toCategory(match);
            if (!categories.some((item) => item.id === category.id)) categories = [...categories, category];
            return category.id;
          }
        } catch {
          // ignore
        }
      }
      return null;
    }
  };

  // Legacy category ids: "study"/"later" for the built-ins, custom ids whose
  // labels/colors live in the stored customCategories list.
  const legacyCategoryById = new Map();
  for (const category of legacyCustomCategories) {
    if (category && typeof category.id === "string" && typeof category.label === "string") {
      legacyCategoryById.set(category.id, { label: category.label, color: category.color });
    }
  }

  const sectionByIndex = new Map(
    (lessonData?.sections ?? []).map((section, index) => [index, section])
  );

  const migratedMarks = [];
  for (const legacyMark of legacyMarks) {
    const builtIn = BUILT_IN_CATEGORIES.find((item) => item.key === legacyMark?.categoryId);
    const info = builtIn
      ? { label: builtIn.label, color: builtIn.color }
      : legacyCategoryById.get(legacyMark?.categoryId) || { label: "Study this", color: "#FFE45C" };

    const section = sectionByIndex.get(legacyMark?.sectionIndex);
    if (!section) continue;
    const start = Math.max(0, Number(legacyMark.start ?? 0));
    const end = Math.max(start, Number(legacyMark.end ?? start));
    if (end <= start) continue;
    const text = String(legacyMark.text ?? "").slice(0, 500);
    const paragraphIndex = Math.max(0, Number(legacyMark.paragraphIndex ?? 0));

    const collectionId = await collectionIdFor(info.label, info.color);
    if (!collectionId) continue;

    try {
      const pin = await createPin({
        lessonId: Number(lessonIdParam),
        sectionId: section.id,
        selectedText: text,
        startPosition: start,
        endPosition: end,
        paragraphIndex,
        collectionIds: [Number(collectionId)],
      });
      const category = categories.find((item) => item.id === collectionId);
      migratedMarks.push({
        id: pin.id,
        sectionId: section.id,
        sectionIndex: legacyMark.sectionIndex,
        paragraphIndex,
        start,
        end,
        text: pin.selectedText || text,
        categoryId: category.id,
        categoryLabel: category.label,
        color: category.color,
      });
    } catch {
      // Skip highlights that fail to migrate.
    }
  }

  const fullyMigrated = migratedMarks.length >= legacyMarks.length;
  if (fullyMigrated) localStorage.removeItem(storageKey);
  return { marks: migratedMarks, categories, fullyMigrated };
}

const SOUND_OPTIONS = [
  { id: "off", label: "Sound off" },
  { id: "white", label: "White noise" },
  { id: "pink", label: "Pink noise" },
  { id: "brown", label: "Brown noise" },
  { id: "rain", label: "Soft rain" },
  { id: "binaural", label: "Binaural beats" },
];

const styles = `
  .lesson-reader-shell {
    --reader-bg: var(--bg, #f5f5f3);
    --reader-surface: var(--surface, #ffffff);
    --reader-ink: var(--ink, #1d1d1d);
    --reader-muted: var(--muted, #4d6670);
    --reader-line: var(--line, rgba(17, 24, 28, 0.12));
    --reader-accent: var(--primary, #5a97af);
    --reader-accent-ink: var(--primary-ink, var(--on-primary, #ffffff));
    background: var(--reader-bg);
    color: var(--reader-ink);
    min-height: 100vh;
    font-family: "Atkinson Hyperlegible", "Segoe UI", sans-serif;
  }

  .lesson-reader-shell * { box-sizing: border-box; }

  .lesson-reader-shell .top {
    position: sticky;
    top: 0;
    z-index: 10;
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 20px;
    min-height: 92px;
    padding: 14px clamp(18px, 4vw, 42px);
    background: color-mix(in srgb, var(--reader-surface) 96%, transparent);
    border-bottom: 1px solid var(--reader-line);
  }

  .lesson-reader-shell .brand {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: clamp(1.8rem, 2.3vw, 2.7rem);
    font-weight: 700;
    letter-spacing: -0.05em;
    color: var(--reader-ink);
    line-height: 1;
  }

  .lesson-reader-shell .back-button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    min-width: 0;
    background: transparent;
    border: 1px solid color-mix(in srgb, var(--reader-line) 90%, transparent);
    border-radius: 999px;
    padding: 8px 14px 8px 10px;
    color: var(--reader-ink);
    cursor: pointer;
    font: inherit;
    font-size: 0.95rem;
    font-weight: 700;
    line-height: 1;
    transition: background 160ms ease, border-color 160ms ease, transform 160ms ease, box-shadow 160ms ease;
  }

  .lesson-reader-shell .back-button:hover {
    background: color-mix(in srgb, var(--reader-ink) 6%, transparent);
    border-color: color-mix(in srgb, var(--reader-accent) 35%, var(--reader-line));
  }

  .lesson-reader-shell .back-button:active {
    transform: translateY(1px);
  }

  .lesson-reader-shell .back-button:focus-visible {
    outline: none;
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--reader-accent) 22%, transparent);
  }

  .lesson-reader-shell .back-button__icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--reader-ink) 7%, transparent);
    color: var(--reader-ink);
    flex: 0 0 auto;
  }

  .lesson-reader-shell .brand-mark {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 28px;
    background: #e6a94b;
    color: #1d1d1d;
    border-radius: 6px;
    font-size: 0.9rem;
    font-weight: 800;
    line-height: 1;
  }

  .lesson-reader-shell .header-actions {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 14px;
    margin-left: auto;
  }

  .lesson-reader-shell .timer {
    display: flex;
    align-items: center;
    gap: 8px;
    font-weight: 700;
    color: var(--reader-ink);
  }

  .lesson-reader-shell .time {
    font-size: clamp(2.3rem, 2.7vw, 3.2rem);
    line-height: 1;
    letter-spacing: -0.08em;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
    min-width: 4.2ch;
  }

  .lesson-reader-shell .phase {
    color: var(--reader-muted);
    font-size: 1rem;
    min-width: 3.5ch;
  }

  .lesson-reader-shell .action-stack {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: nowrap;
  }

  .lesson-reader-shell .btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: auto;
    min-width: 110px;
    font: inherit;
    color: var(--reader-ink);
    background: color-mix(in srgb, var(--reader-ink) 7%, transparent);
    border: 1px solid var(--reader-line);
    padding: 10px 16px;
    border-radius: 10px;
    cursor: pointer;
    text-decoration: none;
    transition: transform 0.2s ease, border-color 0.2s ease, background 0.2s ease;
  }

  .lesson-reader-shell .btn:hover {
    border-color: var(--reader-accent);
    transform: translateY(-1px);
  }

  .lesson-reader-shell .btn.pri {
    background: var(--reader-accent);
    border-color: var(--reader-accent);
    color: var(--reader-accent-ink);
    min-width: 120px;
  }

  .lesson-reader-shell .btn.danger {
    border-color: color-mix(in srgb, #b42318 35%, var(--reader-line));
    color: #b42318;
  }

  .lesson-reader-shell .btn.small {
    min-width: 0;
    padding: 6px 10px;
    font-size: 0.9rem;
    border-radius: 8px;
  }

  .lesson-reader-shell .tools {
    display: flex;
    align-items: stretch;
    gap: 12px;
    padding: 14px clamp(18px, 4vw, 42px);
    background: color-mix(in srgb, var(--reader-surface) 94%, transparent);
    border-bottom: 1px solid var(--reader-line);
    flex-wrap: wrap;
  }

  .lesson-reader-shell .tool-group {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
    padding: 10px 12px;
    background: color-mix(in srgb, var(--reader-ink) 3%, transparent);
    border: 1px solid color-mix(in srgb, var(--reader-line) 80%, transparent);
    border-radius: 14px;
    min-width: 0;
  }

  .lesson-reader-shell .tool-group__title {
    font-size: 0.78rem;
    font-weight: 800;
    letter-spacing: 0.06em;
    text-transform: uppercase;
    color: var(--reader-muted);
    margin-right: 2px;
  }

  .lesson-reader-shell .tool-check,
  .lesson-reader-shell .tool-range,
  .lesson-reader-shell .tool-select {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    color: var(--reader-ink);
    font-size: 0.95rem;
    font-weight: 600;
    min-width: 0;
  }

  .lesson-reader-shell .tool-check input[type="checkbox"] {
    width: 18px;
    height: 18px;
    margin: 0;
    accent-color: var(--reader-accent);
  }

  .lesson-reader-shell .tool-range input[type="range"],
  .lesson-reader-shell .sound-volume {
    width: clamp(140px, 16vw, 220px);
    min-width: 140px;
    max-width: 100%;
    margin: 0;
    padding: 0;
    border: 0;
    min-height: 2rem;
    height: 2rem;
    background: transparent;
    accent-color: var(--reader-accent);
  }

  .lesson-reader-shell .tool-range--volume input[type="range"] {
    width: clamp(120px, 12vw, 180px);
    min-width: 120px;
  }

  .lesson-reader-shell .tool-select .timer-select {
    min-width: 180px;
  }

  .lesson-reader-shell .wrap {
    display: grid;
    grid-template-columns: minmax(0, 1.5fr) minmax(280px, 0.75fr);
    gap: 26px;
    max-width: 1180px;
    margin: 0 auto;
    padding: 14px clamp(18px, 4vw, 42px) 50px;
    align-items: start;
  }

  .lesson-reader-shell .reader {
    max-width: 68ch;
    color: var(--reader-ink);
  }

  .lesson-reader-shell .reader h1 {
    font-size: clamp(2.2rem, 4vw, 3.4rem);
    line-height: 0.98;
    letter-spacing: -0.06em;
    margin: 0 0 8px;
    font-weight: 700;
  }

  .lesson-reader-shell .hint {
    margin: 0 0 16px;
    color: var(--reader-muted);
    font-size: 1.05rem;
    line-height: 1.45;
  }

  .lesson-reader-shell .bar {
    height: 8px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--reader-ink) 14%, transparent);
    overflow: hidden;
    margin: 12px 0 10px;
  }

  .lesson-reader-shell .bar i {
    display: block;
    height: 100%;
    width: 0;
    background: var(--reader-accent);
    transition: width 0.3s ease;
  }

  .lesson-reader-shell .pcount {
    font-size: 0.9rem;
    color: var(--reader-muted);
    margin: 0 0 18px;
  }

  .lesson-reader-shell .chunk {
    background: color-mix(in srgb, var(--reader-surface) 88%, transparent);
    border: 1px solid var(--reader-line);
    border-radius: 16px;
    padding: 18px 20px;
    margin: 0 0 18px;
    scroll-margin-top: 110px;
    transition: opacity 0.2s ease, border-color 0.2s ease, transform 0.2s ease;
  }

  .lesson-reader-shell .focus .chunk { opacity: 0.45; }
  .lesson-reader-shell .focus .chunk.active { opacity: 1; }
  .lesson-reader-shell .chunk.active { border-color: color-mix(in srgb, var(--reader-accent) 80%, transparent); }
  .lesson-reader-shell .chunk h2 { margin: 0 0 12px; font-size: 1.25em; color: var(--reader-ink); text-align: center; }
  .lesson-reader-shell .txt p { margin: 0 0 12px; color: var(--reader-ink); }
  .lesson-reader-shell .txt p:last-child { margin-bottom: 0; }
  .lesson-reader-shell .row { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 14px; justify-content: center; }

  .lesson-reader-shell .m {
    border-radius: 4px;
    color: #1b1b1b;
    padding: 0 2px;
    box-shadow: inset 0 -1px 0 rgba(0, 0, 0, 0.12);
  }

  .lesson-reader-shell .m.blink {
    animation: readerMarkBlink 0.45s ease-in-out 0s 4 alternate;
  }

  @keyframes readerMarkBlink {
    from { filter: brightness(1); box-shadow: inset 0 -1px 0 rgba(0,0,0,0.12), 0 0 0 0 color-mix(in srgb, var(--reader-accent) 0%, transparent); }
    to { filter: brightness(1.08); box-shadow: inset 0 -1px 0 rgba(0,0,0,0.12), 0 0 0 4px color-mix(in srgb, var(--reader-accent) 35%, transparent); }
  }

  .lesson-reader-shell .highlight-menu {
    position: fixed;
    z-index: 30;
    display: none;
    width: min(320px, calc(100vw - 16px));
    gap: 8px;
    flex-direction: column;
    padding: 10px;
    background: color-mix(in srgb, var(--reader-surface) 98%, var(--reader-bg));
    color: var(--reader-ink);
    border: 1px solid var(--reader-line);
    border-radius: 14px;
    box-shadow: 0 18px 40px rgba(0, 0, 0, 0.22);
  }

  .lesson-reader-shell .highlight-menu.open { display: flex; }

  .lesson-reader-shell .highlight-menu__title {
    font-size: 0.85rem;
    font-weight: 700;
    color: var(--reader-muted);
    text-transform: uppercase;
    letter-spacing: 0.04em;
  }

  .lesson-reader-shell .highlight-menu__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .lesson-reader-shell .highlight-menu__actions .btn {
    min-width: 0;
    flex: 1 1 auto;
  }

  .lesson-reader-shell .highlight-menu__category-btn {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    justify-content: flex-start;
  }

  .lesson-reader-shell .highlight-menu__swatch {
    width: 10px;
    height: 10px;
    border-radius: 999px;
    border: 1px solid color-mix(in srgb, var(--reader-ink) 18%, transparent);
    flex: 0 0 auto;
  }

  .lesson-reader-shell .highlight-menu__custom {
    border-top: 1px solid var(--reader-line);
    padding-top: 8px;
    display: grid;
    gap: 8px;
  }

  .lesson-reader-shell .highlight-menu__row {
    display: flex;
    gap: 8px;
    align-items: center;
  }

  .lesson-reader-shell .highlight-menu input[type="text"] {
    flex: 1;
    min-width: 0;
    font: inherit;
    color: var(--reader-ink);
    background: color-mix(in srgb, var(--reader-ink) 5%, transparent);
    border: 1px solid var(--reader-line);
    border-radius: 10px;
    padding: 9px 10px;
  }

  .lesson-reader-shell .highlight-menu input[type="color"] {
    width: 42px;
    height: 40px;
    padding: 2px;
    border: 1px solid var(--reader-line);
    border-radius: 10px;
    background: transparent;
    cursor: pointer;
  }

  .lesson-reader-shell .panel {
    background: color-mix(in srgb, var(--reader-surface) 85%, transparent);
    border: 1px solid var(--reader-line);
    border-radius: 18px;
    padding: 14px 18px 16px;
    min-height: 180px;
  }

  .lesson-reader-shell .panel-title {
    cursor: default;
    font-weight: 700;
    padding: 6px 0 10px;
    font-size: 1.15rem;
    color: var(--reader-ink);
  }

  .lesson-reader-shell .highlight-groups {
    display: grid;
    gap: 10px;
    max-height: 58vh;
    overflow: auto;
    padding-right: 2px;
  }

  .lesson-reader-shell .highlight-group {
    border: 1px solid var(--reader-line);
    border-radius: 12px;
    background: color-mix(in srgb, var(--reader-surface) 70%, transparent);
  }

  .lesson-reader-shell .highlight-group > summary {
    cursor: pointer;
    list-style: none;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    padding: 10px 12px;
    font-weight: 700;
    color: var(--reader-ink);
  }

  .lesson-reader-shell .highlight-group > summary::-webkit-details-marker { display: none; }

  .lesson-reader-shell .highlight-group__meta {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
  }

  .lesson-reader-shell .highlight-dot {
    width: 12px;
    height: 12px;
    border-radius: 999px;
    border: 1px solid color-mix(in srgb, var(--reader-ink) 18%, transparent);
    flex: 0 0 auto;
  }

  .lesson-reader-shell .highlight-count {
    color: var(--reader-muted);
    font-size: 0.9rem;
    font-weight: 700;
  }

  .lesson-reader-shell .highlight-items {
    border-top: 1px solid var(--reader-line);
    padding: 6px;
    display: grid;
    gap: 6px;
  }

  .lesson-reader-shell .highlight-item {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 8px;
    align-items: start;
    padding: 8px;
    border-radius: 10px;
  }

  .lesson-reader-shell .highlight-item:hover {
    background: color-mix(in srgb, var(--reader-ink) 5%, transparent);
  }

  .lesson-reader-shell .highlight-jump {
    all: unset;
    cursor: pointer;
    display: block;
    min-width: 0;
    color: inherit;
  }

  .lesson-reader-shell .highlight-jump p {
    margin: 0;
    color: var(--reader-ink);
    line-height: 1.4;
    overflow-wrap: anywhere;
  }

  .lesson-reader-shell .highlight-jump small {
    display: block;
    margin-top: 4px;
    color: var(--reader-muted);
  }

  .lesson-reader-shell .empty { color: var(--reader-muted); line-height: 1.5; padding: 8px 0; }

  .lesson-reader-shell .timer-select {
    font: inherit;
    color: var(--reader-ink);
    background-color: var(--reader-surface);
    border: 1px solid var(--reader-line);
    border-radius: 10px;
    padding: 9px 12px;
    min-width: 180px;
    max-width: 100%;
    cursor: pointer;
    color-scheme: inherit;
  }

  .lesson-reader-shell .timer-select option,
  .lesson-reader-shell select option {
    background-color: var(--reader-surface);
    color: var(--reader-ink);
  }

  .lesson-reader-shell .custom-timer {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
    padding: 6px 8px;
    border: 1px solid var(--reader-line);
    border-radius: 12px;
    background: color-mix(in srgb, var(--reader-ink) 4%, transparent);
  }

  .lesson-reader-shell .custom-timer__field {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 0.88rem;
    font-weight: 700;
    color: var(--reader-muted);
  }

  .lesson-reader-shell .custom-timer__field > span:first-child {
    color: var(--reader-ink);
  }

  .lesson-reader-shell .custom-timer__field input[type="number"] {
    width: 4.5rem;
    min-width: 4.5rem;
    margin: 0;
    padding: 7px 8px;
    font: inherit;
    font-weight: 700;
    color: var(--reader-ink);
    background-color: var(--reader-surface);
    border: 1px solid var(--reader-line);
    border-radius: 8px;
    color-scheme: inherit;
  }

  .lesson-reader-shell .custom-timer__field input[type="number"]:disabled {
    opacity: 0.65;
    cursor: not-allowed;
  }

  .lesson-reader-shell .custom-timer__unit {
    font-size: 0.8rem;
  }

  .lesson-reader-shell .timer-select:focus {
    outline: 2px solid color-mix(in srgb, var(--reader-accent) 55%, transparent);
    outline-offset: 1px;
  }

  .lesson-reader-shell .sound-controls {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  .lesson-reader-shell .sound-volume {
    width: 92px;
    accent-color: var(--reader-accent);
  }

  .lesson-reader-shell .timer-alert-overlay {
    position: fixed;
    inset: 0;
    z-index: 60;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 20px;
    background: rgba(8, 12, 16, 0.55);
  }

  .lesson-reader-shell .timer-alert {
    width: min(100%, 420px);
    background: var(--reader-surface);
    color: var(--reader-ink);
    border: 1px solid var(--reader-line);
    border-radius: 18px;
    padding: 24px;
    box-shadow: 0 24px 60px rgba(0, 0, 0, 0.28);
    text-align: center;
  }

  .lesson-reader-shell .timer-alert h2 {
    margin: 0 0 8px;
    font-size: clamp(1.6rem, 3vw, 2rem);
    line-height: 1.1;
  }

  .lesson-reader-shell .timer-alert p {
    margin: 0 0 18px;
    color: var(--reader-muted);
    line-height: 1.5;
  }

  .lesson-reader-shell .timer-alert .btn.pri {
    min-width: 140px;
  }

  .lesson-reader-shell .timer-alert .actions {
    display: flex;
    justify-content: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  @media (max-width: 820px) {
    .lesson-reader-shell .top {
      flex-direction: column;
      align-items: stretch;
    }

    .lesson-reader-shell .header-actions {
      align-items: stretch;
      min-width: 0;
      width: 100%;
    }

    .lesson-reader-shell .action-stack {
      width: 100%;
      flex-wrap: wrap;
    }

    .lesson-reader-shell .timer-select {
      width: 100%;
      min-width: 0;
    }

    .lesson-reader-shell .tools {
      flex-wrap: wrap;
      overflow: visible;
      gap: 10px;
    }

    .lesson-reader-shell .tool-group {
      width: 100%;
      align-items: flex-start;
    }

    .lesson-reader-shell .tool-range,
    .lesson-reader-shell .tool-select {
      width: 100%;
      justify-content: space-between;
    }

    .lesson-reader-shell .tool-range input[type="range"],
    .lesson-reader-shell .sound-volume,
    .lesson-reader-shell .tool-range--volume input[type="range"],
    .lesson-reader-shell .tool-select .timer-select {
      width: min(100%, 320px);
      min-width: 0;
      flex: 1 1 180px;
    }

    .lesson-reader-shell .wrap { grid-template-columns: 1fr; }
  }
`;

const getParagraphs = (text) => (text || "").split(/\n\s*\n/).filter(Boolean);

const getBionicText = (text) => {
  if (!text) return "";

  return text.split(/([A-Za-z0-9'’]+)/).flatMap((part, index) => {
    if (index % 2 === 0) return part;
    const length = Math.max(1, Math.ceil(part.length * 0.45));
    return [
      <b key={`${part}-${index}-b`}>{part.slice(0, length)}</b>,
      part.slice(length),
    ];
  });
};

const renderHighlightedText = (text, marks, transform = (value) => value) => {
  if (!text) return "";
  const ordered = [...(marks || [])]
    .filter((mark) => mark.start >= 0 && mark.end > mark.start)
    .sort((a, b) => a.start - b.start || a.end - b.end);

  if (!ordered.length) return transform(text);

  const result = [];
  let cursor = 0;
  ordered.forEach((mark, index) => {
    const start = Math.max(0, Math.min(mark.start, text.length));
    const end = Math.max(start, Math.min(mark.end, text.length));

    if (cursor < start) {
      result.push(<span key={`plain-${index}`}>{transform(text.slice(cursor, start))}</span>);
    }

    if (end > start) {
      result.push(
        <span
          key={`mark-${index}`}
          data-mark-id={mark.id}
          className="m"
          style={{
            background: mark.color || "rgba(255, 228, 92, 0.75)",
            color: "#1b1b1b",
          }}
        >
          {transform(text.slice(start, end))}
        </span>
      );
    }

    cursor = end;
  });

  if (cursor < text.length) {
    result.push(<span key="tail">{transform(text.slice(cursor))}</span>);
  }

  return result;
};

const TIMER_PRESETS = [
  { id: "25-5", label: "25 min focus / 5 min break", focusMinutes: 25, breakMinutes: 5 },
  { id: "15-5", label: "15 min focus / 5 min break", focusMinutes: 15, breakMinutes: 5 },
  { id: "50-10", label: "50 min focus / 10 min break", focusMinutes: 50, breakMinutes: 10 },
  { id: "45-15", label: "45 min focus / 15 min break", focusMinutes: 45, breakMinutes: 15 },
];

const formatTimer = (totalSeconds) => {
  const safe = Math.max(0, Math.floor(totalSeconds));
  const minutes = Math.floor(safe / 60);
  const seconds = safe % 60;
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
};

export default function LessonReaderPage() {
  const navigate = useNavigate();
  const { lessonId } = useParams();
  const highlightMenuRef = useRef(null);
  const [lesson, setLesson] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [done, setDone] = useState({});
  const [marks, setMarks] = useState([]);
  const [categories, setCategories] = useState(DEFAULT_CATEGORIES);
  const [activeIndex, setActiveIndex] = useState(0);
  const [bionic, setBionic] = useState(true);
  const [focusMode, setFocusMode] = useState(true);
  const [fontSize, setFontSize] = useState(19);
  const [lineHeight, setLineHeight] = useState(1.7);
  const [theme, setTheme] = useState(() => {
    const current = document.documentElement.getAttribute("data-theme");
    if (current) return normalizeThemeId(current);
    return getInitialTheme(getCurrentUserId());
  });
  const [settingsLoaded, setSettingsLoaded] = useState(false);
  const [status, setStatus] = useState("");
  const [highlightMenu, setHighlightMenu] = useState({ visible: false, left: 0, top: 0, sectionIndex: 0, paragraphIndex: 0, start: 0, end: 0, text: "" });
  const [customCategoryOpen, setCustomCategoryOpen] = useState(false);
  const [customCategoryLabel, setCustomCategoryLabel] = useState("");
  const [customCategoryColor, setCustomCategoryColor] = useState("#F4A9C8");
  const [pendingDeleteMark, setPendingDeleteMark] = useState(null);
  const [timerPresetId, setTimerPresetId] = useState(TIMER_PRESETS[0].id);
  const [customFocusMinutes, setCustomFocusMinutes] = useState(25);
  const [customBreakMinutes, setCustomBreakMinutes] = useState(5);
  const [timerPhase, setTimerPhase] = useState("focus");
  const [secondsLeft, setSecondsLeft] = useState(TIMER_PRESETS[0].focusMinutes * 60);
  const [timerRunning, setTimerRunning] = useState(false);
  const [timerAlert, setTimerAlert] = useState(null);
  const [soundType, setSoundType] = useState("off");
  const [soundVolume, setSoundVolume] = useState(0.35);
  const audioContextRef = useRef(null);
  const ringIntervalRef = useRef(null);
  const soundNodesRef = useRef([]);
  const focusModeRef = useRef(focusMode);
  const activeIndexRef = useRef(activeIndex);
  const marksRef = useRef(marks);
  const customCategoryOpenRef = useRef(customCategoryOpen);

  useEffect(() => {
    focusModeRef.current = focusMode;
  }, [focusMode]);

  useEffect(() => {
    activeIndexRef.current = activeIndex;
  }, [activeIndex]);

  useEffect(() => {
    marksRef.current = marks;
  }, [marks]);

  useEffect(() => {
    customCategoryOpenRef.current = customCategoryOpen;
  }, [customCategoryOpen]);

  useEffect(() => {
    try {
      const raw = localStorage.getItem(SOUND_PREFS_KEY);
      if (!raw) return;
      const parsed = JSON.parse(raw);
      if (parsed && SOUND_OPTIONS.some((option) => option.id === parsed.soundType)) {
        setSoundType(parsed.soundType);
      }
      if (parsed && Number.isFinite(Number(parsed.soundVolume))) {
        const vol = Math.max(0, Math.min(1, Number(parsed.soundVolume)));
        setSoundVolume(vol);
      }
    } catch {
      // ignore
    }
  }, []);

  useEffect(() => {
    try {
      localStorage.setItem(SOUND_PREFS_KEY, JSON.stringify({ soundType, soundVolume }));
    } catch {
      // ignore
    }
  }, [soundType, soundVolume]);

  useEffect(() => {
    let mounted = true;
    const userId = getCurrentUserId();
    setMarks([]);
    setCategories(DEFAULT_CATEGORIES);

    // Load the lesson first (marks need its section ids), then the user's
    // collections and pins from the server.
    fetchLesson(lessonId)
      .then(async (data) => {
        if (!mounted) return;
        setLesson(data);
        setActiveIndex(0);

        let collections = [];
        let pins = [];
        let highlightError = null;
        try {
          [collections, pins] = await Promise.all([fetchCollections(), fetchPins({ lessonId })]);
        } catch (err) {
          if (err.status === 401) {
            navigate("/login", { replace: true });
            return;
          }
          highlightError = err;
        }
        if (!mounted) return;

        const nextCategories = (collections.length > 0 ? collections : []).map(toCategory);
        if (nextCategories.length > 0) setCategories(nextCategories);

        const sectionIdToIndex = new Map((data.sections ?? []).map((section, index) => [Number(section.id), index]));
        let serverMarks = (Array.isArray(pins) ? pins : [])
          .map((pin) => pinToMark(pin, sectionIdToIndex, nextCategories))
          .filter(Boolean);

        // One-time migration: this lesson has no server highlights yet but the
        // browser still has old localStorage ones — move them to the server.
        if (serverMarks.length === 0 && highlightError == null) {
          try {
            const migrated = await migrateLegacyHighlights(userId, lessonId, data, nextCategories);
            if (!mounted) return;
            serverMarks = migrated.marks;
            if (migrated.categories.length > 0) setCategories(migrated.categories);
          } catch {
            // ignore migration failures — the reader still works without them
          }
        }

        setMarks(serverMarks);
        if (highlightError) {
          setStatus("Highlights could not be loaded from the server.");
        }
      })
      .catch((err) => {
        if (!mounted) return;
        if (err.status === 401) {
          navigate("/login", { replace: true });
          return;
        }
        setError("Could not load this lesson. Please try again.");
      })
      .finally(() => {
        if (mounted) setLoading(false);
      });

    return () => {
      mounted = false;
    };
  }, [lessonId, navigate]);

  useEffect(() => {
    let mounted = true;

    fetchSettings()
      .then((settings) => {
        if (!mounted || !settings) return;
        if (typeof settings.bionicReadingEnabled === "boolean") setBionic(settings.bionicReadingEnabled);
        if (Number.isFinite(Number(settings.fontSize))) setFontSize(Number(settings.fontSize));
        if (Number.isFinite(Number(settings.lineSpacing))) setLineHeight(Number(settings.lineSpacing));
        // Prefer the locally chosen theme (consistent across dashboard/reader).
        // Backend settings may lag or be unavailable; don't override a local choice.
        // Theme is stored per user so it never leaks between accounts.
        const localTheme = getSavedTheme(getCurrentUserId());
        if (settings.theme && !localTheme) {
          const nextTheme = normalizeThemeId(settings.theme);
          setTheme(nextTheme);
          applyTheme(nextTheme, getCurrentUserId());
        } else if (localTheme) {
          const nextTheme = normalizeThemeId(localTheme);
          setTheme(nextTheme);
          applyTheme(nextTheme, getCurrentUserId());
        }
      })
      .catch(() => {
        // Keep local/system defaults if settings can't be loaded.
      })
      .finally(() => {
        if (mounted) setSettingsLoaded(true);
      });

    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    applyTheme(theme, getCurrentUserId());
  }, [theme]);

  useEffect(() => {
    if (!settingsLoaded) return;
    const timeout = window.setTimeout(() => {
      saveSettings({
        bionicReadingEnabled: bionic,
        fontSize,
        lineSpacing: lineHeight,
        theme,
      }).catch(() => {
        // Don't interrupt reading if settings persistence fails.
      });
    }, 250);

    return () => window.clearTimeout(timeout);
  }, [bionic, fontSize, lineHeight, theme, settingsLoaded]);

  useEffect(() => {
    const handleSelection = () => {
      const selection = window.getSelection();
      if (!selection || selection.rangeCount === 0 || selection.isCollapsed) {
        if (customCategoryOpenRef.current) return;
        setHighlightMenu((current) => ({ ...current, visible: false }));
        setCustomCategoryOpen(false);
        return;
      }

      const range = selection.getRangeAt(0);
      const container = range.startContainer.nodeType === 3 ? range.startContainer.parentElement : range.commonAncestorContainer;
      if (highlightMenuRef.current?.contains(container)) return;

      if (customCategoryOpenRef.current) return;

      const block = container?.closest(".section-text");
      if (!block) {
        setHighlightMenu((current) => ({ ...current, visible: false }));
        return;
      }

      const sectionIndex = Number(block.dataset.sectionIndex ?? -1);
      const paragraphIndex = Number(block.dataset.paragraphIndex ?? -1);
      if (sectionIndex < 0 || paragraphIndex < 0) {
        setHighlightMenu((current) => ({ ...current, visible: false }));
        return;
      }

      if (focusModeRef.current && sectionIndex !== activeIndexRef.current) {
        setHighlightMenu((current) => ({ ...current, visible: false }));
        setStatus("Highlighting on non-focused panel is not allowed.");
        window.getSelection()?.removeAllRanges();
        return;
      }

      const beforeStart = range.cloneRange();
      beforeStart.selectNodeContents(block);
      beforeStart.setEnd(range.startContainer, range.startOffset);
      const start = beforeStart.toString().length;
      const selectedText = range.toString();
      const end = start + selectedText.length;

      const overlapsExisting = marksRef.current.some(
        (mark) => mark.sectionIndex === sectionIndex && mark.paragraphIndex === paragraphIndex && start < mark.end && end > mark.start
      );
      if (overlapsExisting) {
        setHighlightMenu((current) => ({ ...current, visible: false }));
        setStatus("That text is already highlighted.");
        window.getSelection()?.removeAllRanges();
        return;
      }

      const bounds = range.getBoundingClientRect();
      const left = Math.max(8, Math.min(window.innerWidth - 328, bounds.left + bounds.width / 2 - 150));
      const top = bounds.bottom + 10 > window.innerHeight - 220 ? Math.max(8, bounds.top - 210) : bounds.bottom + 10;

      setHighlightMenu({ visible: true, left, top, sectionIndex, paragraphIndex, start, end, text: selectedText.trim() });
    };

    document.addEventListener("selectionchange", handleSelection);
    return () => document.removeEventListener("selectionchange", handleSelection);
  }, [lesson]);

  const sections = lesson?.sections ?? [];
  const progress = useMemo(() => {
    const total = sections.length || 1;
    const completed = Object.values(done).filter(Boolean).length;
    return Math.round((completed / total) * 100);
  }, [done, sections.length]);

  const marksByCategory = useMemo(() => {
    return categories
      .map((category) => ({
        category,
        items: marks.filter((mark) => mark.categoryId === category.id),
      }))
      .filter((group) => group.items.length > 0);
  }, [categories, marks]);

  const closeHighlightMenu = () => {
    setHighlightMenu((current) => ({ ...current, visible: false }));
    setCustomCategoryOpen(false);
    setCustomCategoryLabel("");
    window.getSelection()?.removeAllRanges();
  };

  const cancelCustomHighlight = () => {
    setHighlightMenu((current) => ({ ...current, visible: false }));
    setCustomCategoryOpen(false);
    setCustomCategoryLabel("");
    setStatus("Custom highlight canceled.");
    window.getSelection()?.removeAllRanges();
  };

  const addHighlightMark = async (category) => {
    if (!highlightMenu.visible || !category) return;
    const { sectionIndex, paragraphIndex, start, end, text } = highlightMenu;
    if (!text) return;
    const section = sections[sectionIndex];
    if (!section) return;
    const paragraphText = getParagraphs(section.content ?? "")[paragraphIndex] ?? "";
    const actualStart = Math.max(0, Math.min(start, paragraphText.length));
    const actualEnd = Math.max(actualStart, Math.min(end, paragraphText.length));
    if (actualEnd <= actualStart) return;
    const markText = paragraphText.slice(actualStart, actualEnd).trim() || text;

    setStatus(`Saving to ${category.label}...`);
    try {
      // Highlights are stored in core_pin; the category is the collection row.
      const pin = await createPin({
        lessonId: Number(lessonId),
        sectionId: section.id,
        selectedText: markText,
        startPosition: actualStart,
        endPosition: actualEnd,
        paragraphIndex,
        collectionIds: [Number(category.id)],
      });
      setMarks((current) => [
        ...current,
        {
          id: pin.id,
          sectionId: section.id,
          sectionIndex,
          paragraphIndex,
          start: actualStart,
          end: actualEnd,
          text: pin.selectedText || markText,
          categoryId: category.id,
          categoryLabel: category.label,
          color: category.color,
        },
      ]);
      setStatus(`Saved to ${category.label}.`);
      closeHighlightMenu();
    } catch (err) {
      if (err.status === 401) {
        navigate("/login", { replace: true });
        return;
      }
      setStatus(`Could not save highlight: ${err.message}`);
      closeHighlightMenu();
    }
  };

  const handleAddCustomCategory = async () => {
    const label = customCategoryLabel.trim();
    if (!label || !highlightMenu.visible) return;
    const color = customCategoryColor || "#F4A9C8";

    setStatus(`Creating category "${label}"...`);
    const useCollection = (collection) => {
      const category = toCategory(collection);
      setCategories((current) => (current.some((item) => item.id === category.id) ? current : [...current, category]));
      addHighlightMark(category);
      setCustomCategoryLabel("");
      setCustomCategoryColor("#F4A9C8");
    };

    try {
      // Custom categories persist in core_collection on the server.
      const collection = await createCollection({ name: label, color });
      useCollection(collection);
    } catch (err) {
      if (err.status === 409) {
        // The name is already taken — reuse the existing collection.
        try {
          const all = await fetchCollections();
          const match = all.find((item) => String(item.name).toLowerCase() === label.toLowerCase());
          if (match) {
            useCollection(match);
            return;
          }
        } catch {
          // fall through to the generic error
        }
      }
      if (err.status === 401) {
        navigate("/login", { replace: true });
        return;
      }
      setStatus(`Could not create category: ${err.message}`);
    }
  };

  const handleSaveSectionLater = async (index) => {
    const category =
      categories.find((item) => item.builtInKey === "later") ||
      categories.find((item) => item.label.toLowerCase() === "save this for later");
    if (!category) {
      setStatus("No \"Save this for later\" category yet.");
      return;
    }
    const section = sections[index];
    if (!section) return;
    const paragraphText = getParagraphs(section.content ?? "")[0] ?? section.content ?? "";
    const snippet = (paragraphText || "Section").replace(/\s+/g, " ").trim().slice(0, 120);
    if (!snippet) return;
    const end = Math.min(snippet.length, paragraphText.length || snippet.length);

    if (marksRef.current.some((item) => item.categoryId === category.id && item.sectionIndex === index && item.start === 0 && item.end === end)) {
      setStatus("Already saved to Save this for later.");
      return;
    }

    setStatus("Saving to Save this for later...");
    try {
      const pin = await createPin({
        lessonId: Number(lessonId),
        sectionId: section.id,
        selectedText: snippet,
        startPosition: 0,
        endPosition: end,
        paragraphIndex: 0,
        collectionIds: [Number(category.id)],
      });
      setMarks((current) => [
        ...current,
        {
          id: pin.id,
          sectionId: section.id,
          sectionIndex: index,
          paragraphIndex: 0,
          start: 0,
          end,
          text: pin.selectedText || snippet,
          categoryId: category.id,
          categoryLabel: category.label,
          color: category.color,
        },
      ]);
      setStatus("Saved to Save this for later.");
    } catch (err) {
      if (err.status === 401) {
        navigate("/login", { replace: true });
        return;
      }
      setStatus(`Could not save: ${err.message}`);
    }
  };

  const jumpToMark = (mark) => {
    if (!mark) return;
    const targetSection = Number.isInteger(mark.sectionIndex) ? mark.sectionIndex : 0;
    setActiveIndex(targetSection);

    window.requestAnimationFrame(() => {
      const sectionNode = document.querySelectorAll(".chunk")[targetSection];
      if (sectionNode) {
        sectionNode.scrollIntoView({ behavior: "smooth", block: "start" });
      }

      window.setTimeout(() => {
        const el = document.querySelector(`[data-mark-id="${CSS.escape(String(mark.id))}"]`);
        if (!el) return;
        el.classList.remove("blink");
        // restart animation
        void el.offsetWidth;
        el.classList.add("blink");
        window.setTimeout(() => el.classList.remove("blink"), 2200);
      }, 220);
    });
  };

  const confirmDeleteMark = async () => {
    if (!pendingDeleteMark) return;
    const mark = pendingDeleteMark;
    setMarks((current) => current.filter((item) => item.id !== mark.id));
    setPendingDeleteMark(null);
    setStatus("Highlight removed.");
    try {
      // Deleting a highlight deletes its pin row in core_pin.
      await deletePinApi(mark.id);
    } catch (err) {
      if (err.status === 401) {
        navigate("/login", { replace: true });
        return;
      }
      setMarks((current) => [...current, mark]);
      setStatus(`Could not remove highlight: ${err.message}`);
    }
  };

  const handleNext = (index) => {
    const nextIndex = index < sections.length - 1 ? index + 1 : index;
    setDone((current) => ({ ...current, [index]: true }));
    setActiveIndex(nextIndex);
  };

  const clampMinutes = (value, min, max, fallback) => {
    const n = Number(value);
    if (!Number.isFinite(n)) return fallback;
    return Math.min(max, Math.max(min, Math.round(n)));
  };

  const selectedTimerPreset = useMemo(() => {
    if (timerPresetId === "custom") {
      return {
        id: "custom",
        label: "Custom",
        focusMinutes: clampMinutes(customFocusMinutes, 1, 180, 25),
        breakMinutes: clampMinutes(customBreakMinutes, 1, 60, 5),
      };
    }
    return TIMER_PRESETS.find((preset) => preset.id === timerPresetId) || TIMER_PRESETS[0];
  }, [timerPresetId, customFocusMinutes, customBreakMinutes]);

  const stopRing = () => {
    if (ringIntervalRef.current) {
      window.clearInterval(ringIntervalRef.current);
      ringIntervalRef.current = null;
    }
  };

  const playRingTone = () => {
    try {
      const AudioContextClass = window.AudioContext || window.webkitAudioContext;
      if (!AudioContextClass) return;
      if (!audioContextRef.current) audioContextRef.current = new AudioContextClass();
      const ctx = audioContextRef.current;
      if (ctx.state === "suspended") ctx.resume().catch(() => {});
      const now = ctx.currentTime;
      [0, 0.22, 0.44].forEach((offset, index) => {
        const oscillator = ctx.createOscillator();
        const gain = ctx.createGain();
        oscillator.type = "sine";
        oscillator.frequency.value = index % 2 === 0 ? 880 : 660;
        gain.gain.setValueAtTime(0.0001, now + offset);
        gain.gain.exponentialRampToValueAtTime(0.08, now + offset + 0.02);
        gain.gain.exponentialRampToValueAtTime(0.0001, now + offset + 0.18);
        oscillator.connect(gain);
        gain.connect(ctx.destination);
        oscillator.start(now + offset);
        oscillator.stop(now + offset + 0.2);
      });
    } catch {
      // Audio may be unavailable; the visual alert still works.
    }
  };

  const startRing = () => {
    stopRing();
    playRingTone();
    ringIntervalRef.current = window.setInterval(playRingTone, 1400);
  };

  const getAudioContext = () => {
    try {
      const AudioContextClass = window.AudioContext || window.webkitAudioContext;
      if (!AudioContextClass) return null;
      if (!audioContextRef.current || audioContextRef.current.state === "closed") {
        audioContextRef.current = new AudioContextClass();
      }
      if (audioContextRef.current.state === "suspended") {
        audioContextRef.current.resume().catch(() => {});
      }
      return audioContextRef.current;
    } catch {
      return null;
    }
  };

  const stopBackgroundSound = () => {
    soundNodesRef.current.forEach((node) => {
      try {
        if (typeof node.stop === "function") node.stop();
      } catch {
        // ignore
      }
      try {
        if (typeof node.disconnect === "function") node.disconnect();
      } catch {
        // ignore
      }
    });
    soundNodesRef.current = [];
  };

  const createNoiseBuffer = (ctx, type) => {
    const durationSeconds = 2;
    const frameCount = ctx.sampleRate * durationSeconds;
    const buffer = ctx.createBuffer(1, frameCount, ctx.sampleRate);
    const data = buffer.getChannelData(0);

    if (type === "brown") {
      let last = 0;
      for (let i = 0; i < frameCount; i += 1) {
        const white = Math.random() * 2 - 1;
        last = (last + 0.02 * white) / 1.02;
        data[i] = Math.max(-1, Math.min(1, last * 3.5));
      }
      return buffer;
    }

    if (type === "pink") {
      let b0 = 0;
      let b1 = 0;
      let b2 = 0;
      let b3 = 0;
      let b4 = 0;
      let b5 = 0;
      let b6 = 0;
      for (let i = 0; i < frameCount; i += 1) {
        const white = Math.random() * 2 - 1;
        b0 = 0.99886 * b0 + white * 0.0555179;
        b1 = 0.99332 * b1 + white * 0.0750759;
        b2 = 0.969 * b2 + white * 0.153852;
        b3 = 0.8665 * b3 + white * 0.3104856;
        b4 = 0.55 * b4 + white * 0.5329522;
        b5 = -0.7616 * b5 - white * 0.016898;
        const pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362;
        b6 = white * 0.115926;
        data[i] = Math.max(-1, Math.min(1, pink * 0.11));
      }
      return buffer;
    }

    for (let i = 0; i < frameCount; i += 1) {
      data[i] = Math.random() * 2 - 1;
    }
    return buffer;
  };

  const startBackgroundSound = (type, volume) => {
    stopBackgroundSound();
    if (!type || type === "off") return;
    const ctx = getAudioContext();
    if (!ctx) return;

    const gain = ctx.createGain();
    gain.gain.value = Math.max(0, Math.min(1, volume));
    gain.connect(ctx.destination);
    soundNodesRef.current.push(gain);

    if (type === "binaural") {
      const left = ctx.createOscillator();
      const right = ctx.createOscillator();
      const leftPan = ctx.createStereoPanner ? ctx.createStereoPanner() : null;
      const rightPan = ctx.createStereoPanner ? ctx.createStereoPanner() : null;
      const merger = ctx.createChannelMerger ? ctx.createChannelMerger(2) : null;

      left.type = "sine";
      right.type = "sine";
      left.frequency.value = 200;
      right.frequency.value = 210; // 10 Hz alpha beat

      if (leftPan && rightPan) {
        leftPan.pan.value = -1;
        rightPan.pan.value = 1;
        left.connect(leftPan);
        right.connect(rightPan);
        leftPan.connect(gain);
        rightPan.connect(gain);
        soundNodesRef.current.push(left, right, leftPan, rightPan);
      } else if (merger) {
        left.connect(merger, 0, 0);
        right.connect(merger, 0, 1);
        merger.connect(gain);
        soundNodesRef.current.push(left, right, merger);
      } else {
        left.connect(gain);
        right.connect(gain);
        soundNodesRef.current.push(left, right);
      }

      left.start();
      right.start();
      return;
    }

    const source = ctx.createBufferSource();
    source.buffer = createNoiseBuffer(ctx, type === "rain" ? "white" : type);
    source.loop = true;

    if (type === "rain") {
      const highpass = ctx.createBiquadFilter();
      highpass.type = "highpass";
      highpass.frequency.value = 700;
      const lowpass = ctx.createBiquadFilter();
      lowpass.type = "lowpass";
      lowpass.frequency.value = 5500;
      source.connect(highpass);
      highpass.connect(lowpass);
      lowpass.connect(gain);
      soundNodesRef.current.push(source, highpass, lowpass);
    } else {
      source.connect(gain);
      soundNodesRef.current.push(source);
    }

    source.start();
  };

  const ensureAudioReady = () => {
    getAudioContext();
  };

  useEffect(() => {
    startBackgroundSound(soundType, soundVolume);
  }, [soundType, soundVolume]);

  useEffect(() => {
    if (!timerRunning || timerAlert) return undefined;
    if (secondsLeft <= 0) {
      setTimerRunning(false);
      setTimerAlert({
        phase: timerPhase,
        message: timerPhase === "focus" ? "Take a break" : "Time to focus",
      });
      startRing();
      return undefined;
    }

    const interval = window.setInterval(() => {
      setSecondsLeft((current) => (current > 0 ? current - 1 : 0));
    }, 1000);

    return () => window.clearInterval(interval);
  }, [timerRunning, timerAlert, timerPhase, secondsLeft]);

  useEffect(() => () => {
    stopRing();
    stopBackgroundSound();
    if (audioContextRef.current) {
      audioContextRef.current.close().catch(() => {});
    }
  }, []);

  const handleTimerPresetChange = (event) => {
    const nextId = event.target.value;
    stopRing();
    setTimerAlert(null);
    setTimerRunning(false);
    setTimerPresetId(nextId);
    setTimerPhase("focus");
    if (nextId === "custom") {
      setSecondsLeft(clampMinutes(customFocusMinutes, 1, 180, 25) * 60);
      return;
    }
    const preset = TIMER_PRESETS.find((item) => item.id === nextId) || TIMER_PRESETS[0];
    setSecondsLeft(preset.focusMinutes * 60);
  };

  const applyCustomMinutes = (nextFocus, nextBreak) => {
    const focus = clampMinutes(nextFocus, 1, 180, 25);
    const brk = clampMinutes(nextBreak, 1, 60, 5);
    setCustomFocusMinutes(focus);
    setCustomBreakMinutes(brk);
    if (timerPresetId === "custom" && !timerRunning && !timerAlert) {
      setTimerPhase("focus");
      setSecondsLeft(focus * 60);
    }
  };

  const handleTimerToggle = () => {
    if (timerAlert) return;
    if (!timerRunning) ensureAudioReady();
    setTimerRunning((current) => !current);
  };

  const handleTimerReset = () => {
    stopRing();
    setTimerAlert(null);
    setTimerRunning(false);
    setTimerPhase("focus");
    setSecondsLeft(selectedTimerPreset.focusMinutes * 60);
  };

  const handleTimerAlertOk = () => {
    stopRing();
    const nextPhase = timerAlert?.phase === "focus" ? "break" : "focus";
    setTimerAlert(null);
    setTimerPhase(nextPhase);
    setSecondsLeft(nextPhase === "focus" ? selectedTimerPreset.focusMinutes * 60 : selectedTimerPreset.breakMinutes * 60);
    setTimerRunning(false);
  };

  if (loading) return <div style={{ padding: 32 }}>Loading lesson…</div>;
  if (error) {
    return (
      <div style={{ padding: 32 }}>
        <p role="alert">{error}</p>
        <Link to="/dashboard">Back to lessons</Link>
      </div>
    );
  }

  return (
    <div className="lesson-reader-shell">
      <style>{styles}</style>

      <header className="top">
        <button type="button" className="back-button" onClick={() => navigate(-1)} aria-label="Go back">
          <span className="back-button__icon" aria-hidden="true">
            <svg viewBox="0 0 20 20" width="18" height="18" focusable="false">
              <path d="M12.8 4.6a1 1 0 0 1 0 1.4L9.3 9.5H16a1 1 0 1 1 0 2H9.3l3.5 3.5a1 1 0 1 1-1.4 1.4l-5.2-5.2a1 1 0 0 1 0-1.4l5.2-5.2a1 1 0 0 1 1.4 0Z" fill="currentColor"/>
            </svg>
          </span>
          <span className="back-button__text">Back</span>
        </button>

        <div className="brand">
          <span className="brand-mark">L</span>
          Lessonfy
        </div>

        <div className="header-actions">
          <div className="timer" role="group" aria-label="Focus timer">
            <span className="time">{formatTimer(secondsLeft)}</span>
            <span className="phase">{timerPhase === "focus" ? "Focus" : "Break"}</span>
          </div>

          <div className="action-stack">
            <label className="sr-only" htmlFor="timer-preset" style={{ position: "absolute", width: 1, height: 1, overflow: "hidden" }}>
              Timer preset
            </label>
            <select
              id="timer-preset"
              className="timer-select"
              value={timerPresetId}
              onChange={handleTimerPresetChange}
              aria-label="Timer preset"
              title="Timer preset"
            >
              {TIMER_PRESETS.map((preset) => (
                <option key={preset.id} value={preset.id}>
                  {preset.label}
                </option>
              ))}
              <option value="custom">Custom minutes…</option>
            </select>
            {timerPresetId === "custom" && (
              <div className="custom-timer" role="group" aria-label="Custom timer minutes">
                <label className="custom-timer__field">
                  <span>Focus</span>
                  <input
                    type="number"
                    min="1"
                    max="180"
                    step="1"
                    inputMode="numeric"
                    value={customFocusMinutes}
                    onChange={(e) => applyCustomMinutes(e.target.value, customBreakMinutes)}
                    aria-label="Focus minutes"
                    title="Focus minutes"
                    disabled={timerRunning || !!timerAlert}
                  />
                  <span className="custom-timer__unit">min</span>
                </label>
                <label className="custom-timer__field">
                  <span>Break</span>
                  <input
                    type="number"
                    min="1"
                    max="60"
                    step="1"
                    inputMode="numeric"
                    value={customBreakMinutes}
                    onChange={(e) => applyCustomMinutes(customFocusMinutes, e.target.value)}
                    aria-label="Break minutes"
                    title="Break minutes"
                    disabled={timerRunning || !!timerAlert}
                  />
                  <span className="custom-timer__unit">min</span>
                </label>
              </div>
            )}
            <button className="btn pri" type="button" onClick={handleTimerToggle} disabled={!!timerAlert}>
              {timerRunning ? "Pause" : "Start"}
            </button>
            <button className="btn" type="button" onClick={handleTimerReset}>
              Reset
            </button>
          </div>
        </div>
      </header>

      <section className="tools" aria-label="Reading and study settings">
        <div className="tool-group">
          <span className="tool-group__title">Reading</span>
          <label className="tool-check">
            <input type="checkbox" checked={bionic} onChange={() => setBionic((value) => !value)} />
            Bionic
          </label>
          <label className="tool-check">
            <input type="checkbox" checked={focusMode} onChange={() => setFocusMode((value) => !value)} />
            Focus
          </label>
        </div>

        <div className="tool-group">
          <span className="tool-group__title">Display</span>
          <label className="tool-range">
            <span>Text size</span>
            <input type="range" min="16" max="28" step="1" value={fontSize} onChange={(e) => setFontSize(Number(e.target.value))} />
          </label>
          <label className="tool-range">
            <span>Line spacing</span>
            <input type="range" min="1.4" max="2.4" step="0.1" value={lineHeight} onChange={(e) => setLineHeight(Number(e.target.value))} />
          </label>
        </div>

        <div className="tool-group">
          <span className="tool-group__title">Atmosphere</span>
          <label className="tool-select">
            <span className="sr-only" style={{ position: "absolute", width: 1, height: 1, overflow: "hidden" }}>Theme</span>
            <select
              className="timer-select"
              value={normalizeThemeId(theme)}
              onChange={(e) => setTheme(normalizeThemeId(e.target.value))}
              aria-label="Theme"
              title="Theme"
            >
              {THEMES.map((option) => (
                <option key={option.id} value={option.id}>
                  {option.label} · {option.tone === "light" ? "Light" : "Dark"}
                </option>
              ))}
            </select>
          </label>
          <label className="tool-select">
            <span className="sr-only" style={{ position: "absolute", width: 1, height: 1, overflow: "hidden" }}>Background sound</span>
            <select
              className="timer-select"
              value={soundType}
              onChange={(event) => {
                ensureAudioReady();
                setSoundType(event.target.value);
                setStatus(event.target.value === "off" ? "Background sound turned off." : `Playing ${SOUND_OPTIONS.find((option) => option.id === event.target.value)?.label || "sound"}.`);
              }}
              aria-label="Background sound"
              title="Background sound"
            >
              {SOUND_OPTIONS.map((option) => (
                <option key={option.id} value={option.id}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
          <label className="tool-range tool-range--volume">
            <span>Volume</span>
            <input
              type="range"
              className="sound-volume"
              min="0"
              max="1"
              step="0.05"
              value={soundVolume}
              onChange={(event) => {
                ensureAudioReady();
                setSoundVolume(Number(event.target.value));
              }}
              aria-label="Sound volume"
              title="Sound volume"
              disabled={soundType === "off"}
            />
          </label>
        </div>
      </section>

      <div style={{ minHeight: "1.4em", padding: "0 clamp(14px, 4vw, 32px)", color: "var(--primary, #2b6e8f)", fontWeight: 700 }} role="status" aria-live="polite">
        {status || ""}
      </div>

      <main className={`wrap ${focusMode ? "focus" : ""}`}>
        <article className="reader" style={{ fontSize: `${fontSize}px`, lineHeight: lineHeight }}>
          <h1>{lesson?.title || "Lesson"}</h1>
          {lesson?.subtitle && <p className="hint">{lesson.subtitle}</p>}

          <div className="bar" aria-hidden="true">
            <i style={{ width: `${progress}%` }} />
          </div>

          <div className="pcount">
            {Object.values(done).filter(Boolean).length} of {sections.length || 0} sections done
          </div>

          {sections.map((section, index) => {
            const paragraphs = getParagraphs(section.content);
            const isActive = !focusMode || index === activeIndex;

            return (
              <section key={section.id ?? `${lessonId}-${index}`} className={`chunk ${isActive ? "active" : ""}`}>
                <div className="txt">
                  {section.title && <h2>{section.title}</h2>}
                  {paragraphs.map((paragraph, paragraphIndex) => {
                    const sectionMarks = marks.filter(
                      (mark) => mark.sectionIndex === index && mark.paragraphIndex === paragraphIndex
                    );
                    const display = renderHighlightedText(paragraph, sectionMarks, bionic ? getBionicText : undefined);

                    return (
                      <p key={`${section.id ?? index}-p-${paragraphIndex}`} className="section-text" data-section-index={index} data-paragraph-index={paragraphIndex}>
                        {display}
                      </p>
                    );
                  })}
                </div>

                <div className="row">
                  <button className="btn pri" type="button" onClick={() => handleNext(index)}>
                    {done[index] ? "Done" : index === sections.length - 1 ? "Mark done" : "Done, next"}
                  </button>
                </div>
              </section>
            );
          })}
        </article>

        <aside className="panel">
          <div className="panel-title">Highlighted texts ({marks.length})</div>
          {marksByCategory.length > 0 ? (
            <div className="highlight-groups">
              {marksByCategory.map(({ category, items }) => (
                <details key={category.id} className="highlight-group">
                  <summary>
                    <span className="highlight-group__meta">
                      <span className="highlight-dot" style={{ background: category.color }} aria-hidden="true" />
                      <span>{category.label}</span>
                    </span>
                    <span className="highlight-count">{items.length}</span>
                  </summary>
                  <div className="highlight-items">
                    {items.map((item) => (
                      <div key={item.id} className="highlight-item">
                        <button type="button" className="highlight-jump" onClick={() => jumpToMark(item)}>
                          <p>{item.text || "Highlighted text"}</p>
                          <small>{sections[item.sectionIndex]?.title || `Section ${item.sectionIndex + 1}`}</small>
                        </button>
                        <button
                          type="button"
                          className="btn small danger"
                          onClick={() => setPendingDeleteMark(item)}
                          aria-label={`Remove highlight: ${item.text || item.categoryLabel}`}
                        >
                          Remove
                        </button>
                      </div>
                    ))}
                  </div>
                </details>
              ))}
            </div>
          ) : (
            <div className="empty">No highlighted texts yet. Select text in the lesson to study it, save it for later, or add your own category.</div>
          )}
        </aside>
      </main>

      <div
        className={`highlight-menu ${highlightMenu.visible ? "open" : ""}`}
        style={{ left: highlightMenu.left, top: highlightMenu.top }}
        ref={highlightMenuRef}
        onMouseDown={(event) => event.preventDefault()}
      >
        <div className="highlight-menu__title">
          {customCategoryOpen ? "Name and choose a color" : "Highlight selection"}
        </div>
        <div className="highlight-menu__actions">
          {categories.map((category) => (
            <button
              key={category.id}
              type="button"
              className="btn highlight-menu__category-btn"
              onClick={() => addHighlightMark(category)}
              disabled={customCategoryOpen}
              title={category.label}
            >
              <span className="highlight-menu__swatch" style={{ background: category.color }} aria-hidden="true" />
              {category.label}
            </button>
          ))}
          <button
            type="button"
            className="btn"
            onClick={() => setCustomCategoryOpen(true)}
            aria-expanded={customCategoryOpen}
            disabled={customCategoryOpen}
            title="Add your own category"
          >
            +
          </button>
        </div>
        {customCategoryOpen && (
          <div className="highlight-menu__custom">
            <div className="highlight-menu__row">
              <input
                type="text"
                value={customCategoryLabel}
                onChange={(event) => setCustomCategoryLabel(event.target.value)}
                placeholder="Your category name"
                aria-label="Custom highlight category name"
                autoFocus
                onMouseDown={(event) => event.stopPropagation()}
              />
              <input
                type="color"
                value={customCategoryColor}
                onChange={(event) => setCustomCategoryColor(event.target.value)}
                aria-label="Custom highlight color"
                onMouseDown={(event) => event.stopPropagation()}
              />
            </div>
            <div className="highlight-menu__row">
              <button
                type="button"
                className="btn"
                onClick={cancelCustomHighlight}
                onMouseDown={(event) => event.preventDefault()}
              >
                Cancel
              </button>
              <button
                type="button"
                className="btn pri"
                onClick={handleAddCustomCategory}
                disabled={!customCategoryLabel.trim()}
                onMouseDown={(event) => event.preventDefault()}
              >
                Add and highlight
              </button>
            </div>
          </div>
        )}
      </div>

      {timerAlert && (
        <div className="timer-alert-overlay" role="presentation">
          <div
            className="timer-alert"
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="timer-alert-title"
            aria-describedby="timer-alert-desc"
          >
            <h2 id="timer-alert-title">{timerAlert.message}</h2>
            <p id="timer-alert-desc">
              {timerAlert.phase === "focus"
                ? "Focus time is up. Take a break before continuing."
                : "Break time is up. Ready to focus again?"}
            </p>
            <button type="button" className="btn pri" onClick={handleTimerAlertOk}>
              OK
            </button>
          </div>
        </div>
      )}

      {pendingDeleteMark && (
        <div className="timer-alert-overlay" role="presentation">
          <div
            className="timer-alert"
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="delete-highlight-title"
            aria-describedby="delete-highlight-desc"
          >
            <h2 id="delete-highlight-title">Remove highlight?</h2>
            <p id="delete-highlight-desc">
              "{pendingDeleteMark.text || "This highlight"}" will be removed from {pendingDeleteMark.categoryLabel || "your highlights"}.
            </p>
            <div className="actions">
              <button type="button" className="btn" onClick={() => setPendingDeleteMark(null)}>
                Cancel
              </button>
              <button type="button" className="btn danger" onClick={confirmDeleteMark}>
                Remove
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
