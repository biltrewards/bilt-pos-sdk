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
 * The contract between the retail-media widget and the ad decision platform, plus an in-memory fake
 * for tests and the emulator.
 *
 * <p>{@link com.bilt.pos.media.service.AdDecisionService} is the whole surface the widget needs: it
 * registers a shopper session described by an {@link com.bilt.pos.media.service.AdSessionSnapshot},
 * asks for a decision per placement within a deadline, validates the shopper's taps, reports
 * interactions, and subscribes to events the platform originates. The snapshot types here are
 * deliberately independent of the shopper session API ({@code ShopperSession}, {@code
 * SessionBasket}, {@code IdentifyResult}); the widget projects a session into a snapshot so the
 * service can evolve on its own.
 *
 * <p>{@link com.bilt.pos.media.service.InMemoryAdDecisionService} is the fake: scripted renderings
 * per placement, artificial latency, failure injection, recorded snapshots and reports, and
 * test-injected events. Everything downstream can be built and demoed against it until the
 * platform-backed implementation lands.
 */
package com.bilt.pos.media.service;
