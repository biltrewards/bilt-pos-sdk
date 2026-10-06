import { SessionError } from '@bilt/pos-sdk';
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';

export type ToastTone = 'info' | 'success' | 'warning';

export interface Toast {
  readonly id: number;
  readonly tone: ToastTone;
  readonly text: string;
}

export interface ToastApi {
  readonly items: readonly Toast[];
  push(tone: ToastTone, text: string): void;
  dismiss(id: number): void;
}

const TOAST_MS = 6000;

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
    (tone: ToastTone, text: string) => {
      const id = ++counter.current;
      setItems((previous) => [...previous, { id, tone, text }]);
      timers.current.set(
        id,
        setTimeout(() => dismiss(id), TOAST_MS),
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
          <button type="button" aria-label="Dismiss" onClick={() => toasts.dismiss(toast.id)}>
            &times;
          </button>
        </div>
      ))}
    </div>
  );
}

/** One line for any failure: the `SessionError` code when there is one, the message otherwise. */
export function describeError(error: unknown): string {
  if (error instanceof SessionError) return `${error.code}: ${error.message}`;
  if (error instanceof Error) return error.message;
  return String(error);
}
