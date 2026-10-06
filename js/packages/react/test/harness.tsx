// The React tests run against the SDK package's in-memory doubles, the same ones its own
// design-sketch test uses, so the hooks are proven against the public interface alone.
import type { Rendering } from '@bilt/pos-protocol';
import type { TerminalSessionOptions } from '@bilt/pos-sdk';
import { render, type RenderOptions, type RenderResult } from '@testing-library/react';
import type { ReactElement, ReactNode } from 'react';
import { BiltPosProvider } from '../src/index';
import { MockBiltPos, MockShopperSession, MockTerminalSession } from '../../sdk/test/mock-pos';
import * as fx from '../../sdk/test/fixtures';

export { MockBiltPos, MockShopperSession, MockTerminalSession, fx };

export const LANE: TerminalSessionOptions = {
  saleId: 'LANE-3',
  poiId: 'VictaLane-275839164',
  currency: 'USD',
  storeLocation: 'STR-0142',
  widgets: [{ type: 'retail-media', placements: ['lane-banner', 'pin-pad'] }],
};

/** Renders `ui` under a provider holding `pos`. */
export function renderWithPos(
  pos: MockBiltPos,
  ui: ReactElement,
  options?: Omit<RenderOptions, 'wrapper'>,
): RenderResult {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <BiltPosProvider pos={pos}>{children}</BiltPosProvider>
  );
  return render(ui, { ...options, wrapper });
}

/** A terminal session started directly on the double, outside any hook. */
export function startLane(pos = new MockBiltPos()): Promise<MockTerminalSession> {
  return pos.startTerminalSession(LANE);
}

/** Pushes a `widget.rendering` for `placement` onto the session. */
export function showRendering(
  session: MockShopperSession,
  placement: string,
  overrides: Partial<Rendering> = {},
): Rendering {
  const rendering: Rendering = { ...fx.rendering(placement), ...overrides };
  session.emitter.emit('widget.rendering', { widget: 'retail-media', placement, rendering });
  return rendering;
}

/** Pushes a `widget.clear` for `placement` onto the session. */
export function clearPlacement(session: MockShopperSession, placement: string): void {
  session.emitter.emit('widget.clear', { widget: 'retail-media', placement });
}

/** Lets pending promise callbacks run. */
export function flush(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0));
}
