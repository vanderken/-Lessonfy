import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { fetchLessons } from "../api/lessons";
import useDebouncedValue from "../hooks/useDebouncedValue";
import "../styles/theme.css";
import "./DashboardPage.css";

const PAGE_SIZE = 10;
const dateFormat = new Intl.DateTimeFormat(undefined, { dateStyle: "medium" });

export default function DashboardPage() {
  const navigate = useNavigate();
  const [searchInput, setSearchInput] = useState("");
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [reloadKey, setReloadKey] = useState(0);

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

  function handleSearchChange(e) {
    setSearchInput(e.target.value);
    setPage(0);
  }

  function clearSearch() {
    setSearchInput("");
    setPage(0);
  }

  return (
    <main className="dash">
      <header className="dash__header">
        <h1 className="dash__title">My lessons</h1>
        <Link className="btn btn--primary" to="/lessons/new">
          New lesson
        </Link>
      </header>

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
            return (
              <li key={lesson.id}>
                <Link className="lesson-row" to={`/lessons/${lesson.id}`}>
                  <span className="lesson-row__title">{lesson.title}</span>
                  <span className="lesson-row__meta">
                    {dateFormat.format(new Date(lesson.createdAt))} · {n} {n === 1 ? "panel" : "panels"}
                  </span>
                </Link>
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
    </main>
  );
}