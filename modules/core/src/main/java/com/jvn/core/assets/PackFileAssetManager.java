package com.jvn.core.assets;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link AssetManager} that reads assets from a single packed archive file.
 *
 * <p>The pack file is a ZIP archive containing assets organized by type
 * (matching the {@link AssetPaths} conventional layout). It includes an in-memory
 * index for fast lookups without decompressing the entire archive.</p>
 *
 * <p>Used by web and mobile exports to reduce download size and app install size.</p>
 */
public class PackFileAssetManager implements AssetManager {
  private static final Logger log = LoggerFactory.getLogger(PackFileAssetManager.class);

  private final String packFilePath;
  private final Map<String, PackEntry> index = new HashMap<>();
  private final Map<String, List<String>> directoryIndex = new HashMap<>();

  private static class PackEntry {
    String path = "";
  }

  /**
   * Construct a pack file asset manager and load the index.
   *
   * @param packFilePath path to the .pack file (ZIP archive)
   * @throws IOException if the pack file cannot be read or is invalid
   */
  public PackFileAssetManager(String packFilePath) throws IOException {
    this.packFilePath = packFilePath;
    loadIndex();
  }

  @Override
  public boolean exists(AssetType type, String name) {
    String path = AssetPaths.build(type, name);
    return index.containsKey(path);
  }

  @Override
  public URL url(AssetType type, String name) {
    // Pack files don't have URLs; return null
    return null;
  }

  @Override
  public InputStream open(AssetType type, String name) throws IOException {
    String path = AssetPaths.build(type, name);
    PackEntry entry = index.get(path);
    if (entry == null) {
      throw new IOException("Asset not found in pack: " + path);
    }
    return readEntryData(entry);
  }

  @Override
  public List<String> list(String directory) {
    String dir = directory.endsWith("/") ? directory : directory + "/";
    List<String> results = directoryIndex.get(dir);
    return results != null ? new ArrayList<>(results) : Collections.emptyList();
  }

  private void loadIndex() throws IOException {
    try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(
        new java.io.FileInputStream(packFilePath)))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        String name = entry.getName();
        if (!entry.isDirectory()) {
          PackEntry pe = new PackEntry();
          pe.path = name;
          index.put(name, pe);

          // Build directory index
          int lastSlash = name.lastIndexOf('/');
          if (lastSlash > 0) {
            String dir = name.substring(0, lastSlash + 1);
            String filename = name.substring(lastSlash + 1);
            directoryIndex.computeIfAbsent(dir, k -> new ArrayList<>()).add(filename);
          }
        }
      }
    }
    log.info("Loaded pack file with {} entries: {}", index.size(), packFilePath);
  }

  private InputStream readEntryData(PackEntry entry) throws IOException {
    try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(
        new java.io.FileInputStream(packFilePath)))) {
      ZipEntry ze;
      while ((ze = zis.getNextEntry()) != null) {
        if (ze.getName().equals(entry.path)) {
          byte[] data = zis.readAllBytes();
          return new ByteArrayInputStream(data);
        }
      }
    }
    throw new IOException("Entry not found in pack: " + entry.path);
  }
}
