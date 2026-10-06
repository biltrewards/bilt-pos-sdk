import createClient, { type Client, type ClientOptions } from 'openapi-fetch';
import type { paths } from './generated/openapi';

/** A typed `fetch` client over the Session Protocol paths; one per host. */
export type ProtocolClient = Client<paths>;

/**
 * Creates a typed client for a host, e.g. `createProtocolClient({ baseUrl: 'http://127.0.0.1:48333' })`.
 * Requests under `/v1/sessions` need an `Idempotency-Key` header; the SDK layer supplies it,
 * a direct user of this client passes it per call.
 */
export function createProtocolClient(options: ClientOptions): ProtocolClient {
  return createClient<paths>(options);
}
