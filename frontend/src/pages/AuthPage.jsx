import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Side from "../components/Side.jsx";
import AuthForm from "../components/AuthForm.jsx";
import Done from "../components/Done.jsx";

export default function AuthPage({ mode }) {
  const navigate = useNavigate();
  const [result, setResult] = useState(null);
  const [theme, setTheme] = useState(null); // null = follow system

  useEffect(() => {
    setResult(null);
  }, [mode]);

  useEffect(() => {
    if (theme) document.documentElement.setAttribute("data-theme", theme);
    else document.documentElement.removeAttribute("data-theme");
  }, [theme]);

  useEffect(() => {
    document.title = result
      ? "Lessonfy"
      : mode === "register"
      ? "Lessonfy: Create account"
      : "Lessonfy: Sign in";
    document.getElementById("form-title")?.focus({ preventScroll: true });
  }, [mode, result]);

  const go = (m) => navigate("/" + m);

  const onDone = (r) => {
    if (r.type === "signedin") {
      navigate("/dashboard");
      return;
    }

    if (r.type === "registered") {
      navigate("/login");
      return;
    }

    setResult(r);
  };

  const isDark = theme
    ? theme === "dark"
    : window.matchMedia("(prefers-color-scheme: dark)").matches;

  return (
    <div className="shell">
      <Side />
      <main>
        <button
          className="ghost theme"
          aria-pressed={isDark}
          onClick={() => setTheme(isDark ? "light" : "dark")}
        >
          {isDark ? "Light theme" : "Dark theme"}
        </button>
        {result ? (
          <Done
            result={result}
            reset={() => {
              setResult(null);
              go("login");
            }}
          />
        ) : (
          <AuthForm mode={mode} go={go} onDone={onDone} />
        )}
      </main>
    </div>
  );
}