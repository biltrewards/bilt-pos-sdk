/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session;

import com.bilt.pos.platform.BiltCredentials;
import com.bilt.pos.platform.BiltEnvironment;
import com.bilt.pos.platform.BiltPlatformClient;
import com.bilt.pos.platform.OkHttpPlatformClient;
import com.bilt.pos.widget.WidgetHost;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The {@link WidgetHost} a session hands its widgets: a narrow view over the session and its {@link
 * SessionOperations}, plus the platform client the widgets share, created on first request from the
 * session's credentials and closed with the session.
 */
final class SessionWidgetHost implements WidgetHost {

  private static final Logger LOGGER = Logger.getLogger(SessionWidgetHost.class.getName());

  private final AbstractShopperSession session;
  private final BiltCredentials credentials;
  private final BiltEnvironment environment;
  private BiltPlatformClient platformClient;
  private boolean closed;

  SessionWidgetHost(
      AbstractShopperSession session, BiltCredentials credentials, BiltEnvironment environment) {
    this.session = session;
    this.credentials = credentials;
    this.environment = environment;
  }

  @Override
  public String sessionId() {
    return session.getSessionId();
  }

  @Override
  public BiltCredentials credentials() {
    return credentials;
  }

  @Override
  public BiltEnvironment environment() {
    return environment;
  }

  @Override
  public synchronized BiltPlatformClient platformClient() {
    if (credentials == null || closed) {
      return null;
    }
    if (platformClient == null) {
      platformClient =
          OkHttpPlatformClient.builder().credentials(credentials).environment(environment).build();
    }
    return platformClient;
  }

  @Override
  public Executor operationExecutor() {
    return session.operations.executor();
  }

  @Override
  public Executor callbackExecutor() {
    Executor callback = session.operations.callback();
    return callback != null ? callback : Runnable::run;
  }

  @Override
  public void reportBackgroundError(SessionError error) {
    session.operations.backgroundError("a widget's background work", error);
  }

  @Override
  public SessionContextSnapshot context() {
    return session.context().snapshot();
  }

  /** Closes the shared platform client, if one was created; further requests for it get null. */
  synchronized void close() {
    closed = true;
    if (platformClient == null) {
      return;
    }
    try {
      platformClient.close();
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "closing the session's platform client failed", e);
    } finally {
      platformClient = null;
    }
  }
}
