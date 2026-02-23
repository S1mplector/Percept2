package com.musicplayer.data.models;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a music artist.
 * This class contains information about an artist and a list of their songs.
 */
public class Artist {

    /**
     * Unique identifier for the artist.
     */
    private long id;

    /**
     * Name of the artist.
     */
    private String name;

    /**
     * List of songs by the artist.
     */
    private List<Song> songs;

    /**
     * Default constructor.
     * Initializes an empty list of songs.
     */
    public Artist() {
        this.songs = new ArrayList<>();
    }

    /**
     * Constructs a new Artist with a specified ID and name.
     *
     * @param id   Unique identifier for the artist.
     * @param name Name of the artist.
     */
    public Artist(long id, String name) {
        this.id = id;
        this.name = name;
        this.songs = new ArrayList<>();
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
     * Adds a song to the artist's list of songs.
     *
     * @param song The song to add.
     */
    public void addSong(Song song) {
        this.songs.add(song);
    }

    /**
     * Compares this artist to another object for equality.
     *
     * @param o The object to compare with.
     * @return true if the objects are equal, false otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Artist artist = (Artist) o;
        return id == artist.id &&
                Objects.equals(name, artist.name);
    }

    /**
     * Generates a hash code for the artist.
     *
     * @return The hash code.
     */
    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    /**
     * Returns a string representation of the artist.
     *
     * @return A string containing the artist's details.
     */
    @Override
    public String toString() {
        return "Artist{" +
                "id=" + id +
                ", name='" + name + '\'' +
                '}';
    }
}
