import sys

# 1. Update CssIcon.java
css_icon_file = "editor/src/main/java/com/jvn/editor/ui/CssIcon.java"
with open(css_icon_file, "r") as f:
    css_content = f.read()

theater_path = "M21 2h-8c-1.1 0-2 .9-2 2v3.5h1.5c1.1 0 2 .9 2 2v4.95c1.04.48 2.24.68 3.5.47 2.93-.49 5-3.17 5-6.14V4c0-1.1-.9-2-2-2zm-7 4.5c0-.55.45-1 1-1s1 .45 1 1-.45 1-1 1-1-.45-1-1zm4.85 4.38h-3.72c-.38 0-.63-.41-.44-.75.39-.66 1.27-1.13 2.3-1.13s1.91.47 2.3 1.14c.19.33-.06.74-.44.74zM19 7.5c-.55 0-1-.45-1-1s.45-1 1-1 1 .45 1 1-.45 1-1 1z M11 9H3c-1.1 0-2 .9-2 2v4.79c0 3.05 2.19 5.77 5.21 6.16C9.87 22.42 13 19.57 13 16v-5c0-1.1-.9-2-2-2zm-7 4.5c0-.55.45-1 1-1s1 .45 1 1-.45 1-1 1-1-.45-1-1zm5.3 3.25c-.38.67-1.27 1.14-2.3 1.14s-1.91-.47-2.3-1.14c-.19-.34.06-.75.44-.75h3.72c.38 0 .63.41.44.75zM9 14.5c-.55 0-1-.45-1-1s.45-1 1-1 1 .45 1 1-.45 1-1 1z"

# replace PATH_ROBOT definition with PATH_THEATER
if "PATH_THEATER" not in css_content:
    css_content = css_content.replace('private static final String PATH_ROBOT =', 'private static final String PATH_THEATER = "' + theater_path + '";\n  private static final String PATH_ROBOT =')

if "public static Region theater(" not in css_content:
    css_content = css_content.replace('public static Region robot(String color)', 'public static Region theater(String color) { return icon(PATH_THEATER, color, 14); }\n  public static Region robot(String color)')
    css_content = css_content.replace('public static Region robot()', 'public static Region theater() { return theater("#b0b8c8"); }\n  public static Region robot()')

with open(css_icon_file, "w") as f:
    f.write(css_content)

# 2. Update EditorApp.java to use theater instead of robot
app_file = "editor/src/main/java/com/jvn/editor/EditorApp.java"
with open(app_file, "r") as f:
    app_content = f.read()

app_content = app_content.replace('.robot("#f0a0d0");', '.theater("#f0a0d0");')
with open(app_file, "w") as f:
    f.write(app_content)

# 3. Update ProjectExplorerView.java
project_file = "editor/src/main/java/com/jvn/editor/ui/ProjectExplorerView.java"
with open(project_file, "r") as f:
    project_content = f.read()

# Fields
fields_injection = """  private final TextField filter = new TextField();
  private final javafx.scene.layout.StackPane treeContainer = new javafx.scene.layout.StackPane();
  private final VBox emptyStateBox = new VBox(8);"""
project_content = project_content.replace('  private final TextField filter = new TextField();', fields_injection)

# Constructor modifications
constructor_search = """    tree.setShowRoot(true);
    tree.getStyleClass().add("project-explorer-tree");
    VBox.setVgrow(tree, Priority.ALWAYS);"""
constructor_replace = """    tree.setShowRoot(true);
    tree.getStyleClass().add("project-explorer-tree");
    
    Label emptyTitle = new Label("No project selected");
    emptyTitle.getStyleClass().add("sidebar-empty-title");
    Label emptyMessage = new Label("Open a project folder or create a new one to see files here.");
    emptyMessage.setWrapText(true);
    emptyMessage.setStyle("-fx-text-fill: #999; -fx-text-alignment: center;");
    emptyStateBox.getChildren().addAll(emptyTitle, emptyMessage);
    emptyStateBox.setAlignment(Pos.CENTER);
    emptyStateBox.setPadding(new Insets(16));
    
    treeContainer.getChildren().add(emptyStateBox);
    VBox.setVgrow(treeContainer, Priority.ALWAYS);"""
project_content = project_content.replace(constructor_search, constructor_replace)

children_add_search = "getChildren().addAll(header, filter, tree);"
children_add_replace = "getChildren().addAll(header, filter, treeContainer);"
project_content = project_content.replace(children_add_search, children_add_replace)

# Refresh modification
refresh_search = """  public void refresh() {
    if (rootDir == null) { tree.setRoot(null); return; }"""
refresh_replace = """  public void refresh() {
    if (rootDir == null) { 
      tree.setRoot(null);
      if (!treeContainer.getChildren().contains(emptyStateBox)) {
          treeContainer.getChildren().setAll(emptyStateBox);
      }
      return; 
    } else {
      if (!treeContainer.getChildren().contains(tree)) {
          treeContainer.getChildren().setAll(tree);
      }
    }"""
project_content = project_content.replace(refresh_search, refresh_replace)

with open(project_file, "w") as f:
    f.write(project_content)

