package com.jvn.plugin.api;

/** Idempotent handle used to remove an extension registration. */
@FunctionalInterface
public interface Registration extends AutoCloseable {
  @Override void close();
}
