// A TCP proxy in front of the host, so a test can cut every connection (the WebSocket among
// them) and watch the SDK reconnect. The first line of each upstream request is kept, which is
// enough to see the `since=` cursor a reconnecting event stream sends.
import { createConnection, createServer, type Server, type Socket } from 'node:net';

export class TcpProxy {
  readonly requestLines: string[] = [];
  private readonly sockets = new Set<Socket>();
  private server: Server | undefined;
  private port = 0;

  constructor(
    private readonly upstreamHost: string,
    private readonly upstreamPort: number,
  ) {}

  async start(): Promise<number> {
    this.server = createServer((client) => {
      const upstream = createConnection({ host: this.upstreamHost, port: this.upstreamPort });
      this.sockets.add(client).add(upstream);
      let first = true;
      client.on('data', (chunk: Buffer) => {
        if (first) {
          first = false;
          const text = chunk.toString('latin1');
          this.requestLines.push(text.slice(0, text.indexOf('\r\n')));
        }
      });
      client.pipe(upstream);
      upstream.pipe(client);
      const drop = () => {
        this.sockets.delete(client);
        this.sockets.delete(upstream);
        client.destroy();
        upstream.destroy();
      };
      client.on('close', drop);
      client.on('error', drop);
      upstream.on('close', drop);
      upstream.on('error', drop);
    });
    await new Promise<void>((resolve) => this.server!.listen(0, '127.0.0.1', resolve));
    const address = this.server.address();
    this.port = typeof address === 'object' && address ? address.port : 0;
    return this.port;
  }

  /** Destroys every live connection; the SDK sees its WebSocket and HTTP sockets close. */
  dropAll(): number {
    const count = this.sockets.size;
    for (const socket of this.sockets) socket.destroy();
    this.sockets.clear();
    return count;
  }

  async stop(): Promise<void> {
    this.dropAll();
    await new Promise<void>((resolve) =>
      this.server ? this.server.close(() => resolve()) : resolve(),
    );
  }
}
