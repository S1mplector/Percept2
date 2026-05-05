package com.jvn.core.menu.gallery;

/**
 * A single gallery/CG entry.
 *
 * @param id         unique identifier (used as persistent-store key suffix)
 * @param imagePath  asset path to the CG image (relative to project root)
 * @param category   grouping label (e.g. "Chapter 1", "Endings")
 * @param order      sort order within category (lower = earlier)
 * @param unlockFlag persistent-store key that marks this CG as unlocked;
 *                   defaults to {@code "gallery.unlocked." + id} when null
 */
public record GalleryEntry(
    String id,
    String imagePath,
    String category,
    int order,
    String unlockFlag
) {
  public GalleryEntry {
    if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
    if (imagePath == null || imagePath.isBlank()) throw new IllegalArgumentException("imagePath must not be blank");
    category = category == null || category.isBlank() ? "Default" : category.trim();
    unlockFlag = unlockFlag == null || unlockFlag.isBlank()
        ? "gallery.unlocked." + id.trim()
        : unlockFlag.trim();
    id = id.trim();
    imagePath = imagePath.trim();
  }

  /** Convenience constructor without explicit unlock flag. */
  public GalleryEntry(String id, String imagePath, String category, int order) {
    this(id, imagePath, category, order, null);
  }
}
