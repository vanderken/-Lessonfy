import { Navigate } from "react-router-dom";
import { isSignedIn } from "../api.js";

export default function RequireAuth({ children }) {
  if (!isSignedIn()) return <Navigate to="/login" replace />;
  return children;
}