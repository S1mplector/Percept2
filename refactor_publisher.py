import re

file_path = "modules/editor/src/main/java/com/jvn/editor/ui/GameBuildPublisherView.java"
with open(file_path, "r") as f:
    content = f.read()

# Add imports
imports = """import com.jvn.editor.ui.build.ProjectManifestService;
import com.jvn.editor.ui.build.BuildArtifactService;
import com.jvn.editor.ui.build.BuildCliFormatter;
"""
if "import com.jvn.editor.ui.build.ProjectManifestService;" not in content:
    content = re.sub(r'import java.util.function.Consumer;\n', r'import java.util.function.Consumer;\n' + imports, content)

# Replace method calls
content = content.replace("loadManifest(", "ProjectManifestService.loadManifest(")
content = content.replace("manifestEntryText(", "ProjectManifestService.manifestEntryText(")
content = content.replace("validateManifest(", "ProjectManifestService.validateManifest(")
content = content.replace("sameCanonical(", "ProjectManifestService.sameCanonical(")
content = content.replace("firstNonBlank(", "ProjectManifestService.firstNonBlank(")

content = content.replace("summarizeArtifacts(", "BuildArtifactService.summarizeArtifacts(")
content = content.replace("formatArtifactInventory(", "BuildArtifactService.formatArtifactInventory(")
content = content.replace("formatBytes(", "BuildArtifactService.formatBytes(")
content = content.replace("zipDirectory(", "BuildArtifactService.zipDirectory(")

content = content.replace("buildCliCommand(", "BuildCliFormatter.buildCliCommand(")
content = content.replace("safeToken(", "BuildCliFormatter.safeToken(")
content = content.replace("safeNativeVersionToken(", "BuildCliFormatter.safeNativeVersionToken(")

# Remove methods by counting braces
methods_to_remove = [
    r'(?:private|static|public).*?\s+loadManifest\s*\(',
    r'(?:private|static|public).*?\s+manifestEntryText\s*\(',
    r'(?:private|static|public).*?\s+validateManifest\s*\(',
    r'(?:private|static|public).*?\s+sameCanonical\s*\(',
    r'(?:private|static|public).*?\s+firstNonBlank\s*\(',
    r'(?:private|static|public).*?\s+resolveScriptFile\s*\(',
    r'(?:private|static|public).*?\s+addCandidate\s*\(',
    r'(?:private|static|public).*?\s+discoverScript\s*\(',
    r'(?:private|static|public).*?\s+collectScripts\s*\(',
    r'(?:private|static|public).*?\s+scoreScript\s*\(',
    r'(?:private|static|public).*?\s+relativeTo\s*\(',
    r'(?:private|static|public).*?\s+normalizeProjectPath\s*\(',
    r'(?:private|static|public).*?\s+normalizeScriptKey\s*\(',
    r'(?:private|static|public).*?\s+summarizeArtifacts\s*\(',
    r'(?:private|static|public).*?\s+formatArtifactInventory\s*\(',
    r'(?:private|static|public).*?\s+formatBytes\s*\(',
    r'(?:private|static|public).*?\s+formatTimestamp\s*\(',
    r'(?:private|static|public).*?\s+zipDirectory\s*\(',
    r'(?:private|static|public).*?\s+buildCliCommand\s*\(',
    r'(?:private|static|public).*?\s+safeToken\s*\(',
    r'(?:private|static|public).*?\s+safeNativeVersionToken\s*\(',
    r'(?:private|static|public).*?\s+shellQuote\s*\('
]

lines = content.split('\n')
new_lines = []
i = 0
while i < len(lines):
    line = lines[i]
    
    # Check if line matches any method to remove
    matched = False
    for m in methods_to_remove:
        if re.search(m, line) and "ProjectManifestService" not in line and "BuildArtifactService" not in line and "BuildCliFormatter" not in line:
            # We found a method definition. Read until brace count is 0
            # Wait, if the brace is not on the same line, we need to handle it.
            matched = True
            break
            
    if matched:
        brace_count = 0
        started = False
        while i < len(lines):
            brace_count += lines[i].count('{')
            brace_count -= lines[i].count('}')
            if '{' in lines[i]:
                started = True
            if started and brace_count == 0:
                break
            i += 1
    else:
        new_lines.append(line)
    i += 1

with open(file_path, "w") as f:
    f.write('\n'.join(new_lines))

