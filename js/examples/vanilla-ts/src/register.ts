import type {
  Basket,
  BiltPos,
  Member,
  ShopperSession,
  TerminalShopperSession,
} from '@bilt/pos-sdk';
import type { BridgeDetection } from '@bilt/pos-sdk/bridge';
import { CATALOG, recomputeTotal } from './catalog';
import { loadSettings, saveSettings, type Settings } from './settings';

/** What the page needs from the outside world; `main.ts` passes the real bridge, tests a double. */
export interface RegisterDeps {
  readonly detect: () => Promise<BridgeDetection>;
  readonly connect: () => Promise<BiltPos>;
}

type Child = Node | string;

function el<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  props: Partial<HTMLElementTagNameMap[K]> & { dataset?: Record<string, string> } = {},
  ...children: Child[]
): HTMLElementTagNameMap[K] {
  const { dataset, ...rest } = props;
  const node = Object.assign(document.createElement(tag), rest);
  Object.assign(node.dataset, dataset);
  node.append(...children);
  return node;
}

function message(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

/** Mounts the register into `root`: bridge detection, then settings, then a session. */
export async function mountRegister(root: HTMLElement, deps: RegisterDeps): Promise<void> {
  const show = (...children: Child[]) => root.replaceChildren(...children);

  async function detect(): Promise<void> {
    show(el('p', { dataset: { status: 'probing' } }, 'Looking for the Terminal Bridge…'));
    const found = await deps.detect();
    if (found.status !== 'ready') {
      const text =
        found.status === 'missing'
          ? 'Install the Bilt Terminal Bridge on this machine, then retry.'
          : `The bridge at ${found.baseUrl} is outdated; update it, then retry.`;
      showRetry(text, { status: found.status });
      return;
    }
    try {
      showSettings(await deps.connect());
    } catch (error) {
      showRetry(`Could not connect: ${message(error)}`, { status: 'connect-failed' });
    }
  }

  function showRetry(text: string, dataset: Record<string, string>): void {
    const retry = el('button', { type: 'button', textContent: 'Retry' });
    retry.onclick = () => void detect();
    show(el('p', { role: 'alert', dataset }, text), retry);
  }

  function showSettings(pos: BiltPos): void {
    const saved = loadSettings();
    const field = (name: keyof Settings, label: string) =>
      el('label', {}, `${label} `, el('input', { name, value: saved[name] }));
    const mode = el(
      'select',
      { name: 'mode' },
      el('option', { value: 'terminal', textContent: 'Terminal session' }),
      el('option', { value: 'local', textContent: 'Local session (no terminal)' }),
    );
    mode.value = saved.mode;
    const error = el('p', { className: 'error' });
    const start = el('button', { type: 'submit', textContent: 'Start session' });
    const form = el(
      'form',
      {},
      el(
        'fieldset',
        {},
        el('legend', {}, `Lane settings · engine ${pos.capabilities.name}`),
        mode,
        field('poiId', 'POI id (optional, passed through)'),
        field('saleId', 'Lane (saleId)'),
        field('currency', 'Currency'),
        field('storeLocation', 'Store location'),
      ),
      start,
      error,
    );
    form.onsubmit = async (event) => {
      event.preventDefault();
      // A second click while the request is in flight would open a second session nothing ends.
      if (start.disabled) return;
      start.disabled = true;
      error.textContent = '';
      const data = new FormData(form);
      const settings: Settings = {
        mode: data.get('mode') === 'local' ? 'local' : 'terminal',
        poiId: String(data.get('poiId')).trim(),
        saleId: String(data.get('saleId')),
        currency: String(data.get('currency')).toUpperCase(),
        storeLocation: String(data.get('storeLocation')),
      };
      saveSettings(settings);
      try {
        const { mode: kind, poiId, ...lane } = settings;
        const session =
          kind === 'terminal'
            ? await pos.startTerminalSession(poiId ? { ...lane, poiId } : lane)
            : await pos.startShopperSession(lane);
        showSession(pos, session, kind === 'terminal' ? (session as TerminalShopperSession) : null);
      } catch (err) {
        error.textContent = `Could not start the session: ${message(err)}`;
        start.disabled = false;
      }
    };
    show(form);
  }

  function showSession(
    pos: BiltPos,
    session: ShopperSession,
    terminal: TerminalShopperSession | null,
  ): void {
    const money = (value: string) => `${value} ${session.currency}`;
    const lines = el('tbody');
    const total = el('p', { dataset: { testid: 'grand-total' } });
    const memberLine = el('p', {}, 'Guest');
    const status = el('p', { role: 'status' });
    const result = el('pre');

    function renderBasket(basket: Basket): void {
      lines.replaceChildren(
        ...basket.items.map((line) =>
          el(
            'tr',
            {},
            el('td', {}, line.description ?? line.sku),
            el('td', {}, `× ${line.quantity}`),
            el('td', {}, money(line.adjustedTotal)),
          ),
        ),
      );
      total.textContent = `Total ${money(basket.grandTotal)}`;
    }

    function renderMember(member: Member | null): void {
      memberLine.textContent = !member
        ? 'Guest'
        : member.resolved
          ? `Member ${member.memberId} · ${member.pointBalance} points`
          : 'Member lookup pending…';
    }

    async function run(label: string, action: () => Promise<unknown>): Promise<void> {
      status.textContent = `${label}…`;
      try {
        await action();
        status.textContent = '';
      } catch (error) {
        status.textContent = `${label} failed: ${message(error)}`;
      }
    }

    const unsubscribe = [
      session.on('basket.changed', (change) => renderBasket(change.current)),
      session.on('member.changed', ({ member }) => renderMember(member)),
      session.on('session.ended', () => {
        unsubscribe.forEach((off) => off());
        showSettings(pos);
      }),
    ];

    const catalog = CATALOG.map((item) => {
      const button = el('button', {
        type: 'button',
        textContent: `${item.description} ${money(item.unitPrice)}`,
      });
      button.onclick = () => void run('Adding', () => session.basket.addItem(item));
      return button;
    });

    const phone = el('input', { type: 'tel', placeholder: '+12015550123', name: 'phone' });
    const signIn = el('button', { type: 'button', textContent: 'Sign in' });
    signIn.onclick = () =>
      void run('Signing in', () =>
        session.member.set({
          resolver: { type: 'PHONE', value: phone.value, keyedByCashier: true },
        }),
      );

    const pay = el('button', { type: 'button', textContent: 'Pay', disabled: !terminal });
    pay.onclick = () =>
      void run('Paying', async () => {
        if (!terminal) return;
        const paid = await terminal.settle({
          // TOTAL_REQUIRED after rebates: re-tax the rebated lines rather than take the suggestion.
          onRebatesRedeemed: (rebates) => recomputeTotal(rebates.updatedBasket),
        });
        result.textContent = [
          `Paid ${money(paid.cardAmountCharged)} · ${paid.paymentBrand ?? 'card'} · approval ${paid.approvalCode ?? '-'}`,
          paid.customerReceipt?.plainText ?? '',
        ].join('\n');
      });

    const end = el('button', { type: 'button', textContent: 'End session' });
    end.onclick = () => void run('Ending', () => session.end());

    show(
      el(
        'h2',
        {},
        `${terminal ? 'Terminal' : 'Local'} session ${session.id} · lane ${session.saleId}`,
      ),
      el('div', {}, ...catalog),
      el('table', {}, lines),
      total,
      memberLine,
      el('div', {}, phone, signIn),
      el('div', {}, pay, end),
      terminal ? '' : el('p', {}, 'Paying needs a terminal session.'),
      status,
      result,
    );
    renderBasket(session.basket.current);
    renderMember(null);
  }

  await detect();
}
