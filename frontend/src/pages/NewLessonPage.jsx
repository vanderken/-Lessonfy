import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { LessonForm } from "../components/LessonForm.jsx";
import { createLesson } from "../api/lessons.js";

export default function NewLessonPage() {
  const navigate = useNavigate();
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  const handleSave = async (lesson) => {
    setError("");
    setSaving(true);
    try {
      await createLesson({
        title: lesson.title,
        subtitle: lesson.subtitle,
        panels: lesson.panels.map((p, i) => ({
          position: i,
          title: p.title,
          content: p.content,
        })),
      });
      navigate("/");
    } catch (err) {
      if (err.status === 401) {
        navigate("/login");
        return;
      }
      setError("Could not save the lesson. Please try again.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <main style={{ maxWidth: 800, margin: "0 auto", padding: 24 }}>
      <p>
        <Link to="/">← Back to lessons</Link>
      </p>
      <h1>New lesson</h1>
      {error && <p role="alert">{error}</p>}
      {saving && <p>Saving...</p>}
      <LessonForm onSave={handleSave} />
    </main>
  );
}