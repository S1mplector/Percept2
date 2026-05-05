import sys
import re

# 1. Update CssIcon.java
css_file = "editor/src/main/java/com/jvn/editor/ui/CssIcon.java"
with open(css_file, "r") as f:
    css_content = f.read()

with open("generated_icons.txt", "r") as f:
    generated_code = f.read()

# Insert the generated code right before the last closing brace
last_brace_index = css_content.rfind("}")
css_content = css_content[:last_brace_index] + "\n" + generated_code + "\n" + css_content[last_brace_index:]

with open(css_file, "w") as f:
    f.write(css_content)

# 2. Update PuppeteerWindow.java
window_file = "editor/src/main/java/com/jvn/editor/ui/actioneditor/PuppeteerWindow.java"
with open(window_file, "r") as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    # Transport
    if "btnRewind = makeToolbarIconButton(" in line:
        line = line.replace("CssIcon.undo()", "CssIcon.skipPrevious()")
    elif "btnPause = makeToolbarIconButton(" in line:
        line = line.replace("CssIcon.stop()", "CssIcon.pause()")
    # Timeline
    elif "cbLoop = makeToolbarIconToggle(" in line:
        line = line.replace("CssIcon.redo()", "CssIcon.loop()")
    elif "btnLoopIn = makeToolbarIconButton(" in line:
        line = line.replace("CssIcon.arrowDown()", "CssIcon.verticalAlignBottom()")
    elif "btnLoopOut = makeToolbarIconButton(" in line:
        line = line.replace("CssIcon.arrowUp()", "CssIcon.verticalAlignTop()")
    # Keyframes
    elif "btnPasteKeyframes = makeToolbarIconButton(" in line:
        line = line.replace("CssIcon.copy()", "CssIcon.contentPaste()")
    elif "btnDuplicateKeyframes = makeToolbarIconButton(" in line:
        line = line.replace("CssIcon.copy()", "CssIcon.controlPointDuplicate()")
    elif "btnSaveClip = makeToolbarIconButton(" in line:
        line = line.replace("CssIcon.save()", "CssIcon.libraryAdd()")
    elif "btnLoadClip = makeToolbarIconButton(" in line:
        line = line.replace("CssIcon.folder()", "CssIcon.input()")
    elif "slotButton = makeToolbarIconButton(" in line:
        line = line.replace("CssIcon.rectSelect()", "CssIcon.emojiPeople()")
    elif "btnZoomFit = makeToolbarIconButton(" in line:
        line = line.replace("CssIcon.rectSelect()", "CssIcon.zoomOutMap()")
    elif "btnFocusSelection = makeToolbarIconButton(" in line:
        line = line.replace("CssIcon.search()", "CssIcon.myLocation()")
    elif "btnPrevKeyframe = makeToolbarIconButton(" in line:
        line = line.replace("CssIcon.arrowDown()", "CssIcon.fastRewind()")
    elif "btnNextKeyframe = makeToolbarIconButton(" in line:
        line = line.replace("CssIcon.arrowUp()", "CssIcon.fastForward()")
    elif "cbRipple = makeToolbarIconToggle(" in line:
        line = line.replace("CssIcon.auto()", "CssIcon.wrapText()")
    elif "btnDistributeKeys = makeToolbarIconButton(" in line:
        line = line.replace("CssIcon.auto()", "CssIcon.formatAlignJustify()")
    elif "btnReverseKeys = makeToolbarIconButton(" in line:
        line = line.replace("CssIcon.undo()", "CssIcon.swapHoriz()")
    elif "btnStretchKeys = makeToolbarIconButton(" in line:
        line = line.replace("CssIcon.expand()", "CssIcon.openInFull()")
    elif "btnCompressKeys = makeToolbarIconButton(" in line:
        line = line.replace("CssIcon.minus()", "CssIcon.closeFullscreen()")
    elif "cbCompactExport = makeToolbarIconToggle(" in line:
        line = line.replace("CssIcon.save()", "CssIcon.folderZip()")
    # Snap
    elif "cbSnap = makeToolbarIconToggle(" in line:
        line = line.replace("CssIcon.auto()", "CssIcon.grid4x4()")
    # Preview
    elif "cbAutoKey = makeToolbarIconToggle(" in line:
        line = line.replace("CssIcon.auto()", "CssIcon.fiberSmartRecord()")
    elif "cbSnapGrid = makeToolbarIconToggle(" in line:
        line = line.replace("CssIcon.auto()", "CssIcon.borderAll()")
    elif "cbSnapEntity = makeToolbarIconToggle(" in line:
        line = line.replace("CssIcon.auto()", "CssIcon.joinInner()")
    # Orbit
    elif "cbOrbitTool = makeToolbarIconToggle(" in line:
        line = line.replace("CssIcon.auto()", "CssIcon.threeSixty()")
    elif "cbOrbitAlign = makeToolbarIconToggle(" in line:
        line = line.replace("CssIcon.auto()", "CssIcon.explore()")
        
    new_lines.append(line)

with open(window_file, "w") as f:
    f.writelines(new_lines)

