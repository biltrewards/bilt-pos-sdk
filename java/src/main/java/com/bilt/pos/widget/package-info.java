/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
/**
 * Contracts between a Bilt widget and the register screen it draws on.
 *
 * <p>A widget (the retail-media banner is the first) decides <em>what</em> to show and when; the
 * register decides <em>where</em>. The seam between the two is {@link com.bilt.pos.widget.Surface}:
 * the widget hands a {@link com.bilt.pos.widget.Rendering} to the surface, the surface draws it and
 * reports the shopper's interactions back through the {@link com.bilt.pos.widget.ActionSink} it was
 * given. Nothing in a rendering carries PII; a rendering is a structured creative — media, copy, up
 * to two calls to action with opaque tokens, a TTL and tracking URLs — that any surface can draw.
 *
 * <h2>Surface tiers</h2>
 *
 * <p>Registers differ wildly in what they can host, so the SDK offers five ways to get a rendering
 * on screen, from least to most integration work for the register:
 *
 * <ol>
 *   <li><b>Hand us a container.</b> A platform module supplies a native adapter — an Android {@code
 *       View}, a Compose slot, a Swing panel — that implements {@code Surface} and draws the
 *       rendering with platform widgets. The register only reserves the space.
 *   <li><b>Hand us a WebView.</b> The register already embeds a browser control; it subclasses
 *       {@link com.bilt.pos.widget.WebSurface}, wires two calls ({@code loadUrl} and {@code
 *       evaluateJavascript}) and one inbound JavaScript interface, and the SDK drives Bilt's hosted
 *       renderer page through a small, documented JSON bridge.
 *   <li><b>Hand us a URL slot.</b> The register can point an iframe or kiosk browser at a URL but
 *       cannot script it; the SDK gives it a hosted page URL bound to the session and the page
 *       talks to Bilt directly. Interactions reach the SDK through the ad platform's event channel
 *       rather than a local sink.
 *   <li><b>Take the content.</b> The register wants to draw the creative in its own design system;
 *       it receives the {@code Rendering} itself (media URLs, copy, CTA tokens) and draws whatever
 *       it likes, reporting interactions through the sink like any other surface.
 *   <li><b>Implement {@code Surface} yourself.</b> Anything else — a second cashier display, a
 *       pin-pad line, a receipt footer — is a {@code Surface} implementation the register owns end
 *       to end.
 * </ol>
 *
 * <p>This core module is platform-neutral: it defines the contracts and the web bridge but ships no
 * adapter. Adapters live in the platform modules, next to the UI toolkit they draw with.
 *
 * <h2>Threading</h2>
 *
 * <p>The widget calls a surface from its own single-threaded executor; a surface marshals to its UI
 * thread as needed and must never block the caller. Callbacks into the {@code ActionSink} may
 * arrive from any thread, including a UI or JavaScript thread; the sink marshals back to the
 * widget.
 */
package com.bilt.pos.widget;
