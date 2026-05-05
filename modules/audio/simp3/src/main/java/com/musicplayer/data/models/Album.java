package com.musicplayer.data.models;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a music album.
 * This class contains information about an album, including its tracks and cover art.
 */
public class Album {

    /**
     * Unique identifier for the album.
     */
    private long id;

    /**
     * Title of the album.
     */
    private String title;

    /**
     * Name of the artist of the album.
     */
    private String artistName;

    /**
     * Release year of the album.
     */
    private int releaseYear;

    /**
     * List of songs in the album.
     */
    private List<Song> songs;

    /**
     * Path to the album's cover art image.
     */
    private String coverArtPath;

    /**
     * Default constructor.
     * Initializes an empty list of songs.
     */
    public Album() {
        this.songs = new ArrayList<>();
    }

    /**
     * Constructs a new Album with specified details.
     *
     * @param id           Unique identifier for the album.
     * @param title        Title of the album.
     * @param artistName   Name of the artist.
     * @param releaseYear  Release year of the album.
     * @param coverArtPath Path to the cover art image.
     */
    public Album(long id, String title, String artistName, int releaseYear, String coverArtPath) {
        this.id = id;
        this.title = title;
        this.artistName = artistName;
        this.releaseYear = releaseYear;
        this.coverArtPath = coverArtPath;
        this.songs = new ArrayList<>();
    }

    // Getters and Setters

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtistName() {
        return artistName;
    }

    public void setArtistName(String artistName) {
        this.artistName = artistName;
    }

    public int getReleaseYear() {
        return releaseYear;
    }

    public void setReleaseYear(int releaseYear) {
        this.releaseYear = releaseYear;
    }

    public List<Song> getSongs() {
        return songs;
    }

    public void setSongs(List<Song> songs) {
        this.songs = songs;
    }

    /**
     * Adds a song to the album's list of tracks.
     *
     * @param song The song to add.
     */
    public void addSong(Song song) {
        this.songs.add(song);
    }

    public String getCoverArtPath() {
        return coverArtPath;
    }

    public void setCoverArtPath(String coverArtPath) {
        this.coverArtPath = coverArtPath;
    }

    /**
     * Compares this album to another object for equality.
     *
     * @param o The object to compare with.
     * @return true if the objects are equal, false otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Album album = (Album) o;
        return id == album.id &&
                releaseYear == album.releaseYear &&
                Objects.equals(title, album.title) &&
                Objects.equals(artistName, album.artistName);
    }

    /**
     * Generates a hash code for the album.
     *
     * @return The hash code.
     */
    @Override
    public int hashCode() {
        return Objects.hash(id, title, artistName, releaseYear);
    }

    /**
     * Returns a string representation of the album.
     *
     * @return A string containing the album's details.
     */
    @Override
    public String toString() {
        return "Album{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", artistName='" + artistName + '\'' +
                ", releaseYear=" + releaseYear +
                '}';
    }
}
