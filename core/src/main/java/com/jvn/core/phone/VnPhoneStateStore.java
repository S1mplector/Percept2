package com.jvn.core.phone;

import java.io.IOException;
import java.util.function.Supplier;

import com.jvn.core.vn.VnState;

/**
 * Persists phone state through the existing VN variable map so save/load and
 * rollback inherit the behavior automatically.
 */
public final class VnPhoneStateStore {
  public static final String VAR_PHONE_PROPERTIES = "ui.phone.properties";

  private VnPhoneStateStore() {
  }

  public static VnPhoneData load(VnState state, Supplier<VnPhoneData> seedSupplier) {
    if (state == null) {
      return seedSupplier == null ? new VnPhoneData() : safeSeed(seedSupplier);
    }
    Object raw = state.getVariable(VAR_PHONE_PROPERTIES);
    if (raw instanceof String text && !text.isBlank()) {
      try {
        return VnPhonePropertiesCodec.loadFromString(text);
      } catch (IOException ignored) {
      }
    }
    return seedSupplier == null ? new VnPhoneData() : safeSeed(seedSupplier);
  }

  public static void save(VnState state, VnPhoneData data) {
    if (state == null || data == null) return;
    state.setVariable(VAR_PHONE_PROPERTIES, VnPhonePropertiesCodec.toPropertiesString(data));
  }

  private static VnPhoneData safeSeed(Supplier<VnPhoneData> seedSupplier) {
    try {
      VnPhoneData data = seedSupplier.get();
      return data == null ? new VnPhoneData() : data;
    } catch (Exception ignored) {
      return new VnPhoneData();
    }
  }
}
