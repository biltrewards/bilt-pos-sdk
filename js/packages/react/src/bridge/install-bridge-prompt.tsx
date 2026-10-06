import { BridgeOutdatedError } from '@bilt/pos-sdk/bridge';
import type { CSSProperties, ReactNode } from 'react';
import { PLATFORM_NAMES } from './platform';
import { useBridge, type BridgeState, type UseBridgeOptions } from './use-bridge';

/** The copy `InstallBridgePrompt` renders; every string can be replaced. */
export interface InstallBridgeLabels {
  readonly detectingTitle: string;
  readonly detectingBody: string;
  readonly permissionNotice: string;
  readonly continueButton: string;
  readonly missingTitle: string;
  readonly missingBody: string;
  readonly outdatedTitle: string;
  readonly outdatedBody: string;
  readonly download: string;
  readonly noDownload: string;
  readonly waiting: string;
  readonly retry: string;
  readonly releaseNotes: string;
}

const DEFAULT_LABELS: InstallBridgeLabels = {
  detectingTitle: 'Connecting to the {product}',
  detectingBody: 'Looking for the {product} on this computer.',
  permissionNotice:
    'Your browser may ask once whether this page may connect to software running on this computer. Choose Allow so the register can reach the {product}.',
  continueButton: 'Continue',
  missingTitle: 'Install the {product}',
  missingBody:
    'The {product} connects this register to the payment terminal. Install it on this computer and leave it running; the register continues on its own once it is up.',
  outdatedTitle: 'Update the {product}',
  outdatedBody:
    'The {product} on this computer is too old for this register. Install the latest version; the register continues on its own once it is up.',
  download: 'Download for {platform}',
  noDownload: 'Ask your administrator for the {product} installer.',
  waiting: 'Waiting for the {product}…',
  retry: 'Try again',
  releaseNotes: 'Release notes',
};

/** Props for `InstallBridgePrompt`. */
export interface InstallBridgePromptProps extends UseBridgeOptions {
  /**
   * The state from a `useBridge()` the page already runs, so the prompt does not probe on its
   * own. Without it the prompt calls `useBridge` with the options it was given.
   */
  readonly bridge?: BridgeState;

  /** The installer link used when no manifest is configured or it lists nothing for this platform. */
  readonly downloadUrl?: string;

  /** What to call the bridge; default "Bilt Terminal Bridge". A rebranded bridge passes its own name. */
  readonly productName?: string;

  /** Overrides for the copy; `{product}` and `{platform}` are substituted. */
  readonly labels?: Partial<InstallBridgeLabels>;

  /** The prefix of every CSS class the prompt emits; default `"bilt-bridge"`. */
  readonly classNamePrefix?: string;
  readonly className?: string;
  readonly style?: CSSProperties;

  /** Rendered in place of the prompt once the bridge is `ready`. */
  readonly children?: ReactNode;
}

function fill(template: string, values: Record<string, string>): string {
  return template.replace(/\{(\w+)\}/g, (match, key: string) => values[key] ?? match);
}

/**
 * The install and update prompt from the design's "bridge detection and install prompt" flow.
 * While `detecting` it explains the one-time loopback permission Chrome asks for before the
 * first probe (and, with `autoDetect: false`, waits for the cashier to continue before probing);
 * when the bridge is `missing` it links the installer for this platform — from the update
 * manifest when one is configured, otherwise `downloadUrl` — and keeps probing every two
 * seconds; when it is `outdated` it shows the update copy with the protocol versions involved;
 * once `ready` it renders its children.
 *
 * Headless: a `div` with class names under `classNamePrefix` (`bilt-bridge`, `bilt-bridge-title`,
 * `bilt-bridge-body`, `bilt-bridge-notice`, `bilt-bridge-download`, `bilt-bridge-retry`) and a
 * `data-status` attribute, no styles.
 *
 * ```tsx
 * <InstallBridgePrompt manifestUrl="https://downloads.bilt.com/bridge/manifest.json">
 *   <Lane />
 * </InstallBridgePrompt>
 * ```
 */
export function InstallBridgePrompt(props: InstallBridgePromptProps): ReactNode {
  const {
    bridge: given,
    downloadUrl,
    productName = 'Bilt Terminal Bridge',
    labels: overrides,
    classNamePrefix: prefix = 'bilt-bridge',
    className,
    style,
    children,
    ...bridgeOptions
  } = props;
  // Hooks run unconditionally; the own detector is only started when no state was given.
  const own = useBridge({
    ...bridgeOptions,
    autoDetect: given ? false : (bridgeOptions.autoDetect ?? true),
  });
  const bridge = given ?? own;
  const labels = { ...DEFAULT_LABELS, ...overrides };
  const values = { product: productName, platform: PLATFORM_NAMES[bridge.platform] };
  const text = (key: keyof InstallBridgeLabels) => fill(labels[key], values);
  const href = bridge.download?.url ?? downloadUrl;
  const classes = [prefix, className].filter(Boolean).join(' ');

  if (bridge.status === 'ready') return children ?? null;

  const notice = <p className={`${prefix}-notice`}>{text('permissionNotice')}</p>;
  const retry = (
    <button type="button" className={`${prefix}-retry`} onClick={bridge.retry}>
      {text('retry')}
    </button>
  );
  const download = href ? (
    <a className={`${prefix}-download`} href={href} target="_blank" rel="noopener noreferrer">
      {text('download')}
    </a>
  ) : (
    <p className={`${prefix}-no-download`}>{text('noDownload')}</p>
  );
  const releaseNotes = bridge.manifest?.releaseNotesUrl ? (
    <a
      className={`${prefix}-release-notes`}
      href={bridge.manifest.releaseNotesUrl}
      target="_blank"
      rel="noopener noreferrer"
    >
      {text('releaseNotes')}
    </a>
  ) : null;

  if (bridge.status === 'detecting') {
    // Before the first probe the cashier may need to trigger it, after reading the notice.
    return (
      <div className={classes} style={style} data-status="detecting" role="status">
        <h2 className={`${prefix}-title`}>{text('detectingTitle')}</h2>
        <p className={`${prefix}-body`}>{text('detectingBody')}</p>
        {notice}
        {bridge.attempts === 0 ? (
          <button type="button" className={`${prefix}-continue`} onClick={bridge.retry}>
            {text('continueButton')}
          </button>
        ) : null}
      </div>
    );
  }

  const outdated = bridge.status === 'outdated';
  const versions =
    outdated && bridge.error instanceof BridgeOutdatedError
      ? `${bridge.error.available.join(', ') || 'none'} → ${bridge.error.required}`
      : null;
  return (
    <div className={classes} style={style} data-status={bridge.status} role="alert">
      <h2 className={`${prefix}-title`}>{text(outdated ? 'outdatedTitle' : 'missingTitle')}</h2>
      <p className={`${prefix}-body`}>{text(outdated ? 'outdatedBody' : 'missingBody')}</p>
      {versions ? (
        <p
          className={`${prefix}-versions`}
          data-required={
            bridge.error instanceof BridgeOutdatedError ? bridge.error.required : undefined
          }
        >
          {versions}
        </p>
      ) : null}
      {download}
      {releaseNotes}
      {notice}
      <p className={`${prefix}-waiting`} aria-live="polite">
        {text('waiting')}
      </p>
      {retry}
    </div>
  );
}
