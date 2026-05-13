package com.jvn.runtime.hotreload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Local TCP server that listens for {@code RELOAD <path>} messages from the editor.
 *
 * <p>Activate by passing {@code -Djvn.hotReload.port=NNNN} to the runtime JVM.
 * When a {@code RELOAD} command is received, the registered callback is invoked
 * on a background thread — the callback must dispatch to the appropriate thread
 * if UI updates are needed.</p>
 *
 * <pre>{@code
 * // In JvnApp.main():
 * HotReloadServer.startIfEnabled(path -> RuntimeVnInterop.reloadScenario(path));
 * }</pre>
 */
public final class HotReloadServer {

  private static final Logger log = LoggerFactory.getLogger(HotReloadServer.class);
  private static final String PROP_PORT = "jvn.hotReload.port";

  private final int port;
  private final Consumer<String> reloadCallback;
  private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
    Thread t = new Thread(r, "jvn-hotreload-server");
    t.setDaemon(true);
    return t;
  });

  private volatile ServerSocket serverSocket;

  private HotReloadServer(int port, Consumer<String> reloadCallback) {
    this.port = port;
    this.reloadCallback = reloadCallback;
  }

  /**
   * Start the server if {@code -Djvn.hotReload.port} is set.
   *
   * @param reloadCallback called with the script path when a {@code RELOAD} command arrives
   * @return the started server, or {@code null} if the property is not set
   */
  public static HotReloadServer startIfEnabled(Consumer<String> reloadCallback) {
    String portProp = System.getProperty(PROP_PORT);
    if (portProp == null || portProp.isBlank()) return null;
    int port;
    try {
      port = Integer.parseInt(portProp.trim());
    } catch (NumberFormatException e) {
      log.warn("HotReloadServer: invalid port '{}', hot reload disabled", portProp);
      return null;
    }
    HotReloadServer server = new HotReloadServer(port, reloadCallback);
    server.start();
    return server;
  }

  private void start() {
    executor.submit(() -> {
      try {
        serverSocket = new ServerSocket(port);
        log.info("HotReloadServer: listening on port {}", port);
        while (!serverSocket.isClosed()) {
          try {
            Socket client = serverSocket.accept();
            executor.submit(() -> handleClient(client));
          } catch (IOException e) {
            if (!serverSocket.isClosed()) {
              log.warn("HotReloadServer: accept error: {}", e.getMessage());
            }
          }
        }
      } catch (IOException e) {
        log.error("HotReloadServer: could not bind to port {}: {}", port, e.getMessage());
      }
    });
  }

  private void handleClient(Socket client) {
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.startsWith("RELOAD ")) {
          String path = line.substring("RELOAD ".length()).trim();
          log.debug("HotReloadServer: RELOAD received for '{}'", path);
          try {
            reloadCallback.accept(path);
          } catch (Exception e) {
            log.warn("HotReloadServer: reload callback failed for '{}': {}", path, e.getMessage());
          }
        } else {
          log.debug("HotReloadServer: unknown command '{}'", line);
        }
      }
    } catch (IOException e) {
      log.trace("HotReloadServer: client connection closed: {}", e.getMessage());
    } finally {
      try { client.close(); } catch (IOException ignored) {
            // reason: I/O failure on best-effort save/load; in-memory state remains valid
        }
    }
  }

  /** Shut down the server. */
  public void stop() {
    try {
      if (serverSocket != null) serverSocket.close();
    } catch (IOException e) {
      log.warn("HotReloadServer: error closing server socket: {}", e.getMessage());
    }
    executor.shutdown();
  }
}
