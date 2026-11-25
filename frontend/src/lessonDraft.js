const DRAFT_KEY = "lessonfy-lesson-draft";

/**
 * The generated lesson is kept in sessionStorage so a refresh on the review
 * page does not lose work. It is cleared only after a successful save or an
 * explicit cancel.
 */
export function saveDraft(draft) {
  try {
    sessionStorage.setItem(DRAFT_KEY, JSON.stringify(draft));
  } catch {
    // Browsers can block storage; the review page still works in memory.
  }
}

export function loadDraft() {
  try {
    const raw = sessionStorage.getItem(DRAFT_KEY);
    if (!raw) return null;
    const draft = JSON.parse(raw);
    if (!draft || typeof draft.title !== "string" || !Array.isArray(draft.sections)) return null;
    return {
      title: draft.title,
      method: draft.method === "ai" ? "ai" : "semantic",
      fileName: String(draft.fileName ?? ""),
      sections: draft.sections.map((section, index) => ({
        id: String(section?.id ?? `section-${index}`),
        title: String(section?.title ?? ""),
        content: String(section?.content ?? ""),
      })),
    };
  } catch {
    return null;
  }
}

export function clearDraft() {
  try {
    sessionStorage.removeItem(DRAFT_KEY);
  } catch {
    // ignore
  }
}
