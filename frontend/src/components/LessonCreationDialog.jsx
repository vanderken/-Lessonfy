import { useEffect, useRef } from "react";
import ConversionUploadPanel from "./ConversionUploadPanel.jsx";

const STEP_LABELS = { choose: 1, method: 2, upload: 3 };

export default function LessonCreationDialog({
  step,
  method,
  onManual,
  onUploadSelected,
  onMethodSelected,
  onGenerated,
  onClose,
  onBack,
}) {
  const dialogRef = useRef(null);

  useEffect(() => {
    dialogRef.current?.querySelector("button:not(:disabled)")?.focus({ preventScroll: true });
    function handleKey(event) {
      if (event.key === "Escape") onClose?.();
    }
    window.addEventListener("keydown", handleKey);
    return () => window.removeEventListener("keydown", handleKey);
  }, [step, onClose]);

  const current = STEP_LABELS[step] ?? 1;

  return (
    <div className="lc-backdrop" onMouseDown={() => onClose?.()}>
      <div
        className="lc-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="lc-dialog-title"
        ref={dialogRef}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="lc-progress-header">
          <span className="lc-progress-label">Step {current} of 3</span>
          <div className="lc-progress-dots" aria-hidden="true">
            {[1, 2, 3].map((number) => (
              <span key={number} className={number <= current ? "lc-dot is-on" : "lc-dot"} />
            ))}
          </div>
        </div>

        {step === "choose" && (
          <>
            <h2 className="lc-title" id="lc-dialog-title">How would you like to create a lesson?</h2>
            <p className="lc-intro">Pick one path. Each step is short and easy to undo.</p>
            <div className="lc-choice-grid">
              <button type="button" className="lc-choice" onClick={onManual} title="Opens the existing lesson editor.">
                <span className="lc-choice__icon" aria-hidden="true">✎</span>
                <span className="lc-choice__title">Create Manually</span>
                <span className="lc-choice__description">
                  Write your own lesson from scratch using the existing lesson editor.
                </span>
              </button>
              <button type="button" className="lc-choice" onClick={onUploadSelected} title="Choose how Lessonfyy should organize your file.">
                <span className="lc-choice__icon" aria-hidden="true">↑</span>
                <span className="lc-choice__title">Upload a File</span>
                <span className="lc-choice__description">
                  Upload your learning materials and let Lessonfyy organize them into structured lessons automatically.
                </span>
              </button>
            </div>
            <div className="lc-modal__footer">
              <button type="button" className="lc-link-button" onClick={onClose} title="Close this window and stay on your lessons.">
                Cancel
              </button>
            </div>
          </>
        )}

        {step === "method" && (
          <>
            <h2 className="lc-title" id="lc-dialog-title">How should we organize your document?</h2>
            <p className="lc-intro">Both methods prepare a lesson you can review and edit before saving.</p>
            <div className="lc-choice-grid">
              <button
                type="button"
                className="lc-choice"
                onClick={() => onMethodSelected("ai")}
                title="Uses Google Gemini for a faster AI conversion."
              >
                <span className="lc-choice__icon" aria-hidden="true">✦</span>
                <span className="lc-choice__title">AI-Powered Conversion</span>
                <span className="lc-choice__description">
                  Let Gemini read your document, organize its topics, and turn them into short, calm, structured lessons.
                </span>
              </button>
              <button
                type="button"
                className="lc-choice"
                onClick={() => onMethodSelected("semantic")}
                title="Uses headings and paragraphs only. No generative AI."
              >
                <span className="lc-choice__icon" aria-hidden="true">≡</span>
                <span className="lc-choice__title">Semantic-Based Conversion</span>
                <span className="lc-choice__description">
                  Let Lessonfyy organize your document using built-in text processing and document structure without using generative AI.
                </span>
              </button>
            </div>
            <div className="lc-modal__footer">
              <button type="button" className="lc-link-button" onClick={onBack} title="Return to the first choice.">
                ← Go back
              </button>
              <button type="button" className="lc-link-button" onClick={onClose} title="Close this window and stay on your lessons.">
                Cancel
              </button>
            </div>
          </>
        )}

        {step === "upload" && (
          <>
            <h2 className="lc-title" id="lc-dialog-title">Upload your learning material</h2>
            <p className="lc-intro">We will prepare a lesson for you to review. Nothing is saved until you choose to save it.</p>
            <ConversionUploadPanel
              method={method}
              onGenerated={(generated, file) => onGenerated?.(generated, file)}
              onCancel={onClose}
              onBack={onBack}
            />
          </>
        )}
      </div>
    </div>
  );
}
