import sys

# 1. Update CssIcon.java
css_icon_file = "editor/src/main/java/com/jvn/editor/ui/CssIcon.java"
with open(css_icon_file, "r") as f:
    css_content = f.read()

new_paths = """  private static final String PATH_SPARKLES = "M19 8.3q-.125 0-.262-.075Q18.6 8.15 18.55 8l-.8-1.75-1.75-.8q-.15-.05-.225-.188Q15.7 5.125 15.7 5t.075-.263Q15.85 4.6 16 4.55l1.75-.8.8-1.75q.05-.15.188-.225.137-.075.262-.075t.263.075q.137.075.187.225l.8 1.75 1.75.8q.15.05.225.187.075.138.075.263t-.075.262Q22.15 5.4 22 5.45l-1.75.8-.8 1.75q-.05.15-.187.225-.138.075-.263.075Zm0 14q-.125 0-.262-.075-.138-.075-.188-.225l-.8-1.75-1.75-.8q-.15-.05-.225-.188-.075-.137-.075-.262t.075-.262q.075-.138.225-.188l1.75-.8.8-1.75q.05-.15.188-.225.137-.075.262-.075t.263.075q.137.075.187.225l.8 1.75 1.75.8q.15.05.225.188.075.137.075.262t-.075.262q-.075.138-.225.188l-1.75.8-.8 1.75q-.05.15-.187.225-.138.075-.263.075ZM9 18.575q-.275 0-.525-.15T8.1 18l-1.6-3.5L3 12.9q-.275-.125-.425-.375-.15-.25-.15-.525t.15-.525q.15-.25.425-.375l3.5-1.6L8.1 6q.125-.275.375-.425.25-.15.525-.15t.525.15q.25.15.375.425l1.6 3.5 3.5 1.6q.275.125.425.375.15.25.15.525t-.15.525q-.15.25-.425.375l-3.5 1.6L9.9 18q-.125.275-.375.425-.25.15-.525.15Zm0-3.425L10 13l2.15-1L10 11 9 8.85 8 11l-2.15 1L8 13ZM9 12Z";
  private static final String PATH_ROBOT = "M20 9V7c0-1.1-.9-2-2-2h-3c0-1.66-1.34-3-3-3S9 3.34 9 5H6c-1.1 0-2 .9-2 2v2c-1.66 0-3 1.34-3 3s1.34 3 3 3v4c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2v-4c1.66 0 3-1.34 3-3s-1.34-3-3-3zM7.5 11.5c0-.83.67-1.5 1.5-1.5s1.5.67 1.5 1.5S9.83 13 9 13s-1.5-.67-1.5-1.5zM15 17H9c-.55 0-1-.45-1-1s.45-1 1-1h6c.55 0 1 .45 1 1s-.45 1-1 1zm0-4c-.83 0-1.5-.67-1.5-1.5S14.17 10 15 10s1.5.67 1.5 1.5S15.83 13 15 13z";
  private static final String PATH_LIGHTBULB = "M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm-3-3h6c.55 0 1-.45 1-1s-.45-1-1-1H9c-.55 0-1 .45-1 1s.45 1 1 1zm3-17C7.86 2 4.5 5.36 4.5 9.5c0 3.82 2.66 5.86 3.77 6.5h7.46c1.11-.64 3.77-2.68 3.77-6.5C19.5 5.36 16.14 2 12 2z";
"""

new_methods_with_color = """  public static Region sparkles(String color) { return icon(PATH_SPARKLES, color, 14); }
  public static Region robot(String color) { return icon(PATH_ROBOT, color, 14); }
  public static Region lightbulb(String color) { return icon(PATH_LIGHTBULB, color, 14); }
"""

new_methods_no_color = """  public static Region sparkles() { return sparkles("#b0b8c8"); }
  public static Region robot() { return robot("#b0b8c8"); }
  public static Region lightbulb() { return lightbulb("#b0b8c8"); }
"""

# Insert paths
if "PATH_SPARKLES" not in css_content:
    css_content = css_content.replace('private static final String PATH_PLUS', new_paths + '  private static final String PATH_PLUS')

# Insert color methods
if "public static Region sparkles(String color)" not in css_content:
    css_content = css_content.replace('public static Region plus(String color)', new_methods_with_color + '  public static Region plus(String color)')

# Insert no-color methods
if "public static Region sparkles()" not in css_content:
    css_content = css_content.replace('public static Region plus()', new_methods_no_color + '  public static Region plus()')

with open(css_icon_file, "w") as f:
    f.write(css_content)

# 2. Update EditorApp.java
app_file = "editor/src/main/java/com/jvn/editor/EditorApp.java"
with open(app_file, "r") as f:
    app_content = f.read()

app_content = app_content.replace('return com.jvn.editor.ui.CssIcon.palette("#f6a2c8");', 'return com.jvn.editor.ui.CssIcon.lightbulb("#f6a2c8");')
app_content = app_content.replace('return com.jvn.editor.ui.CssIcon.rocket("#ff9f3d");', 'return com.jvn.editor.ui.CssIcon.sparkles("#ff9f3d");')
app_content = app_content.replace('return com.jvn.editor.ui.CssIcon.movie("#f0a0d0");', 'return com.jvn.editor.ui.CssIcon.robot("#f0a0d0");')

with open(app_file, "w") as f:
    f.write(app_content)

