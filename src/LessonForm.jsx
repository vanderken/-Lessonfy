import React, { useState } from 'react';

//Panel Component
export const LessonPanel = ({ panel, index, onUpdate, onRemove }) => {
  return (
    <div className="panel-container" style={{ border: '1px solid #ccc', margin: '10px 0', padding: '10px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '10px' }}>
        <input
          type="text"
          placeholder={`Panel ${index + 1} Title (Optional)`}
          value={panel.title}
          onChange={(e) => onUpdate(panel.id, 'title', e.target.value)}
          style={{ width: '80%' }}
        />
        <button type="button" onClick={() => onRemove(panel.id)}>
          Remove
        </button>
      </div>
      <textarea
        placeholder="Lesson text box"
        value={panel.content}
        onChange={(e) => onUpdate(panel.id, 'content', e.target.value)}
        style={{ width: '100%', minHeight: '80px' }}
      />
    </div>
  );
};

// Main Form Component
export const LessonForm = ({ onSave }) => {
  const [title, setTitle] = useState('');
  const [subtitle, setSubtitle] = useState('');
  
  // Starts with 3 panels by default
  const [panels, setPanels] = useState([
    { id: 1, title: '', content: '' },
    { id: 2, title: '', content: '' },
    { id: 3, title: '', content: '' }
  ]);

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
    if (onSave) {
      onSave({ title, subtitle, panels });
    }
  };

  return (
    <form onSubmit={handleSubmit} className="lesson-form">
      <div style={{ marginBottom: '20px' }}>
        <input
          type="text"
          placeholder="Lesson Title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          required
          style={{ display: 'block', width: '100%', marginBottom: '10px' }}
        />
        <input
          type="text"
          placeholder="Lesson Subtitle"
          value={subtitle}
          onChange={(e) => setSubtitle(e.target.value)}
          style={{ display: 'block', width: '100%' }}
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

      <div style={{ marginTop: '20px', display: 'flex', gap: '10px' }}>
        <button type="button" onClick={handleAddPanel}>
          Add panel
        </button>
        <button type="submit">
          Save Lesson
        </button>
      </div>
    </form>
  );
};