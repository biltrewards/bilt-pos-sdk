import type { SessionError as SessionErrorData } from '@bilt/pos-protocol';
import { EngineUnavailableError, SessionError } from '../errors';
import { isLoopback, type ResolvedBridgeOptions } from './options';

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export interface HttpRequest {
  readonly method: HttpMethod;
  readonly path: string;
  readonly query?: Readonly<Record<string, string | number | undefined>>;
  readonly body?: unknown;
  readonly idempotencyKey?: string;
  readonly signal?: AbortSignal;
}

/** The parsed response: `status` and the JSON body (`undefined` for 204 and empty bodies). */
export interface HttpResponse<T> {
  readonly status: number;
  readonly body: T;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function isNetworkFailure(error: unknown): boolean {
  // `fetch` rejects with a TypeError when the connection fails; an abort is the caller's own.
  return error instanceof TypeError;
}

/**
 * The bridge's HTTP side: JSON over `fetch` to one base URL, with the `Idempotency-Key` and
 * `Authorization` headers, Chrome's `targetAddressSpace: "loopback"` hint for pages served from
 * a public origin, error bodies turned into `SessionError`s, and a retry of requests that never
 * reached the bridge. A retried mutation reuses its idempotency key, so the bridge answers the
 * replay with the original result.
 */
export class BridgeHttp {
  private readonly requestInit: Record<string, unknown>;

  constructor(
    readonly baseUrl: string,
    private readonly options: ResolvedBridgeOptions,
  ) {
    const host = new URL(baseUrl).hostname;
    this.requestInit = isLoopback(host) ? { targetAddressSpace: 'loopback' } : {};
  }

  url(path: string, query?: HttpRequest['query']): string {
    const url = new URL(path, this.baseUrl);
    for (const [key, value] of Object.entries(query ?? {})) {
      if (value !== undefined) url.searchParams.set(key, String(value));
    }
    return url.toString();
  }

  headers(extra: Record<string, string> = {}): Record<string, string> {
    const headers: Record<string, string> = { Accept: 'application/json', ...extra };
    if (this.options.bearerToken !== undefined) {
      headers.Authorization = `Bearer ${this.options.bearerToken}`;
    }
    return headers;
  }

  /** Sends one request; resolves with the parsed body, rejects with a `SessionError` or `EngineUnavailableError`. */
  async send<T>(request: HttpRequest): Promise<T> {
    return (await this.exchange<T>(request)).body;
  }

  async exchange<T>(request: HttpRequest): Promise<HttpResponse<T>> {
    const headers = this.headers();
    if (request.body !== undefined) headers['Content-Type'] = 'application/json';
    if (request.idempotencyKey !== undefined) headers['Idempotency-Key'] = request.idempotencyKey;
    const url = this.url(request.path, request.query);
    const init: RequestInit = {
      ...this.requestInit,
      method: request.method,
      headers,
      ...(request.body !== undefined ? { body: JSON.stringify(request.body) } : {}),
      ...(request.signal ? { signal: request.signal } : {}),
    };

    let attempt = 0;
    for (;;) {
      let response: Response;
      try {
        response = await this.options.fetch(url, init);
      } catch (error) {
        if (isNetworkFailure(error) && attempt < this.options.retries && !request.signal?.aborted) {
          attempt++;
          await sleep(100 * attempt);
          continue;
        }
        throw new EngineUnavailableError(
          `the Terminal Bridge at ${this.baseUrl} did not answer ${request.method} ${request.path}: ${
            error instanceof Error ? error.message : String(error)
          }`,
          { cause: error },
        );
      }
      const text = await response.text();
      const body = text.length === 0 ? undefined : (parseJson(text) as unknown);
      if (response.ok) {
        return { status: response.status, body: body as T };
      }
      throw toSessionError(response.status, body, `${request.method} ${request.path}`);
    }
  }
}

function parseJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

function isErrorBody(body: unknown): body is SessionErrorData {
  return (
    typeof body === 'object' &&
    body !== null &&
    typeof (body as { code?: unknown }).code === 'string' &&
    typeof (body as { message?: unknown }).message === 'string'
  );
}

/** The error body of a failed request as a `SessionError`; the HTTP status rides in `details`. */
export function toSessionError(status: number, body: unknown, what: string): SessionError {
  if (isErrorBody(body)) {
    return new SessionError({ ...body, details: { ...(body.details ?? {}), httpStatus: status } });
  }
  const code =
    status === 401 || status === 403 ? 'UNAUTHORIZED' : status === 404 ? 'NOT_FOUND' : 'UNKNOWN';
  return new SessionError({
    code,
    message: `${what} failed with HTTP ${status}`,
    details: { httpStatus: status, ...(body === undefined ? {} : { body }) },
  });
}
