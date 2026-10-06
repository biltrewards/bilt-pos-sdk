/**
 * Where the SDK sends failures it must not propagate: a throwing event handler, an event stream
 * that dropped, a `[Symbol.asyncDispose]` whose `end()` was refused. The default writes to
 * `console.error`; the core never throws from these paths.
 */
export type Reporter = (context: string, error: unknown) => void;

export const consoleReporter: Reporter = (context, error) => {
  console.error(`[bilt-pos] ${context}:`, error);
};
