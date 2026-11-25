import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { changePassword, clearCurrentUser, getCurrentUser, getCurrentUserId } from "../api.js";
import { deleteLesson, fetchLessons } from "../api/lessons";
import LessonCreationDialog from "../components/LessonCreationDialog.jsx";
import useDebouncedValue from "../hooks/useDebouncedValue";
import { saveDraft } from "../lessonDraft.js";
import { THEMES, applyTheme, getInitialTheme } from "../theme.js";
import "./DashboardPage.css";

const PAGE_SIZE = 10;
const dateFormat = new Intl.DateTimeFormat(undefined, { dateStyle: "medium" });

export default function DashboardPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const user = getCurrentUser();
  const [searchInput, setSearchInput] = useState("");
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [reloadKey, setReloadKey] = useState(0);
  const [deletingId, setDeletingId] = useState(null);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [theme, setTheme] = useState(() => getInitialTheme(getCurrentUserId()));
  const [dialog, setDialog] = useState(() => ({
    open: Boolean(location.state?.creationStep),
    step: location.state?.creationStep ?? "choose",
    method: location.state?.method ?? null,
  }));
  const [flash, setFlash] = useState("");

  // Review screen sends the user back here to choose another method or file.
  useEffect(() => {
    const state = location.state;
    if (!state) return;
    if (state.flash) setFlash(state.flash);
    if (state.creationStep) {
      setDialog({ open: true, step: state.creationStep, method: state.method ?? null });
    }
    if (state.flash || state.creationStep) {
      navigate(location.pathname + location.search, { replace: true, state: null });
    }
  }, [location.key, location.state, location.pathname, location.search, navigate]);

  useEffect(() => {
    const syncTheme = () => setTheme(getInitialTheme(getCurrentUserId()));
    syncTheme();
    window.addEventListener("focus", syncTheme);
    window.addEventListener("storage", syncTheme);
    return () => {
      window.removeEventListener("focus", syncTheme);
      window.removeEventListener("storage", syncTheme);
    };
  }, []);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [passwordStatus, setPasswordStatus] = useState({ type: "", message: "" });
  const [passwordSaving, setPasswordSaving] = useState(false);

  const search = useDebouncedValue(searchInput.trim(), 300);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError("");

    fetchLessons({ search, page, size: PAGE_SIZE }, controller.signal)
      .then((res) => setData(res))
      .catch((err) => {
        if (err.name === "AbortError") return;
        if (err.status === 401) {
          navigate("/login");
          return;
        }
        setError("Could not load your lessons. Please try again.");
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [search, page, reloadKey, navigate]);

  const items = data?.items ?? [];
  const totalItems = data?.totalItems ?? 0;
  const totalPages = data?.totalPages ?? 0;
  const showEmpty = !loading && !error && data && items.length === 0;

  function handleLogout() {
    clearCurrentUser();
    navigate("/login", { replace: true });
  }

  function openCreationDialog() {
    setDialog({ open: true, step: "choose", method: null });
  }

  function closeCreationDialog() {
    setDialog((current) => ({ ...current, open: false }));
  }

  function handleManualCreation() {
    closeCreationDialog();
    navigate("/lessons/new");
  }

  function handleGenerated(generated, file) {
    saveDraft({
      title: generated.title,
      sections: generated.sections,
      method: dialog.method,
      fileName: file?.name ?? "",
      generator: generated.generator ?? "",
      fallback: Boolean(generated.fallback),
    });
    closeCreationDialog();
    navigate("/lessons/review");
  }

  function handleSearchChange(e) {
    setSearchInput(e.target.value);
    setPage(0);
  }

  function clearSearch() {
    setSearchInput("");
    setPage(0);
  }

  function handleThemeChange(event) {
    const nextTheme = applyTheme(event.target.value, getCurrentUserId());
    setTheme(nextTheme);
  }

  async function handleDeleteLesson(lesson) {
    if (!lesson?.id) return;
    const confirmed = window.confirm(`Delete "${lesson.title || "this lesson"}"? This cannot be undone.`);
    if (!confirmed) return;

    setDeletingId(lesson.id);
    setError("");
    try {
      await deleteLesson(lesson.id);
      setReloadKey((k) => k + 1);
    } catch (err) {
      if (err.status === 401) {
        navigate("/login");
        return;
      }
      setError("Could not delete the lesson. Please try again.");
    } finally {
      setDeletingId(null);
    }
  }

  async function handleChangePassword(event) {
    event.preventDefault();
    setPasswordStatus({ type: "", message: "" });

    if (!currentPassword) {
      setPasswordStatus({ type: "error", message: "Enter your current password." });
      return;
    }
    if (!newPassword || newPassword.length < 8) {
      setPasswordStatus({ type: "error", message: "New password must be at least 8 characters." });
      return;
    }
    if (newPassword !== confirmPassword) {
      setPasswordStatus({ type: "error", message: "New passwords do not match." });
      return;
    }

    setPasswordSaving(true);
    try {
      await changePassword({ currentPassword, newPassword });
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      setPasswordStatus({ type: "success", message: "Password updated successfully." });
    } catch (err) {
      setPasswordStatus({ type: "error", message: err?.message || "Could not update password." });
    } finally {
      setPasswordSaving(false);
    }
  }

  return (
    <main className="dash">
      <header className="dash__header">
        <h1 className="dash__title">My lessons</h1>
        <div className="dash__actions">
          <label className="dash__theme-picker">
            <span className="sr-only">Theme</span>
            <select value={theme} onChange={handleThemeChange} aria-label="Theme">
              {THEMES.map((option) => (
                <option key={option.id} value={option.id}>
                  {option.label} theme
                </option>
              ))}
            </select>
          </label>
          <button
            type="button"
            className="btn"
            onClick={() => setSettingsOpen((value) => !value)}
            aria-expanded={settingsOpen}
            aria-controls="dashboard-settings"
          >
            Settings
          </button>
          <button type="button" className="btn btn--primary" onClick={openCreationDialog}>
            New lesson
          </button>
          <button type="button" className="btn" onClick={handleLogout}>
            Logout
          </button>
        </div>
      </header>

      {flash && (
        <div className="dash__flash" role="status">
          <span>{flash}</span>
          <button type="button" className="btn" onClick={() => setFlash("")}>
            Dismiss
          </button>
        </div>
      )}

      {settingsOpen && (
        <section id="dashboard-settings" className="dash__settings" aria-label="Account settings">
          <div className="dash__settings-head">
            <h2 className="dash__settings-title">Account settings</h2>
          </div>

          <div className="dash__account-grid">
            <div className="dash__account-card">
              <h3 className="dash__account-heading">User info</h3>
              <dl className="dash__account-info">
                <div>
                  <dt>Name</dt>
                  <dd>{user?.name || "—"}</dd>
                </div>
                <div>
                  <dt>Email</dt>
                  <dd>{user?.email || "—"}</dd>
                </div>
              </dl>
            </div>

            <form className="dash__account-card" onSubmit={handleChangePassword}>
              <h3 className="dash__account-heading">Reset password</h3>
              <label className="dash__field">
                <span>Current password</span>
                <input
                  type="password"
                  autoComplete="current-password"
                  value={currentPassword}
                  onChange={(e) => setCurrentPassword(e.target.value)}
                />
              </label>
              <label className="dash__field">
                <span>New password</span>
                <input
                  type="password"
                  autoComplete="new-password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                />
              </label>
              <label className="dash__field">
                <span>Confirm new password</span>
                <input
                  type="password"
                  autoComplete="new-password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                />
              </label>
              {passwordStatus.message && (
                <p className={`dash__password-status ${passwordStatus.type === "error" ? "is-error" : "is-success"}`} role="status">
                  {passwordStatus.message}
                </p>
              )}
              <div>
                <button type="submit" className="btn btn--primary" disabled={passwordSaving}>
                  {passwordSaving ? "Updating…" : "Update password"}
                </button>
              </div>
            </form>
          </div>
        </section>
      )}

      <div className="dash__search">
        <label className="sr-only" htmlFor="lesson-search">
          Search lessons by title
        </label>
        <input
          id="lesson-search"
          type="search"
          className="dash__input"
          placeholder="Search lessons by title"
          value={searchInput}
          onChange={handleSearchChange}
          autoComplete="off"
        />
      </div>

      {error && (
        <div className="dash__error" role="alert">
          <span>{error}</span>
          <button type="button" className="btn" onClick={() => setReloadKey((k) => k + 1)}>
            Retry
          </button>
        </div>
      )}

      {data && !error && (
        <p className="dash__count" role="status">
          {totalItems} {totalItems === 1 ? "lesson" : "lessons"}
          {search ? ` matching \u201c${search}\u201d` : ""}
        </p>
      )}

      {loading && !data && <p className="dash__muted">Loading your lessons…</p>}

      {items.length > 0 && (
        <ul className="lesson-list" aria-busy={loading}>
          {items.map((lesson) => {
            const n = lesson.panelCount ?? 0;
            const isDeleting = deletingId === lesson.id;
            return (
              <li key={lesson.id} className="lesson-item">
                <Link className="lesson-row" to={`/lessons/${lesson.id}`}>
                  <span className="lesson-row__title">{lesson.title}</span>
                  <span className="lesson-row__meta">
                    {dateFormat.format(new Date(lesson.createdAt))} · {n} {n === 1 ? "panel" : "panels"}
                  </span>
                </Link>
                <div className="lesson-row__actions">
                  <Link className="btn" to={`/lessons/${lesson.id}/edit`} aria-label={`Modify ${lesson.title || "lesson"}`}>
                    Modify
                  </Link>
                  <button
                    type="button"
                    className="btn btn--danger"
                    onClick={() => handleDeleteLesson(lesson)}
                    disabled={loading || isDeleting}
                    aria-label={`Delete ${lesson.title || "lesson"}`}
                  >
                    {isDeleting ? "Deleting…" : "Delete"}
                  </button>
                </div>
              </li>
            );
          })}
        </ul>
      )}

      {showEmpty && !search && (
        <section className="dash__empty">
          <h2>No lessons yet</h2>
          <p>
            A lesson is a title plus one or more panels of text. Create one by typing it in, or
            upload a .txt or .docx file and Lessonfy will split it into panels for you.
          </p>
          <Link className="btn btn--primary" to="/lessons/new">
            Create your first lesson
          </Link>
        </section>
      )}

      {showEmpty && search && (
        <section className="dash__empty">
          <h2>No lessons match &ldquo;{search}&rdquo;</h2>
          <p>Check the spelling or try a shorter title.</p>
          <button type="button" className="btn" onClick={clearSearch}>
            Clear search
          </button>
        </section>
      )}

      {totalPages > 1 && (
        <nav className="dash__pager" aria-label="Lesson pages">
          <button
            type="button"
            className="btn"
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0 || loading}
          >
            Previous
          </button>
          <span className="dash__muted">
            Page {page + 1} of {totalPages}
          </span>
          <button
            type="button"
            className="btn"
            onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
            disabled={page >= totalPages - 1 || loading}
          >
            Next
          </button>
        </nav>
      )}

      {dialog.open && (
        <LessonCreationDialog
          step={dialog.step}
          method={dialog.method}
          onManual={handleManualCreation}
          onUploadSelected={() => setDialog((current) => ({ ...current, step: "method" }))}
          onMethodSelected={(method) => setDialog((current) => ({ ...current, step: "upload", method }))}
          onGenerated={handleGenerated}
          onClose={closeCreationDialog}
          onBack={() => setDialog((current) => ({
            ...current,
            step: current.step === "upload" ? "method" : "choose",
          }))}
        />
      )}
    </main>
  );
}
