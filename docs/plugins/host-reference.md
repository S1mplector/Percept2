# Plugin host and lifecycle

## Discovery

The shared host checks user plugins, project plugins, explicitly configured directories, and bundled `ServiceLoader` providers. JARs are processed in filename order; identity and dependencies determine activation order.

Set `-Djvn.plugins.disabled=true` to start JVN without loading plugins during recovery or diagnosis.

Bundled applications implement `BundledPluginProvider` and register its class in:

```text
META-INF/services/com.jvn.plugin.api.BundledPluginProvider
```

## Lifecycle

```text
discover → validate → dependency order → initialize → start
                                                   ↓
                                      listeners and extensions
                                                   ↓
                          stop in reverse dependency order
```

`initialize(context)` registers extensions and reads configuration. `start()` begins work after valid plugins initialize. `stop()` releases resources; the host then removes registrations and closes external JAR classloaders.

Initialization or startup failure marks only that plugin failed. Other independent plugins continue. Missing, cyclic, and incompatible dependencies prevent affected plugins from initializing.

## Data and configuration

Each plugin receives `~/.jvn/plugin-data/<plugin-id>/`. If `config.properties` exists there, the host exposes an immutable key/value map through `PluginContext.configuration()`.

## Diagnostics

`PluginHost.diagnostics()` reports severity, plugin ID, stable code, message, and cause. Important codes include `jar-load`, `duplicate-id`, `api-incompatible`, dependency errors, lifecycle failures, and listener failures.

`PluginHost.plugins()` exposes descriptor, state, source JAR, and failure text for management UI.
