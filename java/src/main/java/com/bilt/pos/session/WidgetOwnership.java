/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session;

import com.bilt.pos.widget.Widget;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Which widget instances are bound to a session right now. A widget holds one session's host and
 * state, so a second session that tried to share the instance would feed it both shoppers' events,
 * and ending either session would detach it from the other.
 */
final class WidgetOwnership {

  private static final Set<Widget> OWNED = Collections.newSetFromMap(new IdentityHashMap<>());

  private WidgetOwnership() {}

  /** Claims {@code widget} for a session; false when another session already holds it. */
  static boolean claim(Widget widget) {
    synchronized (OWNED) {
      return OWNED.add(widget);
    }
  }

  static void release(Widget widget) {
    synchronized (OWNED) {
      OWNED.remove(widget);
    }
  }
}
