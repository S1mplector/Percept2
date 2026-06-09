import re

file_path = "modules/editor/src/main/java/com/jvn/editor/ui/GameBuildPublisherView.java"
with open(file_path, "r") as f:
    lines = f.readlines()

def find_method_start(name):
    for i, line in enumerate(lines):
        if re.search(r'(?:private|static|public).*?\s+' + name + r'\s*\(', line):
            return i
    return -1

methods = [
    "loadManifest", "manifestEntryText", "validateManifest", "sameCanonical", "firstNonBlank",
    "resolveScriptFile", "addCandidate", "discoverScript", "collectScripts", "scoreScript",
    "relativeTo", "normalizeProjectPath", "normalizeScriptKey", "summarizeArtifacts",
    "formatArtifactInventory", "formatBytes", "formatTimestamp", "zipDirectory",
    "buildCliCommand", "safeToken", "safeNativeVersionToken", "shellQuote"
]

for m in methods:
    start = find_method_start(m)
    if start != -1:
        # Find end by counting braces
        brace_count = 0
        started = False
        end = start
        while end < len(lines):
            brace_count += lines[end].count('{')
            brace_count -= lines[end].count('}')
            if '{' in lines[end]:
                started = True
            if started and brace_count == 0:
                break
            end += 1
        print(f"Method {m}: {start+1} to {end+1}")
