import { useEffect, useState, type ReactNode } from 'react';

function secondsLeft(deadline: Date): number {
  return Math.max(0, Math.ceil((deadline.getTime() - Date.now()) / 1000));
}

/** Seconds until a step's deadline, ticking down; the host applies the default when it reaches zero. */
export function Countdown({ deadline }: { readonly deadline: Date }): ReactNode {
  const [left, setLeft] = useState(() => secondsLeft(deadline));
  useEffect(() => {
    setLeft(secondsLeft(deadline));
    const timer = setInterval(() => setLeft(secondsLeft(deadline)), 500);
    return () => clearInterval(timer);
  }, [deadline]);
  return (
    <span className="countdown" data-testid="countdown">
      {left}s
    </span>
  );
}
