import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useLocation } from "react-router";

type FeedbackContextValue = {
  /** 画面上部の赤いバナーにエラーを出す */
  showError: (message: string) => void;
  clearError: () => void;
  /** 画面下に少しだけ完了メッセージを出す */
  showToast: (message: string) => void;
};

const FeedbackContext = createContext<FeedbackContextValue | null>(null);

export function FeedbackProvider({ children }: { children: ReactNode }) {
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const toastTimer = useRef<number | undefined>(undefined);
  const location = useLocation();

  // 別の画面に移ったら、前の画面のエラーは消す
  useEffect(() => setError(null), [location.pathname]);
  useEffect(() => () => window.clearTimeout(toastTimer.current), []);

  const showToast = useCallback((message: string) => {
    setToast(message);
    window.clearTimeout(toastTimer.current);
    toastTimer.current = window.setTimeout(() => setToast(null), 2500);
  }, []);
  const clearError = useCallback(() => setError(null), []);

  const value = useMemo(() => ({ showError: setError, clearError, showToast }), [clearError, showToast]);
  return (
    <FeedbackContext.Provider value={value}>
      {error && (
        <div className="error-banner" role="alert">
          <span>{error}</span>
          <button type="button" className="link-button" aria-label="閉じる" onClick={clearError}>
            ×
          </button>
        </div>
      )}
      {children}
      {toast && (
        <div className="toast" role="status">
          {toast}
        </div>
      )}
    </FeedbackContext.Provider>
  );
}

export function useFeedback(): FeedbackContextValue {
  const value = useContext(FeedbackContext);
  if (!value) throw new Error("useFeedback は FeedbackProvider の中で使う");
  return value;
}
