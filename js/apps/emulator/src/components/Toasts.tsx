import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';

export type ToastTone = 'info' | 'success' | 'warning';

export interface ToastAction {
  readonly label: string;
  readonly run: () => void;
}

export interface Toast {
  readonly id: number;
  readonly tone: ToastTone;
  readonly text: string;
  readonly action?: ToastAction;
}

export interface ToastApi {
  readonly items: readonly Toast[];
  push(tone: ToastTone, text: string, action?: ToastAction): void;
  dismiss(id: number): void;
}

const TOAST_MS = 6000;
const ACTION_TOAST_MS = 20000;

/** A short queue of notices that dismiss themselves; offers and background errors land here. */
export function useToasts(): ToastApi {
  const [items, setItems] = useState<readonly Toast[]>([]);
  const counter = useRef(0);
  const timers = useRef(new Map<number, ReturnType<typeof setTimeout>>());
  const dismiss = useCallback((id: number) => {
    clearTimeout(timers.current.get(id));
    timers.current.delete(id);
    setItems((previous) => previous.filter((toast) => toast.id !== id));
  }, []);
  const push = useCallback(
    (tone: ToastTone, text: string, action?: ToastAction) => {
      const id = ++counter.current;
      setItems((previous) => [...previous, { id, tone, text, ...(action ? { action } : {}) }]);
      timers.current.set(
        id,
        setTimeout(() => dismiss(id), action ? ACTION_TOAST_MS : TOAST_MS),
      );
    },
    [dismiss],
  );
  useEffect(() => {
    const pending = timers.current;
    return () => {
      pending.forEach(clearTimeout);
      pending.clear();
    };
  }, []);
  return useMemo(() => ({ items, push, dismiss }), [items, push, dismiss]);
}

export function Toasts({ toasts }: { readonly toasts: ToastApi }): ReactNode {
  if (toasts.items.length === 0) return null;
  return (
    <div className="toasts" role="status" aria-live="polite">
      {toasts.items.map((toast) => (
        <div key={toast.id} className={`toast toast-${toast.tone}`}>
          <span>{toast.text}</span>
          {toast.action ? (
            <button
              type="button"
              className="toast-action"
              onClick={() => {
                toast.action?.run();
                toasts.dismiss(toast.id);
              }}
            >
              {toast.action.label}
            </button>
          ) : null}
          <button type="button" aria-label="Dismiss" onClick={() => toasts.dismiss(toast.id)}>
            &times;
          </button>
        </div>
      ))}
    </div>
  );
}
