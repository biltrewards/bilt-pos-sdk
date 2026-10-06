/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.host.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;

/** One entry of a session's event stream: the {@code { seq, at, type, payload }} envelope. */
public final class Event {

  private final long seq;
  private final Instant at;
  private final String type;
  private final JsonNode payload;
  private final String json;

  Event(long seq, Instant at, String type, JsonNode payload) {
    this.seq = seq;
    this.at = at;
    this.type = type;
    this.payload = payload;
    ObjectNode envelope = Json.object();
    envelope.put("seq", seq);
    envelope.put("at", at.toString());
    envelope.put("type", type);
    envelope.set("payload", payload == null ? Json.nullNode() : payload);
    this.json = Json.write(envelope);
  }

  public long seq() {
    return seq;
  }

  public Instant at() {
    return at;
  }

  public String type() {
    return type;
  }

  public JsonNode payload() {
    return payload;
  }

  /** The envelope, serialized once at creation so every subscriber sends the same bytes. */
  public String json() {
    return json;
  }

  @Override
  public String toString() {
    return "Event{" + seq + " " + type + "}";
  }
}
