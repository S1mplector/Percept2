package com.jvn.hub;

import java.nio.file.Path;
import java.util.List;

record HubShortcutCommand(Path script, List<String> command) {
}
