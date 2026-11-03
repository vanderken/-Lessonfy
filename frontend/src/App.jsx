import { Routes, Route } from 'react-router-dom';
import DashboardPage from './pages/DashboardPage';
import NewLessonPage from './pages/NewLessonPage';

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<DashboardPage />} />
      <Route path="/lessons/new" element={<NewLessonPage />} />
      <Route path="*" element={<p>Page not found.</p>} />
    </Routes>
  );
}