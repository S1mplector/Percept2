package com.jvn.web;

import com.jvn.core.vn.VnPersistenceBackend;
import org.teavm.jso.browser.Storage;

/** Browser {@code localStorage}-backed {@link VnPersistenceBackend}. One JSON blob per browser origin. */
public final class WebLocalStoragePersistenceBackend implements VnPersistenceBackend {
  private static final String STORAGE_KEY = "jvn.persistent";

  @Override
  public String read() {
    return Storage.getLocalStorage().getItem(STORAGE_KEY);
  }

  @Override
  public void write(String json) {
    Storage.getLocalStorage().setItem(STORAGE_KEY, json);
  }
}
