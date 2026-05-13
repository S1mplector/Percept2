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
  void executorUsesAtMostFourThreads() throws Exception {
    URL resource = getClass().getResource("/com/jvn/core/assets/test-asset.txt");
    assertNotNull(resource);

    Set<String> threadNames = ConcurrentHashMap.newKeySet();
    int count = 100;
    List<CompletableFuture<Void>> futures = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      futures.add(AsyncAssetLoader.loadBytes(resource).thenAccept(b ->
          threadNames.add(Thread.currentThread().getName())
      ));
    }
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        .get(10, TimeUnit.SECONDS);

    assertTrue(threadNames.size() <= 4,
        "Expected ≤4 threads but got: " + threadNames);
    for (String name : threadNames) {
      assertTrue(name.startsWith("jvn-asset-io-"),
          "Thread name should start with 'jvn-asset-io-' but was: " + name);
    }
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
