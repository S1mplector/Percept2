package com.jvn.core.menu;

import com.jvn.core.engine.Engine;
import com.jvn.core.menu.config.MenuActionSpec;

/**
 * Context payload for runtime custom menu action handling.
 */
public record MenuActionContext(
    Engine engine,
    String sourceMenuId,
    String sourceItemId,
    String defaultScriptName,
    MenuActionSpec action
) {
}
