import { Routes, Route } from "react-router-dom";
import DashboardPage from "./pages/DashboardPage.jsx";
import NewLessonPage from "./pages/NewLessonPage.jsx";
import AuthPage from "./pages/AuthPage.jsx";
import RequireAuth from "./components/RequireAuth.jsx";

export default function App() {
  return (
    <Routes>
      <Route
        path="/"
        element={
          <RequireAuth>
            <DashboardPage />
          </RequireAuth>
        }
      />
      <Route
        path="/lessons/new"
        element={
          <RequireAuth>
            <NewLessonPage />
          </RequireAuth>
        }
      />
      <Route path="/login" element={<AuthPage mode="login" />} />
      <Route path="/register" element={<AuthPage mode="register" />} />
      <Route path="*" element={<p style={{ padding: 24 }}>Page not found.</p>} />
    </Routes>
  );
}