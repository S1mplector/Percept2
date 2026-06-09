import re

file_path = "modules/editor/src/main/java/com/jvn/editor/ui/GameBuildPublisherView.java"
with open(file_path, "r") as f:
    lines = f.readlines()

# Line ranges to delete (1-indexed)
ranges_to_delete = [
    (1514, 1527), (1508, 1512), (1481, 1494), (1474, 1479), (1465, 1472),
    (1457, 1463), (1448, 1455), (1435, 1446), (1424, 1433), (1412, 1422),
    (1397, 1410), (1391, 1395), (1369, 1389), (1359, 1367), (1175, 1192),
    (1050, 1055), (1038, 1048), (1012, 1036), (994, 1010), (696, 731),
    (449, 455), (435, 447)
]

# Sort descending
ranges_to_delete.sort(reverse=True)

# Delete lines
for start, end in ranges_to_delete:
    del lines[start-1 : end]

content = "".join(lines)

imports = """import com.jvn.editor.ui.build.ProjectManifestService;
import com.jvn.editor.ui.build.BuildArtifactService;
import com.jvn.editor.ui.build.BuildCliFormatter;
"""
if "import com.jvn.editor.ui.build.ProjectManifestService;" not in content:
    content = re.sub(r'import java.util.function.Consumer;\n', r'import java.util.function.Consumer;\n' + imports, content)

# Replacements
replacements = {
    "loadManifest(": "ProjectManifestService.loadManifest(",
    "manifestEntryText(": "ProjectManifestService.manifestEntryText(",
    "validateManifest(": "ProjectManifestService.validateManifest(",
    "sameCanonical(": "ProjectManifestService.sameCanonical(",
    "firstNonBlank(": "ProjectManifestService.firstNonBlank(",
    "resolveScriptFile(": "ProjectManifestService.resolveScriptFile(",
    "discoverScript(": "ProjectManifestService.discoverScript(",
    "relativeTo(": "ProjectManifestService.relativeTo(",
    "normalizeProjectPath(": "ProjectManifestService.normalizeProjectPath(",
    "normalizeScriptKey(": "ProjectManifestService.normalizeScriptKey(",
    "summarizeArtifacts(": "BuildArtifactService.summarizeArtifacts(",
    "formatArtifactInventory(": "BuildArtifactService.formatArtifactInventory(",
    "formatBytes(": "BuildArtifactService.formatBytes(",
    "formatTimestamp(": "BuildArtifactService.formatTimestamp(",
    "zipDirectory(": "BuildArtifactService.zipDirectory(",
    "buildCliCommand(": "BuildCliFormatter.buildCliCommand(",
    "safeToken(": "BuildCliFormatter.safeToken(",
    "safeNativeVersionToken(": "BuildCliFormatter.safeNativeVersionToken(",
    "shellQuote(": "BuildCliFormatter.shellQuote("
}

for old, new in replacements.items():
    content = content.replace(old, new)

with open(file_path, "w") as f:
    f.write(content)
