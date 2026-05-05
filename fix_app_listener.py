import sys

with open("editor/src/main/java/com/jvn/editor/EditorApp.java") as f:
    lines = f.readlines()

new_lines = []

for i, line in enumerate(lines):
    new_lines.append(line)
    if "puppeteer.show();" in line and "if (snapshot.currentLabel != null)" in lines[i-4]:
        new_lines.append("    if (puppeteerLauncherPanel != null && preferredTimelineName != null && !preferredTimelineName.isBlank()) {\n")
        new_lines.append("      puppeteerLauncherPanel.setActiveEditingTimeline(preferredTimelineName);\n")
        new_lines.append("      puppeteer.showingProperty().addListener((obs, oldVal, newVal) -> {\n")
        new_lines.append("        if (!newVal && puppeteerLauncherPanel != null) {\n")
        new_lines.append("          puppeteerLauncherPanel.setActiveEditingTimeline(null);\n")
        new_lines.append("        }\n")
        new_lines.append("      });\n")
        new_lines.append("    }\n")

with open("editor/src/main/java/com/jvn/editor/EditorApp.java", "w") as f:
    f.writelines(new_lines)
