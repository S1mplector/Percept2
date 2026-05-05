package com.jvn.core.menu.gallery;

/**
 * A single music-room track entry.
 *
 * @param id          unique track identifier
 * @param audioPath   asset path to the audio file
 * @param title       display title for the track
 * @param artist      optional artist/composer name
 * @param category    grouping label (e.g. "BGM", "Vocal")
 * @param order       sort order within category
 * @param unlockFlag  persistent-store key; defaults to {@code "music.unlocked." + id}
 */
public record MusicRoomEntry(
    String id,
    String audioPath,
    String title,
    String artist,
    String category,
    int order,
    String unlockFlag
) {
  public MusicRoomEntry {
    if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
    if (audioPath == null || audioPath.isBlank()) throw new IllegalArgumentException("audioPath must not be blank");
    id = id.trim();
    audioPath = audioPath.trim();
    title = title == null || title.isBlank() ? id : title.trim();
    artist = artist == null ? "" : artist.trim();
    category = category == null || category.isBlank() ? "BGM" : category.trim();
    unlockFlag = unlockFlag == null || unlockFlag.isBlank()
        ? "music.unlocked." + id
        : unlockFlag.trim();
  }
}
