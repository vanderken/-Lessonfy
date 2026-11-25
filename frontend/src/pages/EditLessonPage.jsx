import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { LessonForm } from "../components/LessonForm.jsx";
import { fetchLesson, updateLesson } from "../api/lessons.js";
import { getCurrentUserId } from "../api.js";
import { applyTheme, getInitialTheme } from "../theme.js";

export default function EditLessonPage() {
  const navigate = useNavigate();
  const { lessonId } = useParams();
  const [lesson, setLesson] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    applyTheme(getInitialTheme(getCurrentUserId()), getCurrentUserId());
  }, []);

  useEffect(() => {
    let mounted = true;
    setLoading(true);
    setLoadError("");

    fetchLesson(lessonId)
      .then((data) => {
        if (mounted) setLesson(data);
      })
      .catch((err) => {
        if (!mounted) return;
        if (err.status === 401) {
          navigate("/login", { replace: true });
          return;
        }
        setLoadError("Could not load this lesson. Please try again.");
      })
      .finally(() => {
        if (mounted) setLoading(false);
      });

    return () => {
      mounted = false;
    };
  }, [lessonId, navigate]);

  const handleSave = async (formLesson) => {
    setError("");
    setSaving(true);
    try {
      await updateLesson(lessonId, {
        title: formLesson.title,
        subtitle: formLesson.subtitle,
        panels: formLesson.panels.map((p, i) => ({
          position: i,
          title: p.title,
          content: p.content,
        })),
      });
      navigate("/dashboard");
    } catch (err) {
      if (err.status === 401) {
        navigate("/login", { replace: true });
        return;
      }
      if (err.status === 403) {
        setError("Could not update the lesson (access denied). Please refresh and try again.");
        return;
      }
      if (err.status === 404) {
        setError("This lesson could not be found. It may have been deleted.");
        return;
      }
      setError("Could not update the lesson. Please try again.");
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <main style={{ maxWidth: 800, margin: "0 auto", padding: 24 }}>
        <p>Loading lesson…</p>
      </main>
    );
  }

  if (loadError) {
    return (
      <main style={{ maxWidth: 800, margin: "0 auto", padding: 24 }}>
        <p><Link to="/dashboard">← Back to lessons</Link></p>
        <p role="alert">{loadError}</p>
      </main>
    );
  }

  const initialPanels = (lesson?.sections ?? []).map((section) => ({
    id: section.id,
    title: section.title ?? "",
    content: section.content ?? "",
  }));

  return (
    <main style={{ maxWidth: 800, margin: "0 auto", padding: 24 }}>
      <p>
        <Link to="/dashboard">← Back to lessons</Link>
      </p>
      <h1>Edit lesson</h1>
      {error && <p role="alert">{error}</p>}
      {saving && <p>Saving...</p>}
      <LessonForm
        onSave={handleSave}
        initialTitle={lesson?.title ?? ""}
        initialSubtitle={lesson?.subtitle ?? ""}
        initialPanels={initialPanels}
        submitLabel="Update Lesson"
        saving={saving}
      />
    </main>
  );
}
