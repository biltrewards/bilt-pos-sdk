package com.bilt.pos.session;

import com.bilt.pos.nexo.client.BiltNexoClientException;
import com.bilt.pos.nexo.client.TerminalClient;
import com.bilt.pos.nexo.model.MessageCategoryType;
import com.bilt.pos.nexo.model.MessageHeader;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * An in-memory {@link TerminalClient} answering each message category with a scripted response
 * body, for session tests that need a terminal but no wire. Records every request it receives; a
 * category with no script fails the request like an unreachable terminal would.
 */
final class ScriptedTerminalClient implements TerminalClient {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private final Map<MessageCategoryType, String> scripts = new ConcurrentHashMap<>();
  private final ConcurrentLinkedQueue<SaleToPOIRequest> requests = new ConcurrentLinkedQueue<>();

  /** Answers every request of the given category with the given response envelope JSON. */
  ScriptedTerminalClient reply(MessageCategoryType category, String responseJson) {
    scripts.put(category, responseJson);
    return this;
  }

  /** The requests received so far, in arrival order. */
  List<SaleToPOIRequest> requests() {
    return new ArrayList<>(requests);
  }

  @Override
  public NexoTerminalAPI request(NexoTerminalAPI request) throws BiltNexoClientException {
    return request(request, null);
  }

  @Override
  public NexoTerminalAPI request(NexoTerminalAPI request, Duration timeout)
      throws BiltNexoClientException {
    SaleToPOIRequest body = request.getSaleToPOIRequest();
    MessageHeader header = body == null ? null : body.getMessageHeader();
    MessageCategoryType category = header == null ? null : header.getMessageCategory();
    requests.add(body);
    String script = category == null ? null : scripts.get(category);
    if (script == null) {
      throw new BiltNexoClientException("no scripted response for " + category);
    }
    try {
      return MAPPER.readValue(script, NexoTerminalAPI.class);
    } catch (IOException e) {
      throw new BiltNexoClientException("malformed scripted response for " + category, e);
    }
  }
}
