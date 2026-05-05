package com.musicplayer.data.models;

import java.util.Objects;

/**
 * Represents a song in the music library.
 * This class contains all the metadata related to a single music track.
 */
public class Song {

    /**
     * Unique identifier for the song.
     */
    private long id;

    /**
     * Title of the song.
     */
    private String title;

    /**
     * Artist of the song.
     */
    private String artist;

    /**
     * Album to which the song belongs.
     */
    private String album;

    /**
     * Genre of the song.
     */
    private String genre;

    /**
     * Duration of the song in seconds.
     */
    private long duration; // in seconds

    /**
     * Absolute path to the audio file.
     */
    private String filePath;

    /**
     * Track number of the song in the album.
     */
    private int trackNumber;

    /**
     * Release year of the song.
     */
    private int year;

    /**
     * Number of times this song has been played.
     */
    private int playCount;

    /**
     * Timestamp of when this song was last played (milliseconds since epoch).
     */
    private long lastPlayed;

    /**
     * Whether this song is marked as a favorite.
     */
    private boolean favorite;
    private int rating;

    /**
     * Default constructor.
     */
    public Song() {
    }

    /**
     * Constructs a new Song with specified details.
     *
     * @param id          Unique identifier for the song.
     * @param title       Title of the song.
     * @param artist      Artist of the song.
     * @param album       Album of the song.
     * @param genre       Genre of the song.
     * @param duration    Duration of the song in seconds.
     * @param filePath    Absolute path to the audio file.
     * @param trackNumber Track number in the album.
     * @param year        Release year of the song.
     */
    public Song(long id, String title, String artist, String album, String genre, long duration, String filePath, int trackNumber, int year) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.genre = genre;
        this.duration = duration;
        this.filePath = filePath;
        this.trackNumber = trackNumber;
        this.year = year;
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

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public int getTrackNumber() {
        return trackNumber;
    }

    public void setTrackNumber(int trackNumber) {
        this.trackNumber = trackNumber;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public int getPlayCount() {
        return playCount;
    }

    public void setPlayCount(int playCount) {
        this.playCount = playCount;
    }

    public long getLastPlayed() {
        return lastPlayed;
    }

    public void setLastPlayed(long lastPlayed) {
        this.lastPlayed = lastPlayed;
    }

    public void incrementPlayCount() {
        this.playCount++;
        this.lastPlayed = System.currentTimeMillis();
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    /**
     * Compares this song to another object for equality.
     *
     * @param o The object to compare with.
     * @return true if the objects are equal, false otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Song song = (Song) o;
        return id == song.id &&
                duration == song.duration &&
                trackNumber == song.trackNumber &&
                year == song.year &&
                Objects.equals(title, song.title) &&
                Objects.equals(artist, song.artist) &&
                Objects.equals(album, song.album) &&
                Objects.equals(genre, song.genre) &&
                Objects.equals(filePath, song.filePath);
    }

    /**
     * Generates a hash code for the song.
     *
     * @return The hash code.
     */
    @Override
    public int hashCode() {
        return Objects.hash(id, title, artist, album, genre, duration, filePath, trackNumber, year);
    }

    /**
     * Returns a string representation of the song.
     *
     * @return A string containing the song's main details.
     */
    @Override
    public String toString() {
        return "Song{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", artist='" + artist + '\'' +
                ", album='" + album + '\'' +
                ", duration=" + duration +
                ", filePath='" + filePath + '\'' +
                '}';
    }
}
