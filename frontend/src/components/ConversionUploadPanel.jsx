import { useEffect, useRef, useState } from "react";
import { convertDocument, validateFile } from "../api/conversion.js";

const STATUS_MESSAGES = [
  "Uploading your document…",
  "Extracting your learning materials…",
  "Organizing your lesson sections…",
  "Preparing your lessons for review…",
];

const METHOD_INFO = {
  ai: {
    label: "AI-Powered Conversion",
    hint: "Gemini reads your document and writes short, calm, easy-to-scan lesson sections.",
    action: "Organize with AI",
    actionHint: "Send this document to Gemini and prepare a lesson for review.",
  },
  semantic: {
    label: "Semantic-Based Conversion",
    hint: "Lessonfyy uses headings and paragraphs only. No generative AI is used.",
    action: "Organize without AI",
    actionHint: "Group this document's own headings and paragraphs into a lesson.",
  },
};

/**
 * Shared upload + processing UI. Used by the creation dialog and by the
 * "Regenerate" area on the review page.
 */
export default function ConversionUploadPanel({ method, onGenerated, onCancel, onBack }) {
  const [file, setFile] = useState(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [statusIndex, setStatusIndex] = useState(0);
  const controllerRef = useRef(null);
  const info = METHOD_INFO[method] ?? METHOD_INFO.semantic;

  useEffect(() => {
    return () => controllerRef.current?.abort();
  }, []);

  useEffect(() => {
    if (!busy) return undefined;
    const timer = window.setInterval(() => {
      setStatusIndex((index) => (index + 1) % STATUS_MESSAGES.length);
    }, 2600);
    return () => window.clearInterval(timer);
  }, [busy]);

  function handleFileChange(event) {
    const selected = event.target.files?.[0] ?? null;
    setFile(selected);
    setError(selected ? validateFile(selected) : "Choose a file before continuing.");
  }

  async function handleConvert() {
    const problem = validateFile(file);
    if (problem) {
      setError(problem);
      return;
    }
    setError("");
    setBusy(true);
    setStatusIndex(0);
    const controller = new AbortController();
    controllerRef.current = controller;

    try {
      const generated = await convertDocument({ file, method, signal: controller.signal });
      onGenerated?.(generated, file);
    } catch (err) {
      if (err?.name === "AbortError") return;
      setError(err?.message || "We could not organize this document. Please try again.");
    } finally {
      controllerRef.current = null;
      if (!controller.signal.aborted) setBusy(false);
    }
  }

  function handleCancel() {
    controllerRef.current?.abort();
    setBusy(false);
    onCancel?.();
  }

  return (
    <div className="lc-upload">
      <div className="lc-upload__method">
        <p className="lc-eyebrow">Selected method</p>
        <h3 className="lc-upload__method-title">{info.label}</h3>
        <p className="lc-help">{info.hint}</p>
      </div>

      <label className="lc-file-label" htmlFor="lc-file-input">
        Step 3: Choose your document
      </label>
      <p className="lc-help" id="lc-file-help">
        Supported: PDF, Word (.docx), PowerPoint (.pptx), and text (.txt). Maximum size: 10 MB.
      </p>
      <input
        id="lc-file-input"
        className="lc-file-input"
        type="file"
        accept=".pdf,.docx,.pptx,.txt"
        onChange={handleFileChange}
        aria-describedby="lc-file-help"
        disabled={busy}
      />

      {file && !error && (
        <p className="lc-file-selected" role="status">
          Ready to use: <strong>{file.name}</strong>
        </p>
      )}
      {error && (
        <p className="lc-error" role="alert">
          {error}
        </p>
      )}

      {busy ? (
        <div className="lc-progress" role="status" aria-live="polite">
          <span className="lc-spinner" aria-hidden="true" />
          <div>
            <p className="lc-progress__title">{STATUS_MESSAGES[statusIndex]}</p>
            <p className="lc-help">This can take a minute. Please stay on this page.</p>
          </div>
        </div>
      ) : (
        <div className="lc-actions">
          <div className="lc-action">
            <button
              type="button"
              className="lc-button lc-button--primary"
              onClick={handleConvert}
              disabled={!file || Boolean(error)}
              title={info.actionHint}
            >
              {info.action}
            </button>
            <p className="lc-action__hint">{info.actionHint}</p>
          </div>
          <div className="lc-action">
            <button
              type="button"
              className="lc-button lc-button--secondary"
              onClick={handleCancel}
              title="Stop and return to the previous step."
            >
              Cancel upload
            </button>
            <p className="lc-action__hint">Stop and return to the previous step.</p>
          </div>
        </div>
      )}

      {!busy && onBack && (
        <button type="button" className="lc-link-button" onClick={onBack} title="Return to the method choice.">
          ← Go back to conversion methods
        </button>
      )}
    </div>
  );
}
