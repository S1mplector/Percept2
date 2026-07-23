package com.jvn.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry for discovering and managing platform-specific renderer implementations.
 *
 * <p>Uses Java ServiceLoader to auto-discover {@link RendererFactory} implementations
 * on the classpath. Registry discovery does not by itself imply that a backend
 * has an executable platform integration or packaging pipeline.</p>
 */
public class RendererRegistry {
  private static final Logger log = LoggerFactory.getLogger(RendererRegistry.class);

  private final Map<String, RendererFactory> factories = new HashMap<>();

  /**
   * Load all available renderer factories from the classpath.
   */
  public RendererRegistry() {
    loadFactories();
  }

  private void loadFactories() {
    ServiceLoader<RendererFactory> loader = ServiceLoader.load(RendererFactory.class);
    for (RendererFactory factory : loader) {
      String name = factory.getRendererName();
      factories.put(name, factory);
      log.debug("Registered renderer: {}", name);
    }
    if (factories.isEmpty()) {
      log.warn("No renderer factories found on classpath");
    }
  }

  /**
   * Get a renderer factory by name.
   *
   * @param name the renderer name (e.g., "JavaFX", "WebGL/Canvas2D", "Android Canvas")
   * @return the factory, or null if not found
   */
  public RendererFactory get(String name) {
    return factories.get(name);
  }

  /**
   * Get the first available renderer factory.
   *
   * @return the first factory found, or null if none available
   */
  public RendererFactory getFirst() {
    return factories.values().stream().findFirst().orElse(null);
  }

  /**
   * List all available renderer names.
   *
   * @return list of registered renderer names
   */
  public List<String> getAvailableRenderers() {
    return new ArrayList<>(factories.keySet());
  }

  /**
   * Check if a specific renderer is available.
   *
   * @param name the renderer name
   * @return true if registered
   */
  public boolean isAvailable(String name) {
    return factories.containsKey(name);
  }
}
