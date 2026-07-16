package com.jvn.plugin.api;

import com.jvn.plugin.api.asset.AssetImporter;
import com.jvn.plugin.api.editor.EditorTool;
import com.jvn.plugin.api.runtime.RuntimeListener;
import com.jvn.plugin.api.script.ScriptCommand;

/** Supported extension families. Access is checked against manifest capabilities. */
public interface PluginRegistries {
  /** Returns commands; requires {@code script.command}.
   * @return owned registry
   */
  ExtensionRegistry<ScriptCommand> scriptCommands();
  /** Returns tools; requires {@code editor.tool}.
   * @return owned registry
   */
  ExtensionRegistry<EditorTool> editorTools();
  /** Returns importers; requires {@code asset.importer}.
   * @return owned registry
   */
  ExtensionRegistry<AssetImporter> assetImporters();
  /** Returns listeners; requires {@code runtime.listener}.
   * @return owned registry
   */
  ExtensionRegistry<RuntimeListener> runtimeListeners();
}
