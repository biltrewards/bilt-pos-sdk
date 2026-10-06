import type { ReactNode } from 'react';
import { InstallBridgePrompt, type InstallBridgePromptProps } from './install-bridge-prompt';
import { useBridge, type BridgeState } from './use-bridge';

/** Props for `BridgeGate`: the prompt's, plus a render function for the ready state. */
export interface BridgeGateProps extends Omit<InstallBridgePromptProps, 'bridge' | 'children'> {
  /** What to render once the bridge is `ready`; a function receives the bridge state. */
  readonly children?: ReactNode | ((bridge: BridgeState) => ReactNode);

  /** Replaces `InstallBridgePrompt` for the not-ready states. */
  readonly fallback?: (bridge: BridgeState) => ReactNode;
}

/**
 * Runs `useBridge` and renders `InstallBridgePrompt` until the bridge is `ready`, then its
 * children. The register's top-level gate:
 *
 * ```tsx
 * <BridgeGate manifestUrl={MANIFEST_URL}>
 *   <BiltPosProvider connect={() => BiltPos.connect(localBridge())}>
 *     <Lane />
 *   </BiltPosProvider>
 * </BridgeGate>
 * ```
 */
export function BridgeGate(props: BridgeGateProps): ReactNode {
  const { children, fallback, ...promptProps } = props;
  const bridge = useBridge(promptProps);
  if (bridge.status === 'ready') {
    return typeof children === 'function' ? children(bridge) : (children ?? null);
  }
  if (fallback) return fallback(bridge);
  return <InstallBridgePrompt {...promptProps} bridge={bridge} />;
}
