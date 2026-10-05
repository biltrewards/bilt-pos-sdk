---
---

# ShopperSession — Integration Guide

`ShopperSession` is one shopper's visit at one lane. It holds the basket, the member and the checkout context for that visit, and it runs Bilt widgets such as [`RetailMedia`](#retail-media-widget) alongside it. The register passes basket, member and context in; widgets pass offers and interactions back out.

There are two session types. A plain `ShopperSession` runs on the register alone, with no payment terminal. `TerminalShopperSession` extends it with everything that needs a Bilt terminal: settlement, refunds, voids, stored value, prompts and the customer display. The terminal side is covered in depth in the [TerminalShopperSession guide](./checkout-session-integration.md); this guide covers what both types share and the widgets that run on them.

> **Release status.** The session, basket, member, context and widget APIs described here are complete. `RetailMedia` ships its public API and session bookkeeping in this release, but it does not yet request or draw ads: decisioning, caching and rendering arrive in a later release behind the same API. The [retail media section](#what-works-in-this-release) says exactly what runs today.

---

## Before you begin

Make sure you have:

1. A lane id (`saleId`) for each register and the store location id (`storeLocation`) it belongs to.
2. Bilt platform credentials (an OAuth client id and secret) if you will run platform-backed widgets such as `RetailMedia`.
3. For a terminal session: a boarded terminal and a `BiltNexoTerminalClient`, as described in the [Integration Guide](./integration.md).

---

## Mental model

- **One shopper, one lane.** A session starts when a shopper's visit starts and ends when it ends. Everything widgets know about the visit comes from the session: the basket, who the shopper is, and where the checkout stands.
- **The register only ever passes three things in.** The *basket* (`session.basket()`), the *member* (`session.member(...)`) and the *context* (`session.context()`: a checkout phase and free-form attributes). It never talks to a widget's backend and never builds platform requests.
- **What comes back is offers and interactions.** A widget hands the register validated offers to apply and an informational stream of interactions. The register decides how an offer affects its own pricing.
- **Widgets draw on surfaces the register hands over.** The register reserves space on screen and gives the widget a `Surface` for each placement. The widget decides what to show; the register decides where. See [surfaces](#surfaces).
- **Nothing a widget does blocks the register.** Widget work runs on the session's own threads under timeouts. A slow or unreachable platform shows up as an empty placement, and failures go to one handler, `onBackgroundError`. A session with no widgets behaves exactly as a session did before widgets existed.
- **Local calls are local.** Basket, member and context updates are pure local compute that return immediately, so they are safe to call from the register's UI thread. Only terminal operations (on `TerminalShopperSession`) go over the wire, and those are [lazy](./checkout-session-integration.md#lazy-execution).

```
Register (POS)                  ShopperSession                  Widgets (e.g. RetailMedia)
   │                                 │                                 │
   │ ── builder()…start() ─────────> │ ── attach(host), started ─────> │ registers the visit
   │ <── session ─────────────────── │                                 │ with its platform
   │                                 │                                 │
   │ ── basket().replace(cart) ────> │ diff against current basket     │
   │ <── Basket ──────────────────── │ ── basketChanged(diff) ───────> │
   │ ── member(Member...) ─────────> │ ── memberChanged ─────────────> │
   │ ── context().phase(...) ──────> │ ── contextChanged ────────────> │
   │                                 │                                 │ ── Surface.show(...)
   │                                 │                                 │    shopper taps a CTA
   │ <── onOffer(offer) ──────────────────────────────────────────────── │ token validated
   │     apply in register pricing   │                                 │
   │                                 │                                 │
   │ ── end() ─────────────────────> │ ── ended, detach ─────────────> │ closes the visit
```

---

## Choosing a session type

| | `ShopperSession` | `TerminalShopperSession` |
|---|---|---|
| Needs a Bilt terminal | No | Yes (`client`, `poiId`) |
| `start()` | Returns the session directly | Lazy `SessionResult<TerminalShopperSession>`; announces the session to the terminal |
| Basket, member, context, widgets | Yes | Yes (same API) |
| Member resolution by phone or account id | Not yet: the member stays pending | Background `BalanceInquiry` on the terminal |
| Settlement, refunds, voids, stored value | No | Yes |
| Customer display, input prompts, PIN | No | Yes |

Use `ShopperSession` for lanes without a Bilt terminal, or for a register that only needs the basket model, the member and widgets while payment runs elsewhere. Use `TerminalShopperSession` when the Bilt terminal takes payment. Everything in this guide applies to both, because `TerminalShopperSession extends ShopperSession`.

### Migrating from `CheckoutSession`

`CheckoutSession` was renamed and split. There is no deprecated alias, so update the references:

| Before | After |
|---|---|
| `CheckoutSession` | `TerminalShopperSession` |
| `CheckoutSession.builder()` | `TerminalShopperSession.builder()` (same builder surface; `start()` yields a `SessionResult<TerminalShopperSession>`) |
| `CheckoutSession.Builder` | `TerminalShopperSession.Builder` |
| `MemberIdentifier.phoneNumber("...")` | `Member.idResolver().phone("...")` |
| `MemberIdentifier.accountNumber("...")` | `Member.idResolver().accountId("...")` |
| `session.getMember()` | `session.member()` (`getMember()` is deprecated) |

`member()` is not a drop-in for `getMember()`. `getMember()` returned an `IdentifyResult`, and was `null` for a guest *and* while a member was still pending resolution. `member()` returns a `Member`, which is `null` only when nothing is attached and otherwise also covers a member the POS attached or one still pending. Read the fields through the `Member` accessors (`memberId()`, `status()`, `loyaltyBrand()`, `rewards()`, `pointBalance()`), and where you tested `getMember() != null` for "a member is identified", test `member() != null && member().isResolved()`. Skipping the `isResolved()` check would treat a pending member as identified, though settlement still treats the visit as a guest's until it resolves.

Code that only uses the basket, the member and the session ids can take a `ShopperSession` parameter and run against either type.

---

## Start and end a session

### Local session

`ShopperSession.builder()` needs a `saleId` and a `currency`. `start()` has nothing to acknowledge, so it returns the session directly, with every widget attached:

```java
ShopperSession session = ShopperSession.builder()
    .saleId("POS-LANE-3")
    .currency("USD")
    .storeLocation("STR-0142")
    .credentials(BiltCredentials.clientCredentials(clientId, clientSecret))
    .onBackgroundError(error -> log.warn("session background error: {}", error))
    .start();
```

`start()` throws `IllegalStateException` if `saleId` or `currency` is missing.

### Terminal session

`TerminalShopperSession.builder()` additionally needs the terminal `client` and its `poiId`. Its `start()` is lazy: it announces the session to the terminal and yields the session once the terminal acknowledges. Widgets attach after that acknowledgement.

```java
TerminalShopperSession session = TerminalShopperSession.builder()
    .client(client)
    .saleId("POS-LANE-3")
    .poiId("VictaLane-275839164")
    .currency("USD")
    .storeLocation("STR-0142")
    .credentials(BiltCredentials.clientCredentials(clientId, clientSecret))
    .onBackgroundError(error -> log.warn("session background error: {}", error))
    .start()
    .get();
```

A refused start hands out no session; call `start()` again for a fresh attempt. The [TerminalShopperSession guide](./checkout-session-integration.md#start-a-session) covers the terminal bracket in detail.

### Credentials and environment

`credentials(...)` and `environment(...)` on either builder are the Bilt *platform* credentials that widgets use. They are unrelated to the terminal client's own credentials.

- `BiltCredentials.clientCredentials(clientId, clientSecret)` is an OAuth 2.0 client-credentials pair. The secret never appears in `toString()`, logs or exceptions.
- `environment(...)` defaults to `BiltEnvironment.PRODUCTION`. Use `BiltEnvironment.STAGING` for testing, or `BiltEnvironment.custom(tokenEndpoint, apiBaseUrl)` to point at a mock server.
- The session creates one platform client lazily, shares it among its widgets, and closes it when the session ends. The register never handles tokens.

Credentials are optional. A session without them works normally; a platform-backed widget on it reports a `SessionError` through `onBackgroundError` at attach and the session continues without that widget.

### End the session

`end()` is lazy on both types. It freezes the basket, notifies widgets, detaches them, and seals the session; it cannot be restarted.

```java
session.end()
    .onSuccess(ended -> screen.showWelcome())
    .onError(error -> screen.showEndFailed(error.getMessage()))
    .execute();
```

`onComplete` runs on failure too, so it suits cleanup but not the move to the welcome screen. A refused `end()` leaves the session open; show the failure and retry `end()`.

`ShopperSession` is `AutoCloseable`. `close()` is a best-effort, *blocking* `end()` that logs failures instead of throwing, which suits try-with-resources. Don't call `close()` from the callback executor's own thread while operations are in flight; use `end().execute()` with `onSuccess` and `onError` instead.

---

## Basket

`session.basket()` offers three ways to update the basket. They produce the same result and can be mixed. Each returns the updated immutable `Basket`, and each change reaches widgets as a single `BasketChange` diff.

| Style | Call | Use it when |
|---|---|---|
| Incremental | `addItem`, `removeItemBySku`, `updateItemQuantityBySku`, `setDiscountsBySku`, `setTaxRateBySku`, … | The register reacts to scans one at a time. |
| Batch | `mutate(m -> ...)` | Several edits belong together (a price override plus tax, a multi-buy). Widgets see one change. |
| Snapshot | `replace(items)` or `replace(basket)` | The POS owns its own cart model and can hand over the whole cart on every change. |

```java
// Incremental: one call per scan
session.basket().addItem(BasketItem.sale("KRK-CNDL-LRG-VAN", "Large Vanilla Candle", 1, new BigDecimal("24.99")));
session.basket().updateItemQuantityBySku("KRK-CNDL-LRG-VAN", 2);

// Batch: applied atomically, reported once
session.basket().mutate(m -> m
    .addItem(BasketItem.sale("KRK-WICK-TRIM", "Wick Trimmer", 1, new BigDecimal("12.00")))
    .setTaxRateBySku("KRK-WICK-TRIM", new BigDecimal("0.0875")));

// Snapshot: hand over the POS cart as it is now
List<BasketItem> items = pos.cart().lines().stream()
    .map(line -> BasketItem.builder()
        .reference(line.id())
        .sku(line.sku())
        .description(line.name())
        .quantity(line.quantity())
        .unitPrice(line.unitPrice())
        .build())
    .collect(Collectors.toList());
session.basket().replace(items);
```

### How `replace` diffs

`replace` compares the snapshot with the current basket and applies only the difference:

- Lines pair up by `reference` when one is set, otherwise by SKU and item type among lines without a reference. A paired line keeps its item id; unpaired lines are added or removed.
- A snapshot equal to the current basket changes nothing: no widget notification and no display push.
- An ambiguous snapshot (two lines with the same reference, or two unreferenced lines with the same SKU and type) throws `IllegalArgumentException` and leaves the basket untouched. Set `reference` to your POS line id to avoid this.
- After a terminal settlement has consumed the basket, `replace` behaves like `clear()` followed by adding every line.

Currency is set once on the session; `BasketItem` has no currency of its own. Mutations throw `IllegalStateException` while the basket is frozen (during settlement) or after the session ended.

---

## Member

A member can be attached at any time, before or after items are scanned, and replaced or removed later. `session.member(...)` takes one of three forms:

```java
session.member(Member.id("mbr_8f2a"));                               // a known Bilt member id: attached immediately
session.member(Member.idResolver().phone("+12015550123"));           // resolved in the background
session.member(Member.idResolver().accountId("4823").keyedByCashier());
session.member(null);                                                // signs the member out
```

- `Member.id(memberId)` is already resolved and needs no lookup.
- `Member.idResolver()` builds a *pending* member from what the shopper gave you: `accountId`, `phone`, `email` or `custom(type, value)`. Add `keyedByCashier()` when the cashier typed it rather than the shopper.
- `session.member()` returns the attached member, resolved or pending, or `null` for a guest.

### Where resolution runs

A pending member resolves in the background, on the session's operation lane, so it never blocks the register. It runs in order with other session work: a `settle()` executed after attaching a member waits for the lookup.

- **Terminal session:** resolved with a `BalanceInquiry` through the terminal. Account id and phone are supported; email and custom identifiers report `UNSUPPORTED` through `onBackgroundError` and stay pending. A lookup that finds nobody clears the member; a lookup that fails reports through `onBackgroundError` and leaves it pending. If you need the result directly, `identifyMember(pendingMember)` runs the same lookup as a `SessionResult`.
- **Local session:** there is no resolution yet, so a pending member stays pending. Pass `Member.id(...)` when the POS already knows the Bilt member id.

Until a member resolves, the visit is treated as a guest's. The last member attached wins, so a late lookup never overwrites a newer choice.

### Reacting to member changes

Register `onMemberChanged` on the builder to update the register screen. It fires for every change, including a pending member resolving, with `null` meaning signed out:

```java
ShopperSession session = ShopperSession.builder()
    .saleId("POS-LANE-3")
    .currency("USD")
    .member(Member.id("mbr_8f2a"))          // initial state: not announced
    .onMemberChanged(member -> screen.showMember(member))
    .start();
```

A member passed to the builder is initial state and is not announced; a later resolution of it is. Re-attaching an equal member is not a change. The handler runs on the `callbackExecutor` when one is set, otherwise on the thread that made the change.

---

## Context

`session.context()` carries where the checkout stands, for widgets to act on:

- **Phase:** a `CheckoutPhase`, one of `SCANNING` (the default), `MEMBER_IDENTIFIED`, `TENDERING` and `COMPLETE`.
- **Attributes:** free-form string pairs such as a lane type or a store format.
- The lane ids (`saleId()`, `currency()`, `storeLocation()`, and `poiId()` on a terminal session).

```java
session.context().phase(CheckoutPhase.TENDERING);
session.context().attribute("lane-type", "pharmacy");
session.context().removeAttribute("lane-type");
```

Both builders take `phase(...)` and a repeatable `attribute(key, value)` to seed the context at start. A terminal session moves the phase itself: `TENDERING` when `settle()` starts, `COMPLETE` when it succeeds, back to the prior phase if it fails or is aborted, and `SCANNING` on `basket().clear()`. `MEMBER_IDENTIFIED` is never set automatically. A local session never moves the phase on its own, so set it as your checkout progresses.

Widgets use the phase to decide when to act. `RetailMedia`, for example, shows media only in its eligible phases (every phase except `COMPLETE` by default) and clears its surfaces when the checkout leaves them. Attributes are passed to the widget's platform with the session, for targeting.

---

## Retail media widget

`RetailMedia` shows Bilt retail media on the register's screens and hands the register validated offers to apply. It works the same on both session types.

### Attach it to a session

Build the widget with one `Surface` per placement, then register it on the session builder. `widget(...)` is repeatable, one widget per call, in order.

```java
RetailMedia retailMedia = RetailMedia.builder()
    .surface(Placement.of("lane-banner"), bannerSurface)
    .onOffer(this::applyOffer)
    .onInteraction(event -> analytics.record(event))
    .adService(adService)
    .build();

session = ShopperSession.builder()
    .saleId("POS-LANE-3")
    .currency("USD")
    .storeLocation("STR-0142")
    .credentials(BiltCredentials.clientCredentials(clientId, clientSecret))
    .onBackgroundError(error -> log.warn("session background error: {}", error))
    .widget(retailMedia)
    .start();
```

- `surface(placement, surface)` is required at least once. A placement is a named zone from the platform's placement map, such as a banner strip beside the cart; each is bound to exactly one surface.
- `storeLocation` is optional on the session but required by `RetailMedia`: without it (or without a currency) the widget reports `INVALID_STATE` at attach and stays inert.
- `adService(...)` supplies the ad decision service. There is no platform-backed default in this release; use `InMemoryAdDecisionService` for development (see [below](#what-works-in-this-release)). Without one, the widget reports `UNSUPPORTED` at attach and stays inert.
- Optional tuning: `eligiblePhases(Set<CheckoutPhase>)`, `decisionTimeout(Duration)` (default 500 ms) and `renderingTtl(Duration)`.

`onOffer` is registered before the session exists, so point it at a method that reads the session from a field once `start()` has returned (the widget can't deliver an offer before then). At runtime, reach the widget through the session with `session.widget(RetailMedia.class)`.

### Handle offers

`onOffer` is the only callback the register must act on. It fires after the shopper accepted an offer on screen *and* the ad platform validated it. The `Offer` carries its id, a scope, the SKU for a line-item offer, an amount *or* a percentage, an optional expiry and the creative it came from. The SDK never changes prices; the register applies the offer in its own pricing. Only the validated offer counts, never the creative's copy.

| `offer.getScope()` | Meaning | Apply it as |
|---|---|---|
| `LINE_ITEM` | Off one product, `offer.getSku()` | A discount on that line |
| `BASKET` | Off the whole order | An order-level discount in the register's pricing |

Exactly one of `getAmount()` (a fixed amount in the session currency) and `getPercentage()` (0 < p ≤ 100) is set. If your basket lives in the session (incremental or batch updates), a line-item offer becomes a `BasketDiscount` on that line:

```java
void applyOffer(Offer offer) {   // session: the field set from start()
    if (offer.getExpiry() != null && offer.getExpiry().isBefore(Instant.now())) {
        return;
    }
    if (offer.getScope() == Offer.Scope.BASKET) {
        pos.applyOrderDiscount(offer.getId(), offer.getAmount(), offer.getPercentage());
        return;
    }
    // A SKU can appear on several lines, and on return or credit lines, so pick the sale line
    // explicitly instead of getItemBySku(), which throws when the SKU is ambiguous.
    List<BasketLineItem> saleLines = session.basket().snapshot().getItems().stream()
        .filter(item -> item.isSale() && item.getSku().equals(offer.getSku()))
        .collect(Collectors.toList());
    if (saleLines.size() != 1) {
        return; // the product left the basket, or the offer cannot name one line
    }
    BasketLineItem line = saleLines.get(0);
    BigDecimal off = offer.getAmount() != null
        ? offer.getAmount()
        : line.getUnitPrice()
            .multiply(BigDecimal.valueOf(line.getQuantity()))
            .multiply(offer.getPercentage())
            .movePointLeft(2)
            .setScale(2, RoundingMode.HALF_UP);
    if (off.signum() <= 0) {
        return; // a small percentage can round to 0.00; BasketDiscount rejects zero
    }
    List<BasketDiscount> discounts = new ArrayList<>(line.getDiscounts());
    discounts.add(BasketDiscount.offer(offer.getId(), "Bilt offer", off));
    session.basket().setDiscounts(line.getItemId(), discounts);
}
```

When the SKU matches no single sale line, skip the offer or resolve the line in your own cart; don't guess. If the POS owns the cart and calls `replace(...)` on every change, apply the offer to the POS cart instead; the next `replace` carries the discount into the session. Otherwise the next snapshot, which lacks the discount, would remove it.

### Interactions

`onInteraction` is informational. Each shopper-visible ad event arrives as an `AdInteraction` with the creative id, the placement, a timestamp and a `Kind`: `SHOWN`, `VIEWED`, `TAPPED` (with the tapped `Action`), `CTA_ACCEPTED`, `SEND_TO_PHONE_REQUESTED`, `DISMISSED` or `COMPLETED`. Log it, adjust your own screen, or ignore it. Interactions carry no personal data and no basket data.

Both `onOffer` and `onInteraction` are delivered on the session's `callbackExecutor` (or the calling thread when none is set). A handler that throws is reported through `onBackgroundError` and does not affect the widget.

### Pause and resume

Pause the widget while the screen shows something the shopper must not be distracted from: PIN entry, signature capture, a cart review. `pause()` clears every surface immediately and stops new content until `resume()`. The widget keeps receiving session changes while paused, so it resumes with the current basket and member. Both calls are idempotent and safe from any thread.

```java
RetailMedia retailMedia = terminalSession.widget(RetailMedia.class);
retailMedia.pause();
terminalSession.requestSignature("Please sign below")
    .onComplete(retailMedia::resume)
    .execute();
```

### Surfaces

A `Surface` is where the widget draws. It has two verbs: `show(rendering, actions)` replaces whatever is on screen, and `clear()` removes it. A `Rendering` is a structured creative with no personal data: media (image, video or HTML), a headline and body, up to two calls to action with opaque tokens, a TTL and tracking URLs. The surface reports what the shopper does through the `ActionSink` it was given.

Registers differ in what they can host, so there are five ways to get a rendering on screen, from least to most register work:

| Tier | The register provides | Status in this release |
|---|---|---|
| 1. Container | Space on screen; a platform module draws with native widgets (Android `View`, Compose, Swing) | Contract only; no adapter ships yet |
| 2. WebView | An embedded browser control, wired to a `WebSurface` subclass | Available: `WebSurface` |
| 3. URL slot | An iframe or kiosk browser pointed at a hosted Bilt page | Planned |
| 4. Content handoff | Its own drawing of the `Rendering` in its design system | Implement `Surface` (see below) |
| 5. Custom surface | Anything else: a second cashier display, a pin-pad line, a receipt footer | Implement `Surface` |

**Second screen or separate device.** A surface draws wherever the register can reach, so a customer-facing second display attached to the register is just another `Surface`, bound to its own placement. A screen on a separate device (a tablet at the lane) needs its own view of the session and belongs to the URL-slot tier.

#### A WebView surface

Subclass `WebSurface` and wire two outbound calls and one inbound message. A default Android `WebView` has JavaScript disabled, so the example enables it before installing the bridge; construct the surface on the UI thread, since both calls touch the `WebView`. The base class loads Bilt's hosted renderer page lazily, waits for its `ready` signal, pushes renderings to it and checks every inbound message against the rendering on screen.

```java
final class AndroidWebViewSurface extends WebSurface {
    private final WebView webView;

    AndroidWebViewSurface(WebView webView, String rendererPageUrl) {
        super(rendererPageUrl);
        this.webView = webView;
        // Off by default; without it the renderer page can neither send `ready` nor draw.
        webView.getSettings().setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void postMessage(String json) {
                onBridgeMessage(json);
            }
        }, "BiltMediaBridge");
    }

    @Override
    protected void loadUrl(String url) {
        webView.post(() -> webView.loadUrl(url));
    }

    @Override
    protected void evaluateJavascript(String script) {
        webView.post(() -> webView.evaluateJavascript(script, null));
    }
}
```

Both outbound calls must marshal to the browser control's thread and must not wait for a result. Override `onBridgeError(reason, rawJson)` to log malformed or foreign messages from the page; by default they are logged as warnings. The bridge messages in both directions are specified by `bridge-messages.schema.json`, shipped in the SDK jar under `com/bilt/pos/widget/`.

#### Drawing the creative yourself

To draw in your own design system, implement `Surface`. Hand the rendering's own `Cta` back on a tap; never build one yourself, because the token is validated against the rendering that was shown.

```java
final class CartBannerSurface implements Surface {
    private final CartScreen screen;

    CartBannerSurface(CartScreen screen) {
        this.screen = screen;
    }

    @Override
    public Set<MediaSpec.MediaType> supportedFormats() {
        return EnumSet.of(MediaSpec.MediaType.IMAGE);
    }

    @Override
    public void show(Rendering rendering, ActionSink actions) {
        screen.runOnUiThread(() -> {
            screen.showBanner(rendering.getMedia().getUrl(), rendering.getHeadline(), rendering.getBody());
            Cta cta = rendering.getCta();
            if (cta != null) {
                screen.onBannerButton(cta.getLabel(), () -> actions.perform(cta));
            }
            actions.viewed(rendering);
        });
    }

    @Override
    public void clear() {
        screen.runOnUiThread(screen::hideBanner);
    }
}
```

The widget calls `show` and `clear` from its own thread, one at a time; marshal to your UI thread and return without blocking. `ActionSink` calls may come from any thread. `supportedFormats()` tells the platform what this surface can draw, so it never serves, say, a video to an image-only banner. Media URLs are signed and short-lived, so don't cache them across renderings.

### What works in this release

`RetailMedia` ships its public API and the session bookkeeping behind it. Today it:

- registers the visit with the ad decision service at start, keeps it current on every basket (sale lines), member and context change, and closes it at the end;
- clears its surfaces on `pause()` and when the checkout leaves an eligible phase;
- validates a surface's taps against the rendering on screen and with the service, and only then fires `onOffer` and `onInteraction`.

It does **not** yet request decisions or call `Surface.show`: decisioning, caching, rendering and measurement reporting arrive in a later release behind the same API. There is also no platform-backed `AdDecisionService` yet. For development and tests, pass the in-memory fake, which records every session snapshot it receives:

```java
InMemoryAdDecisionService adService = new InMemoryAdDecisionService();

RetailMedia retailMedia = RetailMedia.builder()
    .surface(Placement.of("lane-banner"), bannerSurface)
    .adService(adService)
    .onOffer(this::applyOffer)
    .build();
```

You can wire surfaces, offers, pause/resume and credentials now; that code keeps working when decisioning lands.

---

## Reliability

- **Nothing blocks the register.** Basket, member and context calls return immediately. Widgets hear about changes on the session's operation lane, never on the register thread or under the session lock, and fast changes are merged so widgets always see the newest state.
- **A miss is a blank placement.** Ad decisions run under `decisionTimeout`. A slow platform, no fill, a network failure or a creative the surface can't draw all result in nothing on screen, never an error in a basket call.
- **One place for failures.** Every widget failure goes to the builder's `onBackgroundError`: a widget that could not attach, a platform request that failed, a tap the platform refused. On a terminal session the same handler also receives automatic display failures.
- **A failing widget is isolated.** A widget that fails to attach is left detached and the session carries on without it. A throwing handler or observer never affects the session or other widgets.
- **Offline.** If the platform can't be reached, placements stay empty and offer validation is refused. An offer is never applied without validation, so the safe default is always the undiscounted price.

```java
ShopperSession session = ShopperSession.builder()
    .saleId("POS-LANE-3")
    .currency("USD")
    .storeLocation("STR-0142")
    .callbackExecutor(uiExecutor)
    .onBackgroundError(error -> {
        if (error.getCode() == SessionErrorCode.UNSUPPORTED) {
            log.info("widget unavailable: {}", error.getMessage());
        } else {
            log.warn("session background error: {}", error);
        }
    })
    .widget(retailMedia)
    .start();
```

---

## Common entry points (cheat sheet)

| Task | Call |
|---|---|
| Start a local session | `ShopperSession.builder().saleId(..).currency(..).start()` |
| Start a terminal session | `TerminalShopperSession.builder().client(..).saleId(..).poiId(..).currency(..).start().get()` |
| Add a widget | `.widget(RetailMedia.builder()...build())` on either builder |
| Platform credentials | `.credentials(BiltCredentials.clientCredentials(id, secret))`, `.environment(BiltEnvironment.STAGING)` |
| Update the basket | `basket().addItem(..)`, `basket().mutate(m -> ..)`, `basket().replace(items)` |
| Attach a member | `member(Member.id(..))`, `member(Member.idResolver().phone(..))` |
| Sign out | `member(null)` |
| Watch member changes | `.onMemberChanged(member -> ..)` on the builder |
| Set the phase | `context().phase(CheckoutPhase.TENDERING)` |
| Set an attribute | `context().attribute("lane-type", "pharmacy")` |
| Reach a widget | `session.widget(RetailMedia.class)` |
| Pause media | `session.widget(RetailMedia.class).pause()` / `.resume()` |
| Background failures | `.onBackgroundError(error -> ..)` on the builder |
| End | `session.end().execute()` or try-with-resources |

---

## Next steps

- [TerminalShopperSession guide](./checkout-session-integration.md) for settlement, refunds, voids, stored value and the customer display.
- [SDK Javadoc](./javadoc/index.html) for every type in this guide.
- The register emulator will carry the reference retail media integration once the native renderer lands (RET-6777).
