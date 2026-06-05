package com.jvn.core.generalhelp;

import java.util.List;

/** Optional local model backend used by Jane after TAGI has grounded the answer. */
public interface LocalChatModel {
  String name();

  boolean isAvailable();

  String generate(String query, HelpResponse grounding, List<ChatMessage> history);
}
