package com.jvn.core.assets;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

public interface AssetManager {
  boolean exists(AssetType type, String name);
  URL url(AssetType type, String name);
  InputStream open(AssetType type, String name) throws IOException;
  List<String> list(String directory);
}
