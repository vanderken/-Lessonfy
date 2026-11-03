import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { LessonForm } from '../components/LessonForm';

export default function NewLessonPage() {
  const navigate = useNavigate();
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  const handleSave = async (lesson) => {
    setError('');
    setSaving(true);
    try {
      const res = await fetch('/api/lessons', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: lesson.title,
          subtitle: lesson.subtitle,
          panels: lesson.panels.map((p, i) => ({
            position: i,
            title: p.title,
            content: p.content,
          })),
        }),
      });
      if (res.status === 401) {
        navigate('/login');
        return;
      }
      if (!res.ok) {
        throw new Error('Could not save the lesson. Please try again.');
      }
      navigate('/');
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <main style={{ maxWidth: 800, margin: '0 auto', padding: 24 }}>
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