package com.jvn.hub;

/** Central update target used by every Engine Hub view. */
final class HubUpdateTarget {
  static final String REMOTE = "origin";
  static final String BRANCH = "stable";
  static final String REMOTE_REF = REMOTE + "/" + BRANCH;
  static final String FETCH_REFSPEC = "refs/heads/" + BRANCH + ":refs/remotes/" + REMOTE_REF;

  private HubUpdateTarget() {
  }
}
