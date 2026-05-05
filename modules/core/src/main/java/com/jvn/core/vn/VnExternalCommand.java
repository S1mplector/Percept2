package com.jvn.core.vn;

public class VnExternalCommand {
  private final String provider; // e.g., "jes", "java", "custom"
  private final String payload;  // free-form arg string, provider-specific

  public VnExternalCommand(String provider, String payload) {
    this.provider = provider == null ? "" : provider;
    this.payload = payload == null ? "" : payload;
  }

  public String getProvider() { return provider; }
  public String getPayload() { return payload; }
}
