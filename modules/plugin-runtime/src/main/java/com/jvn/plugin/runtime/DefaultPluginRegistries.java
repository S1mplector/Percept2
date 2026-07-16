package com.jvn.plugin.runtime;

import com.jvn.plugin.api.ExtensionRegistry;
import com.jvn.plugin.api.PluginRegistries;
import com.jvn.plugin.api.PluginCapability;
import com.jvn.plugin.api.PluginDescriptor;
import com.jvn.plugin.api.asset.AssetImporter;
import com.jvn.plugin.api.editor.EditorTool;
import com.jvn.plugin.api.runtime.RuntimeListener;
import com.jvn.plugin.api.script.ScriptCommand;

public final class DefaultPluginRegistries {
  private final OwnedExtensionRegistry<ScriptCommand> commands = new OwnedExtensionRegistry<>();
  private final OwnedExtensionRegistry<EditorTool> tools = new OwnedExtensionRegistry<>();
  private final OwnedExtensionRegistry<AssetImporter> importers = new OwnedExtensionRegistry<>();
  private final OwnedExtensionRegistry<RuntimeListener> listeners = new OwnedExtensionRegistry<>();

  PluginRegistries forPlugin(PluginDescriptor descriptor) {
    String pluginId = descriptor.id();
    return new PluginRegistries() {
      @Override public ExtensionRegistry<ScriptCommand> scriptCommands() { require(PluginCapability.SCRIPT_COMMAND); return commands.forPlugin(pluginId); }
      @Override public ExtensionRegistry<EditorTool> editorTools() { require(PluginCapability.EDITOR_TOOL); return tools.forPlugin(pluginId); }
      @Override public ExtensionRegistry<AssetImporter> assetImporters() { require(PluginCapability.ASSET_IMPORTER); return importers.forPlugin(pluginId); }
      @Override public ExtensionRegistry<RuntimeListener> runtimeListeners() { require(PluginCapability.RUNTIME_LISTENER); return listeners.forPlugin(pluginId); }
      private void require(PluginCapability capability) {
        if (!descriptor.capabilities().contains(capability)) {
          throw new IllegalStateException("Plugin " + pluginId + " did not declare capability " + capability.id());
        }
      }
    };
  }

  public PluginRegistries view() {
    return new PluginRegistries() {
      @Override public ExtensionRegistry<ScriptCommand> scriptCommands() { return commands.forPlugin("host"); }
      @Override public ExtensionRegistry<EditorTool> editorTools() { return tools.forPlugin("host"); }
      @Override public ExtensionRegistry<AssetImporter> assetImporters() { return importers.forPlugin("host"); }
      @Override public ExtensionRegistry<RuntimeListener> runtimeListeners() { return listeners.forPlugin("host"); }
    };
  }

  void removePlugin(String pluginId) {
    commands.removePlugin(pluginId);
    tools.removePlugin(pluginId);
    importers.removePlugin(pluginId);
    listeners.removePlugin(pluginId);
  }
}
