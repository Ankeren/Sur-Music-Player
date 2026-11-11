package com.sur.player.service;

import com.sur.player.model.Playable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * WEEK 4 TOPIC: a shared resource and synchronization.
 *
 * The queue is written by the UI thread (the user taps a song) and read by
 * the service, which runs its own callbacks. Two threads touching the same
 * ArrayList is exactly how you get a ConcurrentModificationException or a
 * silently wrong index, so every method here is `synchronized`: only one
 * thread at a time may be inside this object.
 *
 * There is one queue for the whole app, so this is a singleton.
 */
public class PlaybackQueue {

    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;

    private static final PlaybackQueue INSTANCE = new PlaybackQueue();

    public static PlaybackQueue get() {
        return INSTANCE;
    }

    private final List<Playable> items = new ArrayList<>();
    private final Random random = new Random();

    private int index = -1;
    private boolean shuffle = false;
    private int repeatMode = REPEAT_OFF;

    private PlaybackQueue() {
    }

    // ---- setting up ------------------------------------------------------

    public synchronized void setQueue(List<Playable> newItems, int startIndex) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        index = items.isEmpty() ? -1 : Math.max(0, Math.min(startIndex, items.size() - 1));
    }

    public synchronized void setSingle(Playable item) {
        items.clear();
        if (item != null) {
            items.add(item);
            index = 0;
        } else {
            index = -1;
        }
    }

    // ---- reading ---------------------------------------------------------

    public synchronized Playable current() {
        if (index < 0 || index >= items.size()) return null;
        return items.get(index);
    }

    public synchronized int getIndex() {
        return index;
    }

    public synchronized int size() {
        return items.size();
    }

    public synchronized boolean isEmpty() {
        return items.isEmpty();
    }

    /** A copy, so callers cannot modify our list from outside. */
    public synchronized List<Playable> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(items));
    }

    // ---- moving ----------------------------------------------------------

    /**
     * Moves to the next track and returns it, or null when the queue has
     * genuinely finished (repeat off and we were on the last item).
     */
    public synchronized Playable next(boolean userPressedNext) {
        if (items.isEmpty()) return null;

        // Repeat-one only applies when a track ended by itself. If the user
        // presses next, they want the next track.
        if (repeatMode == REPEAT_ONE && !userPressedNext) {
            return items.get(index);
        }
        if (shuffle && items.size() > 1) {
            int newIndex;
            do {
                newIndex = random.nextInt(items.size());
            } while (newIndex == index);
            index = newIndex;
            return items.get(index);
        }
        if (index + 1 < items.size()) {
            index++;
            return items.get(index);
        }
        if (repeatMode == REPEAT_ALL) {
            index = 0;
            return items.get(index);
        }
        return null;   // end of queue
    }

    public synchronized Playable previous() {
        if (items.isEmpty()) return null;
        if (index - 1 >= 0) {
            index--;
        } else {
            index = items.size() - 1;
        }
        return items.get(index);
    }

    public synchronized Playable jumpTo(int newIndex) {
        if (newIndex < 0 || newIndex >= items.size()) return null;
        index = newIndex;
        return items.get(index);
    }

    // ---- modes -----------------------------------------------------------

    public synchronized boolean isShuffle() {
        return shuffle;
    }

    public synchronized void setShuffle(boolean shuffle) {
        this.shuffle = shuffle;
    }

    public synchronized int getRepeatMode() {
        return repeatMode;
    }

    public synchronized void cycleRepeatMode() {
        repeatMode = (repeatMode + 1) % 3;
    }
}
