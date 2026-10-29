import { Routes, Route } from "react-router-dom";
import DashboardPage from "./pages/DashboardPage.jsx";

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<DashboardPage />} />
      <Route path="*" element={<p style={{ padding: 24 }}>Page not built yet.</p>} />
    </Routes>
  );
}