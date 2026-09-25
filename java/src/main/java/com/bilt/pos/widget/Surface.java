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
 * A place on the register where a widget can draw a {@link Rendering}.
 *
 * <p>The surface is the register's side of the widget seam. It owns pixels and input; the widget
 * owns the decision of what to show. The register provides one — a native adapter from a platform
 * module, a {@link WebSurface} subclass around its browser control, or its own implementation — and
 * the widget drives it with exactly two verbs.
 *
 * <h2>Threading contract</h2>
 *
 * <p>Both methods are invoked on the owning widget's executor, one call at a time, never
 * concurrently with each other. A surface that draws on a UI thread marshals the call there and
 * returns immediately; it must not block the widget waiting for the frame. Conversely, the
 * callbacks a surface makes into the {@link ActionSink} may come from any thread — a UI thread, a
 * JavaScript bridge thread — and the sink marshals back to the widget. A surface should hold no
 * lock while calling the sink.
 *
 * <h2>Replacement and idempotence</h2>
 *
 * <p>{@link #show} replaces whatever is currently displayed, including a rendering still playing;
 * there is no stacking. {@link #clear} removes the current rendering and is idempotent: clearing an
 * empty surface is a no-op, and clearing twice is the same as clearing once. A surface that is
 * cleared must stop reporting interactions for the rendering it was showing.
 */
public interface Surface {

  /**
   * Draws {@code rendering}, replacing anything currently shown, and reports the shopper's
   * interactions with it to {@code actions}. The sink is bound to this rendering: a surface hands
   * back the rendering's own {@link Cta} instances and never a token it made up.
   */
  void show(Rendering rendering, ActionSink actions);

  /** Removes the current rendering, if any. Idempotent. */
  void clear();
}
