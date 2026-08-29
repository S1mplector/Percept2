package com.jvn.web;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.teavm.jso.ajax.XMLHttpRequest;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.webaudio.AudioBuffer;
import org.teavm.jso.webaudio.AudioContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Fetches and decodes audio assets into {@link AudioBuffer}s, caching decoded
 * results by asset id so repeated plays of the same sound reuse the buffer.
 */
final class WebAudioAssetLoader {
  private static final Logger log = LoggerFactory.getLogger(WebAudioAssetLoader.class);

  private final AudioContext context;
  private final Map<String, AudioBuffer> cache = new HashMap<>();
  private final Map<String, List<Consumer<AudioBuffer>>> pending = new HashMap<>();

  WebAudioAssetLoader(AudioContext context) {
    this.context = context;
  }

  static String resolveUrl(String id) {
    return WebImageCache.resolveAssetUrl(id);
  }

  void getOrLoad(String id, Consumer<AudioBuffer> onReady) {
    AudioBuffer cached = cache.get(id);
    if (cached != null) {
      onReady.accept(cached);
      return;
    }
    List<Consumer<AudioBuffer>> waiters = pending.get(id);
    if (waiters != null) {
      waiters.add(onReady);
      return;
    }
    List<Consumer<AudioBuffer>> newWaiters = new ArrayList<>();
    newWaiters.add(onReady);
    pending.put(id, newWaiters);

    String url = resolveUrl(id);
    XMLHttpRequest request = new XMLHttpRequest();
    request.open("GET", url, true);
    request.setResponseType("arraybuffer");
    request.onLoad(event -> {
      if (request.getStatus() < 200 || request.getStatus() >= 300) {
        log.warn("Audio fetch failed for id={} url={} status={}", id, url, request.getStatus());
        pending.remove(id);
        return;
      }
      ArrayBuffer buffer = (ArrayBuffer) request.getResponse();
      context.decodeAudioData(
          buffer,
          decoded -> {
            cache.put(id, decoded);
            List<Consumer<AudioBuffer>> fired = pending.remove(id);
            if (fired != null) {
              for (Consumer<AudioBuffer> waiter : fired) waiter.accept(decoded);
            }
          },
          error -> {
            log.warn("Audio decode failed for id={} url={}", id, url);
            pending.remove(id);
          });
    });
    request.onError(event -> {
      log.warn("Audio fetch network error for id={} url={}", id, url);
      pending.remove(id);
    });
    request.send();
  }

  void clear() {
    cache.clear();
    pending.clear();
  }
}
