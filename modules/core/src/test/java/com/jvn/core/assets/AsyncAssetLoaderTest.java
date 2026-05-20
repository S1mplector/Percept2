package com.jvn.core.assets;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AsyncAssetLoaderTest {

  @Test
  void hundredConcurrentLoadsComplete() throws Exception {
    URL resource = getClass().getResource("/com/jvn/core/assets/test-asset.txt");
    assertNotNull(resource, "test-asset.txt must be on the classpath");

    int count = 100;
    List<CompletableFuture<byte[]>> futures = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      futures.add(AsyncAssetLoader.loadBytes(resource));
    }

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        .get(10, TimeUnit.SECONDS);

    for (CompletableFuture<byte[]> f : futures) {
      assertNotNull(f.get());
      assertTrue(f.get().length > 0);
    }
  }

  @Test
  void executorUsesBoundedAssetIoThreads() throws Exception {
    URL resource = getClass().getResource("/com/jvn/core/assets/test-asset.txt");
    assertNotNull(resource);

    Set<String> threadNames = ConcurrentHashMap.newKeySet();
    int count = 100;
    List<CompletableFuture<Void>> futures = new ArrayList<>(count);
    // Use thenAcceptAsync with the shared executor so the callback always runs
    // on a pool thread, not the caller thread (which happens when the future is
    // already complete at the point thenAccept is attached).
    for (int i = 0; i < count; i++) {
      futures.add(AsyncAssetLoader.loadBytes(resource).thenAcceptAsync(b ->
          threadNames.add(Thread.currentThread().getName()),
          AsyncAssetLoader.getExecutor()
      ));
    }
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        .get(10, TimeUnit.SECONDS);

    assertTrue(threadNames.size() <= 16,
        "Expected ≤16 threads but got: " + threadNames);
    for (String name : threadNames) {
      assertTrue(name.startsWith("jvn-asset-io-"),
          "Thread name should start with 'jvn-asset-io-' but was: " + name);
    }
  }

  @Test
  void defaultThreadCountUsesFourToSixteenThreads() {
    assertEquals(4, AsyncAssetLoader.resolveThreadCount(1, ""));
    assertEquals(4, AsyncAssetLoader.resolveThreadCount(2, ""));
    assertEquals(6, AsyncAssetLoader.resolveThreadCount(3, ""));
    assertEquals(8, AsyncAssetLoader.resolveThreadCount(4, ""));
    assertEquals(16, AsyncAssetLoader.resolveThreadCount(8, ""));
  }

  @Test
  void configuredThreadCountIsBounded() {
    assertEquals(6, AsyncAssetLoader.resolveThreadCount(2, "6"));
    assertEquals(16, AsyncAssetLoader.resolveThreadCount(8, "64"));
    assertEquals(4, AsyncAssetLoader.resolveThreadCount(2, "invalid"));
    assertEquals(4, AsyncAssetLoader.resolveThreadCount(2, "0"));
  }

  @Test
  void loadTextDecodesUtf8() throws Exception {
    URL resource = getClass().getResource("/com/jvn/core/assets/test-asset.txt");
    assertNotNull(resource);
    String text = AsyncAssetLoader.loadText(resource, StandardCharsets.UTF_8).get(5, TimeUnit.SECONDS);
    assertNotNull(text);
    assertFalse(text.isBlank());
  }

  @Test
  void nullUrlFailsGracefully() {
    CompletableFuture<byte[]> f = AsyncAssetLoader.loadBytes(null);
    assertTrue(f.isCompletedExceptionally());
  }
}
