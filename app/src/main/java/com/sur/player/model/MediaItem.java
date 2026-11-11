package com.sur.player.model;

/**
 * WEEK 1 TOPIC: abstract class and inheritance.
 *
 * Songs, radio stations and podcast episodes all have a title, so the title
 * is declared once, here, instead of three times.
 *
 * It is abstract for two reasons:
 *   1. A bare "media item" is never a real thing in this app, so the compiler
 *      should stop anyone writing  new MediaItem().
 *   2. getKey() and getSubtitle() are declared but not written. Every subclass
 *      is FORCED to answer them in its own way. That is polymorphism: the
 *      adapter calls getSubtitle() and the right version runs at run time.
 *
 * Note this class is NOT a Room @Entity. Room still stores the inherited
 * "title" field as a column in each child table.
 */
public abstract class MediaItem {

    private String title;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    /** A unique id as text. Songs use a number, stations use a UUID. */
    public abstract String getKey();

    /** The small grey line under the title. */
    public abstract String getSubtitle();

    /** Used when a MediaItem is dropped straight into a spinner or a log. */
    @Override
    public String toString() {
        return title;
    }
}
