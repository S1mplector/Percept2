package com.jvn.plugin.runtime;

import com.jvn.plugin.api.PluginDescriptor;
import java.nio.file.Path;

public record LoadedPlugin(PluginDescriptor descriptor, PluginState state, Path source, String failure) {}
