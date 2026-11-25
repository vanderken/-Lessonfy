import { useEffect, useRef, useState } from "react";
import PasswordField from "./PasswordField.jsx";
import { login, register, saveCurrentUser } from "../api.js";

const EMPTY = { firstName: "", lastName: "", email: "", password: "", confirm: "" };
const emailOk = (v) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v);
const strength = (p) =>
  [
    p.length >= 8,
    /[a-z]/.test(p) && /[A-Z]/.test(p),
    /\d/.test(p),
    /[^A-Za-z0-9]/.test(p) || p.length >= 12,
  ].filter(Boolean).length;

export default function AuthForm({ mode, go, onDone }) {
  const isReg = mode === "register";
  const [f, setF] = useState(EMPTY);
  const [errs, setErrs] = useState({});
  const [banner, setBanner] = useState(null);
  const [busy, setBusy] = useState(false);
  const emailRef = useRef(null);

  useEffect(() => {
    setF(EMPTY);
    setErrs({});
    setBanner(null);
  }, [mode]);

  const set = (k) => (v) => setF((s) => ({ ...s, [k]: v }));

  const validate = () => {
    const e = {};
    if (isReg && !f.firstName.trim()) e.firstName = "Enter your first name.";
    if (isReg && !f.lastName.trim()) e.lastName = "Enter your last name.";
    if (!f.email.trim()) e.email = "Enter your email address.";
    else if (!emailOk(f.email.trim())) e.email = "Enter a valid email, like name@school.edu.";
    if (!f.password) e.password = "Enter your password.";
    else if (isReg && f.password.length < 8) e.password = "Use at least 8 characters.";
    if (isReg && f.confirm !== f.password) e.confirm = "Passwords don’t match.";
    return e;
  };

  const submit = async (ev) => {
    ev.preventDefault();
    const e = validate();
    setErrs(e);
    setBanner(null);
    const first = Object.keys(e)[0];
    if (first) {
      document.getElementById(first)?.focus();
      return;
    }

    setBusy(true);
    try {
      const payload = { email: f.email.trim(), password: f.password };
      if (isReg) {
        payload.firstName = f.firstName.trim();
        payload.lastName = f.lastName.trim();
      }
      const data = await (isReg ? register(payload) : login(payload));

      if (!isReg) {
        saveCurrentUser({ id: data.id, email: data.email || payload.email, name: data.name || data.email });
      }

      onDone({ type: isReg ? "registered" : "signedin", id: data.id, email: data.email || payload.email, name: data.name || data.email });
    } catch (err) {
      if (isReg && /already exists/i.test(err.message)) setErrs({ email: err.message });
      else setBanner(err.message);
      emailRef.current?.focus();
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="card" onSubmit={submit} noValidate>
      <h2 id="form-title" tabIndex={-1}>{isReg ? "Create your account" : "Welcome back"}</h2>
      <p className="sub">
        {isReg
          ? "Your lessons, pins, and reading settings are saved to your account."
          : "Sign in to pick up where you stopped reading."}
      </p>

      <div aria-live="assertive">
        {banner && <div className="alert err" role="alert">{banner}</div>}
      </div>

      {isReg && (
        <div className="field name-row">
          <div>
            <label htmlFor="firstName">First name</label>
            <input
              id="firstName"
              type="text"
              autoComplete="given-name"
              value={f.firstName}
              onChange={(e) => set("firstName")(e.target.value)}
              aria-invalid={!!errs.firstName}
              aria-describedby={errs.firstName ? "firstName-err" : undefined}
            />
            {errs.firstName && <p className="field-err" id="firstName-err">{errs.firstName}</p>}
          </div>
          <div>
            <label htmlFor="lastName">Last name</label>
            <input
              id="lastName"
              type="text"
              autoComplete="family-name"
              value={f.lastName}
              onChange={(e) => set("lastName")(e.target.value)}
              aria-invalid={!!errs.lastName}
              aria-describedby={errs.lastName ? "lastName-err" : undefined}
            />
            {errs.lastName && <p className="field-err" id="lastName-err">{errs.lastName}</p>}
          </div>
        </div>
      )}

      <div className="field">
        <label htmlFor="email">Email</label>
        <input
          id="email"
          ref={emailRef}
          type="email"
          inputMode="email"
          autoComplete="email"
          value={f.email}
          onChange={(e) => set("email")(e.target.value)}
          aria-invalid={!!errs.email}
          aria-describedby={errs.email ? "email-err" : undefined}
        />
        {errs.email && <p className="field-err" id="email-err">{errs.email}</p>}
      </div>

      <PasswordField
        id="password"
        label="Password"
        value={f.password}
        onChange={set("password")}
        error={errs.password}
        autoComplete={isReg ? "new-password" : "current-password"}
        hint={isReg ? "At least 8 characters. A mix of letters, numbers, and symbols is stronger." : undefined}
      >
        {isReg && (
          <div className="meter" aria-hidden="true">
            {[1, 2, 3, 4].map((i) => (
              <i key={i} className={strength(f.password) >= i ? "on" : ""} />
            ))}
          </div>
        )}
      </PasswordField>

      {isReg && (
        <PasswordField
          id="confirm"
          label="Confirm password"
          value={f.confirm}
          onChange={set("confirm")}
          error={errs.confirm}
          autoComplete="new-password"
        />
      )}

      <button className="btn" type="submit" disabled={busy}>
        {busy && <span className="spinner" aria-hidden="true" />}
        {busy ? (isReg ? "Creating account…" : "Signing in…") : isReg ? "Create account" : "Sign in"}
      </button>

      <p className="switch">
        {isReg ? "Already have an account?" : "New to Lessonfy?"}{" "}
        <button type="button" className="link" onClick={() => go(isReg ? "login" : "register")}>
          {isReg ? "Sign in" : "Create an account"}
        </button>
      </p>
    </form>
  );
}