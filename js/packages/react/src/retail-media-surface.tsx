import type { Cta, Rendering } from '@bilt/pos-protocol';
import type { RetailMediaWidget, ShopperSession } from '@bilt/pos-sdk';
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type ReactNode,
} from 'react';
import { useLatest } from './internal/use-latest';

/** Props for `RetailMediaSurface`. */
export interface RetailMediaSurfaceProps {
  /** The session whose retail-media widget feeds this surface; the surface stays empty while `null`. */
  readonly session: ShopperSession | null | undefined;

  /** The placement id this surface draws, as configured on the widget, e.g. `"lane-banner"`. */
  readonly placement: string;

  /**
   * How long a rendering must be on screen before `viewed` is reported; default 1000 ms. On
   * screen means intersecting the viewport by `viewabilityThreshold` when `IntersectionObserver`
   * exists, and simply mounted where it does not.
   */
  readonly viewabilityMs?: number;

  /** The intersection ratio that counts as on screen; default 0.5. */
  readonly viewabilityThreshold?: number;

  /** Renders a close control that reports `dismissed` and clears the surface; default `false`. */
  readonly dismissible?: boolean;

  /** The prefix of every CSS class the surface emits; default `"bilt-rm"`. */
  readonly classNamePrefix?: string;
  readonly className?: string;
  readonly style?: CSSProperties;

  /** Accessible label of the close control; default `"Dismiss"`. */
  readonly dismissLabel?: string;

  /** Shown while no rendering is on the surface. */
  readonly placeholder?: ReactNode;

  /** The rendering that just arrived, or `null` when the placement was cleared. */
  readonly onRendering?: (rendering: Rendering | null) => void;

  /** A failed action post; a rejected tap arrives as the session's `background.error` instead. */
  readonly onError?: (error: unknown) => void;
}

function hasIntersectionObserver(): boolean {
  return typeof IntersectionObserver === 'function';
}

function renderingKey(rendering: Rendering): string {
  return `${rendering.placement}:${rendering.creativeId}:${rendering.cta?.token ?? ''}`;
}

/**
 * Draws one retail-media placement in the DOM, the browser half of the "widgets over the wire"
 * split: the host decides what to show and validates every tap, this component renders and
 * reports. It subscribes to the session's `widget.rendering` and `widget.clear` events for its
 * placement, draws the creative — an `<img>`, a muted autoplaying `<video>` with its poster, or
 * an HTML creative in an `<iframe sandbox="allow-scripts">` with no access to the page or the
 * bridge — with the headline, body and up to two call-to-action buttons, and posts `perform`,
 * `viewed`, `dismissed` and `completed` through `session.widget('retail-media')`. `viewed` is
 * reported once per rendering after it has been visible for `viewabilityMs`.
 *
 * Headless by design: the output is a container `div` with class names under `classNamePrefix`
 * (`bilt-rm`, `bilt-rm-media`, `bilt-rm-headline`, `bilt-rm-body`, `bilt-rm-actions`,
 * `bilt-rm-cta`, `bilt-rm-cta-primary`, `bilt-rm-cta-secondary`, `bilt-rm-dismiss`) and
 * `data-placement` / `data-state` / `data-media` attributes, and the only inline styles make
 * the media fill the container (`--bilt-rm-media-fit`, `--bilt-rm-media-aspect` tune them).
 * The session must have a retail-media widget configured; rendering a surface on one without
 * throws, as `session.widget()` does.
 *
 * ```tsx
 * <RetailMediaSurface session={session} placement="lane-banner" />
 * ```
 */
