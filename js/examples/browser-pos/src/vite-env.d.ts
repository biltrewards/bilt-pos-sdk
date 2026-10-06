/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Where the Terminal Bridge update manifest is; unset until the update feed exists. */
  readonly VITE_BRIDGE_MANIFEST_URL?: string;
}
