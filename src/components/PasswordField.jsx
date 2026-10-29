import { useState } from "react";

export default function PasswordField({ id, label, value, onChange, error, hint, autoComplete, children }) {
  const [show, setShow] = useState(false);
  const describedBy = [hint && !error && `${id}-hint`, error && `${id}-err`].filter(Boolean).join(" ") || undefined;

  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <div className="input-wrap">
        <input
          id={id}
          type={show ? "text" : "password"}
          value={value}
          autoComplete={autoComplete}
          onChange={(e) => onChange(e.target.value)}
          aria-invalid={!!error}
          aria-describedby={describedBy}
        />
        <button type="button" className="link toggle" aria-pressed={show} onClick={() => setShow((s) => !s)}>
          {show ? "Hide" : "Show"}
          <span className="sr"> password</span>
        </button>
      </div>
      {children}
      {hint && !error && <p className="hint" id={`${id}-hint`}>{hint}</p>}
      {error && <p className="field-err" id={`${id}-err`}>{error}</p>}
    </div>
  );
}
