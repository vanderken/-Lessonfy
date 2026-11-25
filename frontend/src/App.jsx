import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import DashboardPage from "./pages/DashboardPage.jsx";
import NewLessonPage from "./pages/NewLessonPage.jsx";
import EditLessonPage from "./pages/EditLessonPage.jsx";
import LessonReaderPage from "./pages/LessonReaderPage.jsx";
import LessonReviewPage from "./pages/LessonReviewPage.jsx";
import AuthPage from "./pages/AuthPage.jsx";
import { getCurrentUser } from "./api.js";

function RequireAuth({ children }) {
  const user = getCurrentUser();
  const location = useLocation();

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return children;
}

function RedirectIfLoggedIn({ children }) {
  const user = getCurrentUser();

  if (user) {
    return <Navigate to="/dashboard" replace />;
  }

  return children;
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<RedirectIfLoggedIn><AuthPage mode="login" /></RedirectIfLoggedIn>} />
      <Route path="/dashboard" element={<RequireAuth><DashboardPage /></RequireAuth>} />
      <Route path="/lessons/new" element={<RequireAuth><NewLessonPage /></RequireAuth>} />
      <Route path="/lessons/review" element={<RequireAuth><LessonReviewPage /></RequireAuth>} />
      <Route path="/lessons/:lessonId/edit" element={<RequireAuth><EditLessonPage /></RequireAuth>} />
      <Route path="/lessons/:lessonId" element={<RequireAuth><LessonReaderPage /></RequireAuth>} />
      <Route path="/login" element={<RedirectIfLoggedIn><AuthPage mode="login" /></RedirectIfLoggedIn>} />
      <Route path="/register" element={<RedirectIfLoggedIn><AuthPage mode="register" /></RedirectIfLoggedIn>} />
      <Route path="*" element={<p style={{ padding: 24 }}>Page not found.</p>} />
    </Routes>
  );
}