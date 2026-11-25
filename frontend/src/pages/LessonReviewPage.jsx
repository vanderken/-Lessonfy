import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { createLesson } from "../api/lessons.js";
import { getCurrentUserId } from "../api.js";
import ConversionUploadPanel from "../components/ConversionUploadPanel.jsx";
import { clearDraft, loadDraft, saveDraft } from "../lessonDraft.js";
import { applyTheme, getInitialTheme } from "../theme.js";

function newId() {
  return `section-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

const GENERATOR_LABELS = {
  gemini: "Google Gemini",
  groq: "Groq",
  claude: "Claude",
  semantic: "Smart structure",
};

export default function LessonReviewPage() {
  const navigate = useNavigate();
  const initialDraft = useMemo(() => loadDraft(), []);
  const [lesson, setLesson] = useState(() => ({
    title: initialDraft?.title ?? "",
    sections: initialDraft?.sections ?? [],
  }));
  const [baseline, setBaseline] = useState(() => JSON.stringify({
    title: initialDraft?.title ?? "",
    sections: initialDraft?.sections ?? [],
  }));
  const [method] = useState(initialDraft?.method ?? "semantic");
  const [generator, setGenerator] = useState(initialDraft?.generator ?? "");
  const [fallback, setFallback] = useState(Boolean(initialDraft?.fallback));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [status, setStatus] = useState("");
  const [regenerateOpen, setRegenerateOpen] = useState(false);

  const dirty = JSON.stringify(lesson) !== baseline;

  useEffect(() => {
    applyTheme(getInitialTheme(getCurrentUserId()), getCurrentUserId());
  }, []);

  useEffect(() => {
    if (initialDraft) saveDraft({ ...initialDraft, ...lesson });
  }, [lesson, initialDraft]);

  useEffect(() => {
    if (!dirty) return undefined;
    function warn(event) {
      event.preventDefault();
      event.returnValue = "";
    }
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);

  function updateSection(id, field, value) {
    setLesson((current) => ({
      ...current,
      sections: current.sections.map((section) =>
        section.id === id ? { ...section, [field]: value } : section),
    }));
  }

  function addSection() {
    setLesson((current) => ({
      ...current,
      sections: [...current.sections, { id: newId(), title: "", content: "" }],
    }));
    setStatus("A new section was added. Add a title and text when you are ready.");
  }

  function deleteSection(id) {
    const section = lesson.sections.find((item) => item.id === id);
    if (section && (section.title.trim() || section.content.trim())) {
      const confirmed = window.confirm(
        `Delete section "${section.title || "Untitled section"}"? This cannot be undone.`);
      if (!confirmed) return;
    }
    setLesson((current) => ({
      ...current,
      sections: current.sections.filter((item) => item.id !== id),
    }));
    setStatus("Section deleted. You can add another one at any time.");
  }

  function moveSection(index, direction) {
    const target = index + direction;
    if (target < 0 || target >= lesson.sections.length) return;
    setLesson((current) => {
      const sections = [...current.sections];
      [sections[index], sections[target]] = [sections[target], sections[index]];
      return { ...current, sections };
    });
  }

  function handleRegenerated(generated) {
    if (dirty) {
      const confirmed = window.confirm(
        "Replace your current draft with the new result? Your manual edits will be lost.");
      if (!confirmed) {
        setStatus("Your edits were kept. Nothing was replaced.");
        setRegenerateOpen(false);
        return;
      }
    }
    const next = { title: generated.title, sections: generated.sections };
    const nextGenerator = generated.generator ?? generator;
    const nextFallback = Boolean(generated.fallback);
    setLesson(next);
    setBaseline(JSON.stringify(next));
    setGenerator(nextGenerator);
    setFallback(nextFallback);
    if (initialDraft) saveDraft({ ...initialDraft, ...next, generator: nextGenerator, fallback: nextFallback });
    setRegenerateOpen(false);
    setStatus("A new lesson was prepared. Review it, then save when you are happy with it.");
  }

  async function handleSave() {
    setError("");
    setStatus("");
    const title = lesson.title.trim();
    if (!title) {
      setError("Add a lesson title before saving.");
      return;
    }
    const usable = lesson.sections.filter((section) => section.title.trim() || section.content.trim());
    if (usable.length === 0) {
      setError("Add at least one section with a title or lesson text before saving.");
      return;
    }

    setSaving(true);
    try {
      await createLesson({
        title,
        subtitle: "",
        panels: usable.map((section, index) => ({
          position: index,
          title: section.title.trim(),
          content: section.content.trim(),
        })),
      });
      clearDraft();
      navigate("/dashboard", {
        replace: true,
        state: { flash: "Your lesson was saved to your Lessonfyy library." },
      });
    } catch (err) {
      if (err?.status === 401) {
        navigate("/login", { replace: true });
        return;
      }
      setError("Could not save the lesson. Your edits are still here — please try again.");
    } finally {
      setSaving(false);
    }
  }

  function handleGoBack() {
    navigate("/dashboard", { state: { creationStep: "method", method } });
  }

  function handleCancel() {
    if (dirty) {
      const confirmed = window.confirm(
        "Leave lesson creation? Your unsaved edits will be lost.");
      if (!confirmed) return;
    }
    clearDraft();
    navigate("/dashboard", { replace: true });
  }

  if (!initialDraft) {
    return (
      <main className="lc-page">
        <div className="lc-page__inner">
          <h1>No lesson is ready for review</h1>
          <p className="lc-help">Upload a document first, and Lessonfyy will prepare a lesson here.</p>
          <Link className="lc-button lc-button--primary" to="/dashboard" state={{ creationStep: "choose" }}>
            Start a new lesson
          </Link>
          <p className="lc-action__hint">Return to your lessons and choose how to create one.</p>
        </div>
      </main>
    );
  }

  return (
    <main className="lc-page">
      <div className="lc-page__inner">
        <p className="lc-eyebrow">Step 4 of 4 · Review</p>
        <h1 className="lc-page__title">Your Lessons Are Ready! Review Before Proceeding</h1>
        <p className="lc-page__intro">
          Lessonfyy has organized your document into lessons. Take a moment to review the titles and content,
          make any changes you need, and save when you&apos;re satisfied.
        </p>

        {generator && (
          <p className="lc-help">
            Generated with {GENERATOR_LABELS[generator] ?? generator}
            {fallback ? " (backup provider used)" : ""}.
          </p>
        )}

        <div className="lc-page__bar">
          <span>{lesson.sections.length} {lesson.sections.length === 1 ? "section" : "sections"} in this lesson</span>
          {dirty && <span className="lc-unsaved">You have unsaved edits</span>}
        </div>

        {error && <p className="lc-error" role="alert">{error}</p>}
        {status && <p className="lc-success" role="status">{status}</p>}

        <section className="lc-editor-card" aria-labelledby="lc-lesson-title-label">
          <label className="lc-field-label" htmlFor="lc-lesson-title" id="lc-lesson-title-label">
            Main lesson title
          </label>
          <p className="lc-help">Choose a short title that tells you what this lesson is about.</p>
          <input
            id="lc-lesson-title"
            className="lc-input"
            type="text"
            value={lesson.title}
            onChange={(event) => setLesson((current) => ({ ...current, title: event.target.value }))}
            placeholder="Lesson title"
          />

          <div className="lc-sections-head">
            <h2 className="lc-section-heading">Lesson sections</h2>
            <p className="lc-help">Edit each title and text. Move sections up or down, or remove ones you do not need.</p>
          </div>

          {lesson.sections.map((section, index) => (
            <article className="lc-section-card" key={section.id}>
              <div className="lc-section-card__head">
                <h3>Section {index + 1}</h3>
                <div className="lc-section-tools">
                  <button
                    type="button"
                    className="lc-tool"
                    onClick={() => moveSection(index, -1)}
                    disabled={index === 0}
                    title="Move this section before the previous one."
                  >
                    ↑ Move up
                  </button>
                  <button
                    type="button"
                    className="lc-tool"
                    onClick={() => moveSection(index, 1)}
                    disabled={index === lesson.sections.length - 1}
                    title="Move this section after the next one."
                  >
                    ↓ Move down
                  </button>
                  <button
                    type="button"
                    className="lc-tool lc-tool--danger"
                    onClick={() => deleteSection(section.id)}
                    title="Delete this section. This cannot be undone."
                  >
                    Delete section
                  </button>
                </div>
              </div>
              <label className="lc-field-label" htmlFor={`section-title-${section.id}`}>
                Section {index + 1} title
              </label>
              <input
                id={`section-title-${section.id}`}
                className="lc-input"
                type="text"
                value={section.title}
                onChange={(event) => updateSection(section.id, "title", event.target.value)}
                placeholder="Short section title"
              />
              <label className="lc-field-label" htmlFor={`section-text-${section.id}`}>
                Lesson text for section {index + 1}
              </label>
              <textarea
                id={`section-text-${section.id}`}
                className="lc-textarea"
                rows={6}
                value={section.content}
                onChange={(event) => updateSection(section.id, "content", event.target.value)}
                placeholder="Write or edit the lesson text here"
              />
            </article>
          ))}

          <div className="lc-action lc-add-section">
            <button type="button" className="lc-button lc-button--secondary" onClick={addSection} title="Add another editable section to this lesson.">
              Add Section
            </button>
            <p className="lc-action__hint">Add another section to your lesson.</p>
          </div>
        </section>

        <section className="lc-review-actions" aria-label="Lesson review actions">
          <div className="lc-action">
            <button
              type="button"
              className="lc-button lc-button--primary"
              onClick={handleSave}
              disabled={saving}
              title="Save your reviewed lesson to your Lessonfyy library."
            >
              {saving ? "Saving…" : "Save My Lesson"}
            </button>
            <p className="lc-action__hint">Save your reviewed lesson to your Lessonfyy library.</p>
          </div>
          <div className="lc-action">
            <button
              type="button"
              className="lc-button lc-button--secondary"
              onClick={() => setRegenerateOpen((open) => !open)}
              aria-expanded={regenerateOpen}
              title="Generate the lesson again if the current result is not what you expected."
            >
              Regenerate / Retry
            </button>
            <p className="lc-action__hint">Generate again if this result is not what you expected.</p>
          </div>
          <div className="lc-action">
            <button type="button" className="lc-button lc-button--secondary" onClick={handleGoBack} title="Return to the previous step without losing your current progress.">
              Go Back
            </button>
            <p className="lc-action__hint">Return to the previous step without losing your current progress.</p>
          </div>
          <div className="lc-action">
            <button type="button" className="lc-button lc-button--danger" onClick={handleCancel} title="Exit lesson creation. Unsaved work will be discarded after confirmation.">
              Cancel
            </button>
            <p className="lc-action__hint">Exit lesson creation. Unsaved work is discarded after confirmation.</p>
          </div>
        </section>

        {regenerateOpen && (
          <section className="lc-editor-card lc-regenerate" aria-label="Regenerate lesson">
            <h2>Generate this lesson again</h2>
            <p className="lc-help">
              Choose another file and run the same method again. Your current lesson is kept unless you confirm a replacement.
            </p>
            <ConversionUploadPanel
              method={method}
              onGenerated={handleRegenerated}
              onCancel={() => setRegenerateOpen(false)}
              onBack={() => setRegenerateOpen(false)}
            />
          </section>
        )}
      </div>
    </main>
  );
}
