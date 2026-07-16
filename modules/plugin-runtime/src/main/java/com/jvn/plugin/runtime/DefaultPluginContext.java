package com.jvn.plugin.runtime;

import com.jvn.plugin.api.PluginContext;
import com.jvn.plugin.api.PluginDescriptor;
import com.jvn.plugin.api.PluginEnvironment;
import com.jvn.plugin.api.PluginRegistries;
import java.nio.file.Path;
import java.util.Map;
import org.slf4j.Logger;

record DefaultPluginContext(
    PluginDescriptor descriptor,
    PluginEnvironment environment,
    String jvnVersion,
    Path dataDirectory,
    Path projectDirectory,
    Map<String, String> configuration,
    Logger logger,
    PluginRegistries registries
) implements PluginContext {}
