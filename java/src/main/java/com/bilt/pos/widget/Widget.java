/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.widget;

/**
 * A shopper-facing capability the SDK runs alongside a session and renders on a {@link Surface} the
 * register hands over: the retail-media banner first, member identity and wallet later on the same
 * mechanism. A widget is registered on the session builder ({@code widget(..)}), follows the
 * session's state as a {@link SessionObserver}, and is reached at runtime through {@code
 * ShopperSession.widget(Class)}.
 *
 * <p>Integrators use shipped widgets — each with its own builder, options, surfaces and callbacks —
 * and normally do not implement this interface. The contract is small and internal-facing so that
 * the SDK's own widgets, and a test double, can be written against it.
 *
 * <p>One widget instance belongs to one session. The session {@linkplain #attach(WidgetHost)
 * attaches} it once when the session starts and {@linkplain #detach() detaches} it once when the
 * session ends; registering the same instance on a second session is refused. Between those two
 * calls the widget receives the {@link SessionObserver} callbacks, all of them — like {@code
 * attach} and {@code detach} — on the session's operation lane, one at a time and in order. {@link
 * #pause()} and {@link #resume()} are the register's, and may be called from any thread at any time
 * while the session is open.
 */
public interface Widget extends SessionObserver {

  /**
   * Binds the widget to the session that owns it, before any other callback. {@code host} is the
   * widget's view of the session — credentials, environment, the platform client, executors, the
   * background-error sink and the session's context — and stays valid until {@link #detach()}.
   *
   * <p>Called on the session's operation lane; a local session's {@code start()} waits for it, a
   * terminal session's start acknowledgement precedes it. It must return promptly: a widget that
   * needs the network starts that work asynchronously rather than waiting for it here. A widget
   * that cannot run with what the host offers — a platform-backed widget on a session built without
   * credentials, say — throws; the session reports the exception through {@code onBackgroundError},
   * leaves the widget detached (it receives no further callbacks), and continues without it.
   */
  void attach(WidgetHost host);

  /**
   * Releases whatever {@link #attach(WidgetHost)} acquired: clears the widget's surfaces, stops its
   * background work, drops the host. Called once on the session's operation lane after {@link
   * #ended()}, whether the session ended normally, was force-ended or was closed. Nothing is called
   * afterwards.
   */
  void detach();

  /**
   * Stops the widget showing anything until {@link #resume()}: its surfaces are cleared and stay
   * clear, and it makes no requests on the shopper's behalf. The session's callbacks keep arriving
   * so the widget stays current and can pick up where the session is when resumed. For a companion
   * display shared with PIN entry, signature capture or cart review. Idempotent.
   */
  void pause();

  /** Lets a {@linkplain #pause() paused} widget show content again. Idempotent. */
  void resume();

  /** Whether {@link #pause()} is in effect. */
  boolean isPaused();
}
