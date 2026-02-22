package com.jvn.core.menu;

/**
 * Runtime extension hook for custom menu actions.
 */
@FunctionalInterface
public interface MenuActionHandler {
  /**
   * @return true if the action was handled/consumed.
   */
  boolean handle(MenuActionContext context);
}
