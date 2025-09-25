package com.jvn.core.assets;

public class AssetCatalog {
  private final AssetManager manager;

  public AssetCatalog() {
    this(new ClasspathAssetManager());
  }

  public AssetCatalog(AssetManager manager) {
    this.manager = manager;
  }

  public boolean exists(AssetType type, String name) {
    return manager.exists(type, name);
  }

  public java.net.URL url(AssetType type, String name) {
    return manager.url(type, name);
  }

  public java.io.InputStream open(AssetType type, String name) throws java.io.IOException {
    return manager.open(type, name);
  }

  public java.util.List<String> list(String directory) {
    return manager.list(directory);
  }

  public java.util.List<String> listImages() { return list(AssetPaths.IMAGES); }
  public java.util.List<String> listAudio() { return list(AssetPaths.AUDIO); }
  public java.util.List<String> listScripts() { return list(AssetPaths.SCRIPTS); }
  public java.util.List<String> listFonts() { return list(AssetPaths.FONTS); }
}
