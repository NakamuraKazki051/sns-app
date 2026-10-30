import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router";
import { App } from "./App";
import { AuthProvider } from "./auth";
import { initialTheme } from "./components/Layout";
import { FeedbackProvider } from "./feedback";
import "./styles.css";

// 画面を描く前にテーマを決めて、白い画面が一瞬光らないようにする
document.documentElement.dataset.theme = initialTheme();

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <FeedbackProvider>
          <App />
        </FeedbackProvider>
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
);
