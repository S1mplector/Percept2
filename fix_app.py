import sys

with open("editor/src/main/java/com/jvn/editor/EditorApp.java") as f:
    lines = f.readlines()

new_lines = []
skip = 0

for i, line in enumerate(lines):
    if skip > 0:
        skip -= 1
        continue
    
    if "puppeteer.show();" in line and "preferredTimelineName" not in lines[i-1]:
        # Need to check if it's the second occurrence
        if "resolvePuppeteerLaunchScene(ft, imported, null);" in lines[i-4]:
            new_lines.append(line)
            # We skip the added lines
            skip = 7
            continue
            
    new_lines.append(line)

with open("editor/src/main/java/com/jvn/editor/EditorApp.java", "w") as f:
    f.writelines(new_lines)
