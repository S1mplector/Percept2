package com.musicplayer.data.models;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a user-created playlist.
 * This class contains a list of songs and a name for the playlist.
 */
public class Playlist {

    /**
     * Unique identifier for the playlist.
     */
    private long id;

    /**
     * Name of the playlist.
     */
    private String name;

    /**
     * List of songs in the playlist.
     */
    private List<Song> songs;

    /**
     * Indicates whether the playlist is in shuffle mode.
     */
    private boolean isShuffled;

    /**
     * Default constructor.
     * Initializes an empty list of songs.
     */
    public Playlist() {
        this.songs = new ArrayList<>();
    }

    /**
     * Constructs a new Playlist with a specified ID and name.
     *
     * @param id   Unique identifier for the playlist.
     * @param name Name of the playlist.
     */
    public Playlist(long id, String name) {
        this.id = id;
        this.name = name;
        this.songs = new ArrayList<>();
        this.isShuffled = false;
    }

    // Getters and Setters

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Song> getSongs() {
        return songs;
    }

    public void setSongs(List<Song> songs) {
        this.songs = songs;
    }

    /**
     * Adds a song to the playlist.
     *
     * @param song The song to add.
     */
    public void addSong(Song song) {
        this.songs.add(song);
    }

    /**
     * Removes a song from the playlist.
     *
     * @param song The song to remove.
     */
    public void removeSong(Song song) {
        this.songs.remove(song);
    }

    public boolean isShuffled() {
        return isShuffled;
    }

    public void setShuffled(boolean shuffled) {
        isShuffled = shuffled;
    }

    /**
     * Compares this playlist to another object for equality.
     *
     * @param o The object to compare with.
     * @return true if the objects are equal, false otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Playlist playlist = (Playlist) o;
        return id == playlist.id &&
                Objects.equals(name, playlist.name);
    }

    /**
     * Generates a hash code for the playlist.
     *
     * @return The hash code.
     */
    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    /**
     * Returns a string representation of the playlist.
     *
     * @return A string containing the playlist's details.
     */
    @Override
    public String toString() {
        return "Playlist{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", songCount=" + songs.size() +
                '}';
    }
}
