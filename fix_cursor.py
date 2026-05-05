import sys

# 1. Update CssIcon.java
css_file = "editor/src/main/java/com/jvn/editor/ui/CssIcon.java"
with open(css_file, "r") as f:
    css_content = f.read()

path = 'M18.75 3.94 4.07 10.08c-.83.35-.81 1.53.02 1.85L9.43 14a1 1 0 0 1 .57.57l2.06 5.33c.32.84 1.51.86 1.86.03l6.15-14.67c.33-.83-.5-1.66-1.32-1.32z'

generated_code = f"""
private static final String PATH_NEAR_ME = "{path}";
public static Region nearMe() {{ return nearMe("#b0b8c8"); }}
public static Region nearMe(String color) {{ return icon(PATH_NEAR_ME, color, 14); }}
"""

last_brace_index = css_content.rfind("}")
css_content = css_content[:last_brace_index] + "\n" + generated_code + "\n" + css_content[last_brace_index:]

with open(css_file, "w") as f:
    f.write(css_content)

# 2. Update PuppeteerLauncherPanel.java
panel_file = "editor/src/main/java/com/jvn/editor/ui/PuppeteerLauncherPanel.java"
with open(panel_file, "r") as f:
    panel_content = f.read()

panel_content = panel_content.replace('CssIcon.rocket("#f0f0f0")', 'CssIcon.nearMe("#f0f0f0")')

with open(panel_file, "w") as f:
    f.write(panel_content)
