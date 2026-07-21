package com.jvn.plugin.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.plugin.api.BundledPluginProvider;
import com.jvn.plugin.api.JvnPlugin;
import com.jvn.plugin.api.PluginCapability;
import com.jvn.plugin.api.PluginContext;
import com.jvn.plugin.api.PluginDependency;
import com.jvn.plugin.api.PluginDescriptor;
import com.jvn.plugin.api.PluginEnvironment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static com.jvn.plugin.api.animation.AnimationEasingDefinition.easing;

class PluginHostTest {
  @TempDir Path temp;

  @Test
  void ordersDependenciesAndRemovesOwnedExtensionsAtShutdown() {
    List<String> calls = new ArrayList<>();
    PluginHost host = host();
    host.addBundled(provider(descriptor("base", List.of(), Set.of()), plugin("base", calls, false)));
    host.addBundled(provider(
        descriptor("feature", List.of(new PluginDependency("base", "1.x")), Set.of(PluginCapability.SCRIPT_COMMAND)),
        new JvnPlugin() {
          @Override public void initialize(PluginContext context) {
            calls.add("feature:init");
            context.registries().scriptCommands().register("feature.echo", invocation ->
                com.jvn.plugin.api.script.ScriptCommandResult.handled(String.join(" ", invocation.arguments())));
          }
          @Override public void start() { calls.add("feature:start"); }
          @Override public void stop() { calls.add("feature:stop"); }
        }));

    host.start();
    assertEquals(List.of("base:init", "feature:init", "base:start", "feature:start"), calls);
    assertTrue(host.registries().scriptCommands().find("feature.echo").isPresent());
    host.close();
    assertFalse(host.registries().scriptCommands().find("feature.echo").isPresent());
    assertEquals("base:stop", calls.get(calls.size() - 1));
  }

  @Test
  void isolatesInitializationFailureAndRejectsUndeclaredCapability() {
    PluginHost host = host();
    host.addBundled(provider(descriptor("broken", List.of(), Set.of()), new JvnPlugin() {
      @Override public void initialize(PluginContext context) {
        context.registries().editorTools();
      }
    }));
    host.start();
    assertEquals(PluginState.FAILED, host.plugins().get(0).state());
    assertTrue(host.diagnostics().stream().anyMatch(d -> d.code().equals("initialize-failed")));
    host.close();
  }

  @Test
  void reportsMissingDependenciesWithoutStartingPlugin() {
    PluginHost host = host();
    host.addBundled(provider(descriptor("feature", List.of(new PluginDependency("missing", "*")), Set.of()),
        plugin("feature", new ArrayList<>(), false)));
    host.start();
    assertTrue(host.diagnostics().stream().anyMatch(d -> d.code().equals("dependency-missing")));
    assertEquals(PluginState.DISCOVERED, host.plugins().get(0).state());
  }

  @Test
  void ownsFluentAnimationContributionsAndCleansThemUp() {
    PluginHost host = host();
    host.addBundled(provider(
        descriptor("motion", List.of(), Set.of(PluginCapability.ANIMATION_EASING)),
        new JvnPlugin() {
          @Override public void initialize(PluginContext context) {
            context.contribute().animations().easing("motion.smooth",
                easing("Smooth").evaluate(frame -> frame.progress() * frame.progress()));
          }
        }));

    host.start();
    assertEquals("Smooth", host.registries().animationEasings()
        .find("motion.smooth").orElseThrow().label());
    host.close();
    assertTrue(host.registries().animationEasings().entries().isEmpty());
  }

  @Test
  void rejectsUnqualifiedAnimationIdsDuringInitialization() {
    PluginHost host = host();
    host.addBundled(provider(
        descriptor("motion", List.of(), Set.of(PluginCapability.ANIMATION_EASING)),
        new JvnPlugin() {
          @Override public void initialize(PluginContext context) {
            context.contribute().animations().easing("smooth",
                easing("Smooth").evaluate(frame -> frame.progress()));
          }
        }));
    host.start();
    assertEquals(PluginState.FAILED, host.plugins().get(0).state());
    assertTrue(host.diagnostics().stream().anyMatch(d -> d.code().equals("initialize-failed")));
  }

  private PluginHost host() {
    return PluginHost.builder(PluginEnvironment.TEST).userDataDirectory(temp.resolve("data")).build();
  }

  private static PluginDescriptor descriptor(String id, List<PluginDependency> dependencies, Set<PluginCapability> capabilities) {
    return new PluginDescriptor(id, id, "1.0.0", "1.x", "test." + id, "", "", dependencies, capabilities);
  }

  private static JvnPlugin plugin(String id, List<String> calls, boolean fail) {
    return new JvnPlugin() {
      @Override public void initialize(PluginContext context) {
        calls.add(id + ":init");
        if (fail) throw new IllegalStateException("failed");
      }
      @Override public void start() { calls.add(id + ":start"); }
      @Override public void stop() { calls.add(id + ":stop"); }
    };
  }

  private static BundledPluginProvider provider(PluginDescriptor descriptor, JvnPlugin plugin) {
    return new BundledPluginProvider() {
      @Override public PluginDescriptor descriptor() { return descriptor; }
      @Override public JvnPlugin create() { return plugin; }
    };
  }
}
