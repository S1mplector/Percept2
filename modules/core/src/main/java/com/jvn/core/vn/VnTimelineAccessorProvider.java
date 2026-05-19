package com.jvn.core.vn;

/**
 * Exposes the timeline proxy accessor used by VN interop so renderers can draw
 * Puppeteer-driven VN layers from the same proxy state that timelines update.
 */
public interface VnTimelineAccessorProvider {
  VnCharacterSceneAccessor getTimelineAccessor();
}
