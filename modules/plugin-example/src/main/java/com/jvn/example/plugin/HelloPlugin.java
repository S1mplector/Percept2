package com.jvn.example.plugin;

import com.jvn.plugin.api.JvnPlugin;
import com.jvn.plugin.api.PluginContext;
import com.jvn.plugin.api.editor.EditorTool;
import com.jvn.plugin.api.editor.EditorToolContext;
import com.jvn.plugin.api.runtime.RuntimeEvent;
import com.jvn.plugin.api.runtime.RuntimeListener;
import com.jvn.plugin.api.script.ScriptCommandResult;

/** Small, intentionally framework-neutral reference plugin. */
public final class HelloPlugin implements JvnPlugin {
  private PluginContext context;

  @Override
  public void initialize(PluginContext context) {
    this.context = context;
    context.registries().scriptCommands().register("hello.greet", invocation -> {
      String name = invocation.arguments().isEmpty() ? "world" : invocation.arguments().get(0);
      return new ScriptCommandResult(true, "Hello, " + name + "!", java.util.Map.of("plugin.lastGreeting", name));
    });
    context.registries().editorTools().register("hello.info", new EditorTool() {
      @Override public String label() { return "Hello Plugin Information"; }
      @Override public String description() { return "Writes plugin and project information to the editor log."; }
      @Override public void open(EditorToolContext toolContext) {
        context.logger().info("Hello from {}. Project: {}", context.descriptor().name(), toolContext.projectDirectory());
      }
    });
    context.registries().runtimeListeners().register("hello.lifecycle", new RuntimeListener() {
      @Override public void onRuntimeStarted(RuntimeEvent event) {
        context.logger().info("Hello plugin started in JVN {}", event.jvnVersion());
      }
      @Override public void onRuntimeStopping(RuntimeEvent event) {
        context.logger().info("Hello plugin stopping");
      }
    });
  }

  @Override public void start() { context.logger().info("{} is ready", context.descriptor().name()); }
}
