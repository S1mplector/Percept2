import re

with open("editor/src/main/java/com/jvn/editor/ui/CssIcon.java") as f:
    lines = f.readlines()

new_lines = []
seen_paths = set()
seen_methods = set()

for line in lines:
    if "private static final String PATH_" in line:
        path_name = re.search(r'PATH_([A-Z_]+)', line).group(1)
        if path_name in seen_paths:
            continue
        seen_paths.add(path_name)
    elif "public static Region" in line:
        method_sig = line.split('{')[0].strip()
        if method_sig in seen_methods:
            continue
        seen_methods.add(method_sig)
    
    new_lines.append(line)

with open("editor/src/main/java/com/jvn/editor/ui/CssIcon.java", "w") as f:
    f.writelines(new_lines)
