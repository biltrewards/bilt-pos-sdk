/**
 * `@bilt/pos-react/bridge`: the Terminal Bridge pieces of the React bindings. Detecting the
 * bridge on loopback, prompting for its installation or update, and gating the register on it.
 * Kept out of `@bilt/pos-react` on purpose: nothing in the core entry point knows the bridge
 * exists, so the same register runs over the Cloud Session Service with these components left
 * out.
 *
 * @packageDocumentation
 */
export {
  bridgeDetector,
  DEFAULT_BRIDGE_PORT,
  DEFAULT_DETECT_TIMEOUT_MS,
  type BridgeDetection,
  type BridgeDetector,
  type BridgeDetectorOptions,
} from './bridge/detector';
export { detectPlatform, PLATFORM_NAMES, type BridgePlatform } from './bridge/platform';
export {
  downloadFor,
  fetchBridgeManifest,
  parseBridgeManifest,
  type BridgeDownload,
  type BridgeManifest,
} from './bridge/manifest';
export {
  useBridge,
  type BridgeState,
  type BridgeStatus,
  type UseBridgeOptions,
} from './bridge/use-bridge';
export {
  InstallBridgePrompt,
  type InstallBridgeLabels,
  type InstallBridgePromptProps,
} from './bridge/install-bridge-prompt';
export { BridgeGate, type BridgeGateProps } from './bridge/bridge-gate';