export function RetailMediaSurface(props: RetailMediaSurfaceProps): ReactNode {
  const {
    session,
    placement,
    viewabilityMs = 1000,
    viewabilityThreshold = 0.5,
    dismissible = false,
    classNamePrefix: prefix = 'bilt-rm',
    className,
    style,
    dismissLabel = 'Dismiss',
    placeholder = null,
  } = props;
  const onRendering = useLatest(props.onRendering);
  const onError = useLatest(props.onError);
  const current = session ?? null;
  const widget: RetailMediaWidget | null = useMemo(
    () => (current ? current.widget('retail-media') : null),
    [current],
  );

  const [rendering, setRendering] = useState<Rendering | null>(null);
  const container = useRef<HTMLDivElement | null>(null);
  const viewedKeys = useRef(new Set<string>());

  useEffect(() => {
    setRendering(null);
    if (!current) return;
    const unsubscribes = [
      current.on('widget.rendering', (payload) => {
        if (payload.placement !== placement) return;
        setRendering(payload.rendering);
        onRendering.current?.(payload.rendering);
      }),
      current.on('widget.clear', (payload) => {
        if (payload.placement !== placement) return;
        setRendering(null);
        onRendering.current?.(null);
      }),
      current.on('session.ended', () => setRendering(null)),
    ];
    return () => {
      for (const unsubscribe of unsubscribes) unsubscribe();
    };
  }, [current, placement, onRendering]);

  const post = useCallback(
    (action: Promise<void>) => {
      action.catch((error: unknown) => onError.current?.(error));
    },
    [onError],
  );

  useEffect(() => {
    if (!rendering || !widget) return;
    const key = renderingKey(rendering);
    if (viewedKeys.current.has(key)) return;
    let timer: ReturnType<typeof setTimeout> | null = null;
    const report = () => {
      timer = null;
      if (viewedKeys.current.has(key)) return;
      viewedKeys.current.add(key);
      post(widget.viewed(rendering));
    };
    const arm = () => {
      if (timer === null) timer = setTimeout(report, viewabilityMs);
    };
    const disarm = () => {
      if (timer !== null) clearTimeout(timer);
      timer = null;
    };
    const element = container.current;
    if (!element || !hasIntersectionObserver()) {
      arm();
      return disarm;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries.some(
          (entry) => entry.isIntersecting && entry.intersectionRatio >= viewabilityThreshold,
        );
        if (visible) arm();
        else disarm();
      },
      { threshold: viewabilityThreshold },
    );
    observer.observe(element);
    return () => {
      disarm();
      observer.disconnect();
    };
  }, [rendering, widget, viewabilityMs, viewabilityThreshold, post]);

  const perform = useCallback(
    (cta: Cta) => {
      if (rendering && widget) post(widget.perform(rendering, cta));
    },
    [rendering, widget, post],
  );
  const dismiss = useCallback(() => {
    if (rendering && widget) {
      post(widget.dismissed(rendering));
      setRendering(null);
      onRendering.current?.(null);
    }
  }, [rendering, widget, post, onRendering]);
  const completed = useCallback(() => {
    if (rendering && widget) post(widget.completed(rendering));
  }, [rendering, widget, post]);

  const classes = [prefix, className].filter(Boolean).join(' ');
  const mediaStyle: CSSProperties = {
    display: 'block',
    width: '100%',
    objectFit: 'var(--bilt-rm-media-fit, cover)' as CSSProperties['objectFit'],
    aspectRatio: 'var(--bilt-rm-media-aspect, auto)',
    border: 0,
  };

  if (!rendering) {
    return (
      <div
        ref={container}
        className={classes}
        style={style}
        data-placement={placement}
        data-state="empty"
      >
        {placeholder}
      </div>
    );
  }

  const { media } = rendering;
  let creative: ReactNode;
  switch (media.type) {
    case 'IMAGE':
      creative = (
        <img
          className={`${prefix}-image`}
          src={media.url}
          alt={rendering.headline}
          style={mediaStyle}
        />
      );
      break;
    case 'VIDEO':
      creative = (
        <video
          className={`${prefix}-video`}
          src={media.url}
          poster={media.poster}
          muted
          autoPlay
          playsInline
          onEnded={completed}
          style={mediaStyle}
        />
      );
      break;
    case 'HTML':
      creative = (
        <iframe
          className={`${prefix}-html`}
          src={media.url}
          title={rendering.headline}
          sandbox="allow-scripts"
          referrerPolicy="no-referrer"
          loading="lazy"
          style={mediaStyle}
        />
      );
      break;
  }

  return (
    <div
      ref={container}
      className={classes}
      style={style}
      data-placement={placement}
      data-state="rendering"
      data-media={media.type}
      data-creative={rendering.creativeId}
    >
      <div className={`${prefix}-media`}>{creative}</div>
      <div className={`${prefix}-copy`}>
        <div className={`${prefix}-headline`}>{rendering.headline}</div>
        {rendering.body ? <div className={`${prefix}-body`}>{rendering.body}</div> : null}
      </div>
      {rendering.cta || rendering.secondary ? (
        <div className={`${prefix}-actions`}>
          {rendering.cta ? (
            <button
              type="button"
              className={`${prefix}-cta ${prefix}-cta-primary`}
              data-action={rendering.cta.action}
              onClick={() => perform(rendering.cta!)}
            >
              {rendering.cta.label}
            </button>
          ) : null}
          {rendering.secondary ? (
            <button
              type="button"
              className={`${prefix}-cta ${prefix}-cta-secondary`}
              data-action={rendering.secondary.action}
              onClick={() => perform(rendering.secondary!)}
            >
              {rendering.secondary.label}
            </button>
          ) : null}
        </div>
      ) : null}
      {dismissible ? (
        <button
          type="button"
          className={`${prefix}-dismiss`}
          aria-label={dismissLabel}
          onClick={dismiss}
        >
          &times;
        </button>
      ) : null}
    </div>
  );
}
