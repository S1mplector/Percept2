package com.jvn.scripting.jes.runtime;

import java.util.HashMap;
import java.util.Map;

public class Item {
  private String id;
  private final Map<String,Object> props = new HashMap<>();

  public String getId() { return id; }

  public void setId(String id) { this.id = id; }

  public Map<String,Object> getProps() { return props; }

  public Object getProp(String key) { return props.get(key); }
}
