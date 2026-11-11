package com.sur.player.data;

import androidx.room.Embedded;

import com.sur.player.model.Playlist;

/**
 * A small holder so one SQL query can return a playlist AND how many songs
 * are in it. @Embedded tells Room "the playlist columns are inside this
 * object"; songCount comes from the sub-select in PlaylistDao.
 *
 * Without this we would run one extra query per playlist, which is the
 * classic "N+1 queries" mistake.
 */
public class PlaylistWithCount {

    @Embedded
    public Playlist playlist;

    public int songCount;
}
