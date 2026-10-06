// A Bilt terminal that answers from a script, as an HTTP server the host's Nexo client reaches
// with `tls: false, encryption: false`. The scripted responses are the ones the host's own
// ScriptedTerminalClient uses, so a settlement runs the same sequence here as in the Java tests:
// Admin (session start) → Loyalty/Rebate → Payment → Loyalty/Award → Admin (session end).
import { createServer, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';

type NexoBody = {
  SaleToPOIRequest?: {
    MessageHeader?: { MessageCategory?: string };
    LoyaltyRequest?: { LoyaltyTransaction?: { LoyaltyTransactionType?: string } };
    PaymentRequest?: { PaymentTransaction?: { AmountsReq?: { RequestedAmount?: number } } };
  };
};

/** A scripted answer: a JSON string, or a function of the request producing one. */
type Script = string | ((request: NexoBody) => string);

export const ADMIN_OK =
  '{"SaleToPOIResponse":{"MessageHeader":{"ProtocolVersion":"3.0"},' +
  '"AdminResponse":{"Response":{"Result":"Success"}}}}';

export const REBATE_OK =
  '{"SaleToPOIResponse":{"LoyaltyResponse":{' +
  '"Response":{"Result":"Success"},' +
  '"POIData":{"POITransactionID":{"TransactionID":"POI-RB-1","TimeStamp":"2026-07-20T10:00:01Z"}},' +
  '"LoyaltyResult":[{"Rebates":{"TotalRebate":10.00,"RebateLabel":"Gold Member",' +
  '"SaleItemRebate":[{"ItemID":1,"ProductCode":"SKU-1","ItemAmount":10.00,"RebateLabel":"Gold: $10 off"}]}}]}}}';

export const AWARD_OK =
  '{"SaleToPOIResponse":{"LoyaltyResponse":{' +
  '"Response":{"Result":"Success"},' +
  '"POIData":{"POITransactionID":{"TransactionID":"POI-AW-1"}},' +
  '"LoyaltyResult":[{"CurrentBalance":789,"LoyaltyAmount":{"AmountValue":89,"LoyaltyUnit":"Point"}}]}}}';

export const INPUT_CONFIRMED =
  '{"SaleToPOIResponse":{"InputResponse":{' +
  '"InputResult":{"Response":{"Result":"Success"},"Input":{"ConfirmedFlag":true}}}}}';

/** A payment approved for the amount the request asked for. */
export function paymentEcho(request: NexoBody): string {
  const amount =
    request.SaleToPOIRequest?.PaymentRequest?.PaymentTransaction?.AmountsReq?.RequestedAmount ?? 0;
  return (
    '{"SaleToPOIResponse":{"PaymentResponse":{' +
    '"Response":{"Result":"Success"},' +
    '"POIData":{"POITransactionID":{"TransactionID":"POI-PAY-1","TimeStamp":"2026-07-20T10:00:03Z"}},' +
    `"PaymentResult":{"AmountsResp":{"Currency":"USD","AuthorizedAmount":${amount.toFixed(2)}},` +
    '"PaymentAcquirerData":{"ApprovalCode":"APPR7","AcquirerTransactionID":{"TransactionID":"ACQ-1"}},' +
    '"PaymentInstrumentData":{"CardData":{"PaymentBrand":"Visa"}}}}}}'
  );
}

function keyOf(body: NexoBody): string | undefined {
  const request = body.SaleToPOIRequest;
  const category = request?.MessageHeader?.MessageCategory;
  if (!category) return undefined;
  const loyaltyType = request?.LoyaltyRequest?.LoyaltyTransaction?.LoyaltyTransactionType;
  return category === 'Loyalty' && loyaltyType ? `Loyalty/${loyaltyType}` : category;
}

export class FakeTerminal {
  readonly requests: NexoBody[] = [];
  private readonly scripts = new Map<string, Script>();
  private server: Server | undefined;
  private held:
    { key: string; reached: () => void; release: Promise<void>; go: () => void } | undefined;

  constructor() {
    this.reply('Admin', ADMIN_OK)
      .reply('Abort', ADMIN_OK)
      .reply('Input', INPUT_CONFIRMED)
      .reply('Payment', paymentEcho)
      .reply('Loyalty', AWARD_OK)
      .reply('Loyalty/Rebate', REBATE_OK)
      .reply('Loyalty/Award', AWARD_OK);
  }

  /** Scripts one message category (`Payment`) or loyalty transaction type (`Loyalty/Rebate`). */
  reply(key: string, script: Script): this {
    this.scripts.set(key, script);
    return this;
  }

  /**
   * Parks the next request with the key until `release()`; resolves once the host's request
   * arrived, so a test can act while an operation is mid-flight.
   */
  hold(key: string): Promise<void> {
    let reached!: () => void;
    let go!: () => void;
    const arrived = new Promise<void>((resolve) => {
      reached = resolve;
    });
    const release = new Promise<void>((resolve) => {
      go = resolve;
    });
    this.held = { key, reached, release, go };
    return arrived;
  }

  release(): void {
    this.held?.go();
    this.held = undefined;
  }

  requestsOf(key: string): NexoBody[] {
    return this.requests.filter((body) => keyOf(body) === key);
  }

  async start(): Promise<{ host: string; port: number }> {
    this.server = createServer((request, response) => {
      const chunks: Buffer[] = [];
      request.on('data', (chunk: Buffer) => chunks.push(chunk));
      request.on('end', () => {
        void this.answer(Buffer.concat(chunks).toString('utf8')).then(
          (json) => {
            // One connection per request: a pooled connection the server idled out would send
            // the host's Nexo client into its network-error recovery mid-test.
            response.writeHead(200, { 'Content-Type': 'application/json', Connection: 'close' });
            response.end(json);
          },
          (error: unknown) => {
            response.writeHead(500, { 'Content-Type': 'application/json' });
            response.end(JSON.stringify({ error: String(error) }));
          },
        );
      });
    });
    await new Promise<void>((resolve) => this.server!.listen(0, '127.0.0.1', resolve));
    const address = this.server.address() as AddressInfo;
    return { host: '127.0.0.1', port: address.port };
  }

  async stop(): Promise<void> {
    this.release();
    await new Promise<void>((resolve) => {
      if (!this.server) return resolve();
      this.server.closeAllConnections();
      this.server.close(() => resolve());
    });
  }

  private async answer(raw: string): Promise<string> {
    const body = JSON.parse(raw) as NexoBody;
    this.requests.push(body);
    const key = keyOf(body);
    if (key === undefined) throw new Error('request without a message category');
    const held = this.held;
    if (held && held.key === key) {
      held.reached();
      await held.release;
    }
    const script =
      this.scripts.get(key) ??
      (key.startsWith('Loyalty/') ? this.scripts.get('Loyalty') : undefined);
    if (script === undefined) throw new Error(`no scripted response for ${key}`);
    return typeof script === 'string' ? script : script(body);
  }
}
