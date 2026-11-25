import React, { useState } from 'react';

//Panel Component
export const LessonPanel = ({ panel, index, onUpdate, onRemove }) => {
  return (
    <div className="panel-container">
      <div className="panel-header">
        <input
          className="lesson-panel-title"
          type="text"
          placeholder={`Panel ${index + 1} Title (Optional)`}
          value={panel.title}
          onChange={(e) => onUpdate(panel.id, 'title', e.target.value)}
        />
        <button type="button" className="lesson-panel-remove" onClick={() => onRemove(panel.id)}>
          Remove
        </button>
      </div>
      <textarea
        className="lesson-textarea"
        placeholder="Lesson text box"
        value={panel.content}
        onChange={(e) => onUpdate(panel.id, 'content', e.target.value)}
      />
    </div>
  );
};

// Main Form Component
export const LessonForm = ({
  onSave,
  initialTitle = '',
  initialSubtitle = '',
  initialPanels = null,
  submitLabel = 'Save Lesson',
  saving = false,
}) => {
  const [title, setTitle] = useState(initialTitle);
  const [subtitle, setSubtitle] = useState(initialSubtitle);

  // Starts with 3 panels by default, or uses provided initial panels for editing.
  const [panels, setPanels] = useState(() => {
    if (Array.isArray(initialPanels)) {
      if (initialPanels.length === 0) {
        return [{ id: `panel-0-${Date.now()}`, title: '', content: '' }];
      }
      return initialPanels.map((panel, index) => ({
        id: panel.id ?? `panel-${index}-${Date.now()}`,
        title: panel.title ?? '',
        content: panel.content ?? '',
      }));
    }
    return [
      { id: 1, title: '', content: '' },
      { id: 2, title: '', content: '' },
      { id: 3, title: '', content: '' }
    ];
  });

  const handleAddPanel = () => {
    setPanels([...panels, { id: Date.now(), title: '', content: '' }]);
  };

  const handleRemovePanel = (id) => {
    setPanels(panels.filter(p => p.id !== id));
  };

  const handlePanelUpdate = (id, field, value) => {
    setPanels(panels.map(p => p.id === id ? { ...p, [field]: value } : p));
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    // Passes data up to the parent component so saving logic can show the lesson in the lesson list
    if (onSave && !saving) {
      onSave({ title, subtitle, panels });
    }
  };

  return (
    <form onSubmit={handleSubmit} className="lesson-form">
      <div className="lesson-form__fields">
        <input
          className="lesson-input"
          type="text"
          placeholder="Lesson Title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          required
        />
        <input
          className="lesson-input"
          type="text"
          placeholder="Lesson Subtitle"
          value={subtitle}
          onChange={(e) => setSubtitle(e.target.value)}
        />
      </div>

      {panels.map((panel, index) => (
        <LessonPanel
          key={panel.id}
          index={index}
          panel={panel}
          onUpdate={handlePanelUpdate}
          onRemove={handleRemovePanel}
        />
      ))}

      <div className="lesson-form__actions">
        <button type="button" className="lesson-button lesson-button--secondary" onClick={handleAddPanel} disabled={saving}>
          Add panel
        </button>
        <button type="submit" className="lesson-button lesson-button--primary" disabled={saving}>
          {saving ? 'Saving…' : submitLabel}
        </button>
      </div>
    </form>
  );
};