# Authoring your first plugin

This guide creates a plugin with a VNS command and an editor action.

## 1. Create the project

Use Java 21 and compile against the JVN Plugin API. During development inside this repository:

```kotlin
plugins { java }

dependencies {
  compileOnly(project(":plugin-api"))
}
```

Use `compileOnly`: JVN supplies the API at runtime, and bundling a second copy can break type identity.

## 2. Add the manifest

Create `src/main/resources/jvn-plugin.json`:

```json
{
  "id": "dev.example.greeter",
  "name": "Greeter",
  "version": "1.0.0",
  "jvnApi": "1.x",
  "entrypoint": "dev.example.greeter.GreeterPlugin",
  "vendor": "Example Studio",
  "capabilities": ["script.command", "editor.tool"]
}
```

Capabilities are enforced. Accessing an undeclared registry fails initialization and removes registrations already created by that plugin.

## 3. Implement the entry point

```java
public final class GreeterPlugin implements JvnPlugin {
  @Override
  public void initialize(PluginContext context) {
    context.registries().scriptCommands().register("greeter.hello", invocation -> {
      String name = invocation.arguments().isEmpty() ? "world" : invocation.arguments().get(0);
      context.logger().info("Hello, {}", name);
      return ScriptCommandResult.handled("Hello, " + name);
    });

    context.registries().editorTools().register("greeter.open", new EditorTool() {
      public String label() { return "Run Greeter"; }
      public void open(EditorToolContext editor) {
        context.logger().info("Greeter opened for {}", editor.projectDirectory());
      }
    });
  }
}
```

Registration IDs should be namespaced and stable. Changing one is a breaking change for scripts or settings that refer to it.

## 4. Build and install

```bash
./gradlew jar
mkdir -p ~/.jvn/plugins
cp build/libs/greeter-1.0.0.jar ~/.jvn/plugins/
```

For a project-local extension, copy the JAR into `<project>/plugins/`. Open the project again so the editor reloads its plugin host.

The safer installation path is **Engine Hub → Engine → Plugins → Manage Plugins**. It verifies the
manifest, Plugin API range, and entrypoint class presence without starting plugin code, then stages
and atomically installs the verified JAR into the user or current-project plugin folder. The manager
also reports duplicate IDs and missing dependencies, shows a SHA-256 fingerprint, and can disable a
bundle without deleting it. The editor performs the full entrypoint-type and constructor preflight
when it reloads the project.

## 5. Use it

Editor tools appear under **Tools → Plugins**. Script commands use the plugin provider followed by the registered command and arguments:

```vns
[plugin greeter.hello "Ada Lovelace"]
```

Returned variable updates are copied into VNS state.

## 6. Test it

Keep plugin business logic independent from JavaFX. Unit-test handlers directly, then test host behavior using `PluginHost` with a `BundledPluginProvider`. Verify manifests, capabilities, configuration defaults, lifecycle cleanup, malformed arguments, and resources released from `stop()`.

The repository tests in `modules/plugin-runtime/src/test` demonstrate dependency ordering, cleanup, capability enforcement, bundle verification, and failure isolation.

## Add an animation easing

Plugin API 1.1 adds the fluent contribution surface:

```java
context.contribute().animations().easing("greeter.friendly-pop",
    AnimationEasingDefinition.easing("Friendly Pop")
        .parameter("strength", 1.0, AnimationEasingDefinition.range(0.0, 2.0))
        .evaluate(frame -> frame.progress()
            + Math.sin(frame.progress() * Math.PI) * frame.parameter("strength") * 0.15));
```

Add `animation.easing` to the manifest capabilities and set `jvnApi` to `>=1.1.0 <2.0.0`. See [Animation Easing Extensions](animation-extensions.md) for metadata, parameter validation, timeline syntax, editor discovery, testing, and performance rules.
