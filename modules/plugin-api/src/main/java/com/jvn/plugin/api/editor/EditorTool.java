package com.jvn.plugin.api.editor;

/**
 * Platform-neutral editor action displayed under {@code Tools -> Plugins}.
 *
 * <p>{@link #open(EditorToolContext)} runs on the JavaFX application thread. Expensive work should
 * be moved to a bounded background executor and UI updates returned to that thread.</p>
 */
public interface EditorTool {
  /** Supplies the menu label.
   * @return concise label
   */
  String label();
  /** Describes the action.
   * @return optional explanation
   */
  default String description() { return ""; }
  /** Supplies a future logical location.
   * @return menu path
   */
  default String menuPath() { return "Tools/Plugins"; }
  /** Opens the tool.
   * @param context editor context
   * @throws Exception to report failure
   */
  void open(EditorToolContext context) throws Exception;
}
