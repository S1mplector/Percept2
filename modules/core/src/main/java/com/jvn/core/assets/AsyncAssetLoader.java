package com.jvn.core.assets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Async I/O helpers for loading asset bytes and text off the calling thread.
 *
 * <p>All futures execute on a dedicated {@code jvn-asset-io-*} thread pool.
 * Call {@link #shutdown()} during application teardown to release threads.</p>
 */
public final class AsyncAssetLoader {

  private static final Logger log = LoggerFactory.getLogger(AsyncAssetLoader.class);

  private static final int THREAD_COUNT = Math.min(4, Runtime.getRuntime().availableProcessors());
  private static final ExecutorService EXECUTOR =
      Executors.newFixedThreadPool(THREAD_COUNT, namedThreadFactory("jvn-asset-io"));

  private static ThreadFactory namedThreadFactory(String prefix) {
    AtomicInteger counter = new AtomicInteger(0);
    return r -> {
      Thread t = new Thread(r, prefix + "-" + counter.incrementAndGet());
      t.setDaemon(true);
      return t;
    };
  }

  static {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.debug("AsyncAssetLoader shutdown: waiting up to 5s for in-flight loads");
      EXECUTOR.shutdown();
      try {
        if (!EXECUTOR.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
          log.warn("AsyncAssetLoader: executor did not terminate cleanly within 5s");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }, "jvn-asset-io-shutdown"));
  }

  private AsyncAssetLoader() {}

  /**
   * Load the full contents of {@code url} as a byte array asynchronously.
   *
   * @param url the resource URL to read
   * @return a future that resolves to the raw bytes, or fails with {@link IOException}
   */
  public static CompletableFuture<byte[]> loadBytes(URL url) {
    if (url == null) return CompletableFuture.failedFuture(new IllegalArgumentException("url must not be null"));
    return CompletableFuture.supplyAsync(() -> {
      try (InputStream in = url.openStream()) {
        return in.readAllBytes();
      } catch (IOException e) {
        log.warn("AsyncAssetLoader: failed to read bytes from '{}': {}", url, e.getMessage());
        throw new java.util.concurrent.CompletionException(e);
      }
    }, EXECUTOR);
  }

  /**
   * Load the full contents of {@code url} as a string asynchronously.
   *
   * @param url     the resource URL to read
   * @param charset the charset to decode with
   * @return a future that resolves to the decoded string, or fails with {@link IOException}
   */
  public static CompletableFuture<String> loadText(URL url, Charset charset) {
    if (url == null) return CompletableFuture.failedFuture(new IllegalArgumentException("url must not be null"));
    if (charset == null) return CompletableFuture.failedFuture(new IllegalArgumentException("charset must not be null"));
    return loadBytes(url).thenApply(bytes -> new String(bytes, charset));
  }

  /** Returns the shared executor for submitting off-thread work. */
  public static ExecutorService getExecutor() {
    return EXECUTOR;
  }

  /**
   * Shut down the shared I/O executor. Idempotent; safe to call multiple times.
   */
  public static void shutdown() {
    EXECUTOR.shutdown();
  }

}
