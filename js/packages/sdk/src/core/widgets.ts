import type {
  Cta,
  PlacementConfig,
  Rendering,
  WidgetAction,
  WidgetConfig,
  WidgetState,
} from '@bilt/pos-protocol';
import { SessionError } from '../errors';
import type { Engine } from '../internal';
import type { RetailMediaWidget, WidgetOptions, WidgetType } from '../session';
import { newIdempotencyKey } from './ids';

/** The wire form of a widget option: placements become `PlacementConfig` objects. */
export function toWidgetConfig(options: WidgetOptions): WidgetConfig {
  const config: WidgetConfig = {
    type: options.type,
    placements: options.placements.map((placement): PlacementConfig =>
      typeof placement === 'string' ? { id: placement } : { ...placement },
    ),
  };
  if (options.eligiblePhases) config.eligiblePhases = [...options.eligiblePhases];
  if (options.decisionTimeout !== undefined) config.decisionTimeout = options.decisionTimeout;
  if (options.renderingTtl !== undefined) config.renderingTtl = options.renderingTtl;
  return config;
}

/**
 * The retail-media widget handle. Its state is what the host last reported: at session start
 * from `GET .../widgets`, afterwards from the responses to `pause()` and `resume()`.
 */
export class RetailMediaWidgetImpl implements RetailMediaWidget {
  readonly type = 'retail-media' as const;
  private state: WidgetState;

  constructor(
    private readonly engine: Engine,
    private readonly sessionId: string,
    initial: WidgetState,
  ) {
    this.state = initial;
  }

  get inert(): boolean {
    return this.state.inert === true;
  }

  get error(): SessionError | null {
    return this.state.error ? new SessionError(this.state.error) : null;
  }

  isPaused(): boolean {
    return this.state.paused;
  }

  async pause(): Promise<void> {
    this.state = await this.engine.widget(this.sessionId, this.type, 'pause', {
      idempotencyKey: newIdempotencyKey(),
    });
  }

  async resume(): Promise<void> {
    this.state = await this.engine.widget(this.sessionId, this.type, 'resume', {
      idempotencyKey: newIdempotencyKey(),
    });
  }

  private action(action: WidgetAction): Promise<void> {
    return this.engine.widgetAction(this.sessionId, this.type, action, {
      idempotencyKey: newIdempotencyKey(),
    });
  }

  perform(rendering: Rendering, cta: Cta): Promise<void> {
    return this.action({
      kind: 'perform',
      creativeId: rendering.creativeId,
      placement: rendering.placement,
      action: cta.action,
      token: cta.token,
    });
  }

  viewed(rendering: Rendering): Promise<void> {
    return this.action({
      kind: 'viewed',
      creativeId: rendering.creativeId,
      placement: rendering.placement,
    });
  }

  dismissed(rendering: Rendering): Promise<void> {
    return this.action({
      kind: 'dismissed',
      creativeId: rendering.creativeId,
      placement: rendering.placement,
    });
  }

  completed(rendering: Rendering): Promise<void> {
    return this.action({
      kind: 'completed',
      creativeId: rendering.creativeId,
      placement: rendering.placement,
    });
  }
}

/** Builds the handles for the widgets configured on a session, from the host's reported states. */
export function buildWidgets(
  engine: Engine,
  sessionId: string,
  configured: readonly WidgetOptions[],
  states: readonly WidgetState[],
): RetailMediaWidgetImpl[] {
  return configured.map((options) => {
    const state: WidgetState = states.find((s) => s.type === options.type) ?? {
      type: options.type,
      paused: false,
      placements: toWidgetConfig(options).placements,
    };
    return new RetailMediaWidgetImpl(engine, sessionId, state);
  });
}

export type { WidgetType };
