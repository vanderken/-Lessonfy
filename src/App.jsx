import { useEffect, useState } from "react";
import Side from "./components/Side.jsx";
import AuthForm from "./components/AuthForm.jsx";
import Done from "./components/Done.jsx";

const hashMode = () => (location.hash === "#register" ? "register" : "login");

export default function App() {
  const [mode, setMode] = useState(hashMode());
  const [result, setResult] = useState(null);
  const [theme, setTheme] = useState(null); // null = follow system

  useEffect(() => {
    const onHash = () => {
      setMode(hashMode());
      setResult(null);
    };
    window.addEventListener("hashchange", onHash);
    return () => window.removeEventListener("hashchange", onHash);
  }, []);

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

  const go = (m) => {
    location.hash = m;
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
          <AuthForm mode={mode} go={go} onDone={setResult} />
        )}
      </main>
    </div>
  );
}
