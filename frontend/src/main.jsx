import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App.jsx";
import { bootstrapSession } from "./bootstrap.js";
import "./styles.css";
import "./styles/theme.css";
import "./styles/lesson-creation.css";

bootstrapSession();

ReactDOM.createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>
);