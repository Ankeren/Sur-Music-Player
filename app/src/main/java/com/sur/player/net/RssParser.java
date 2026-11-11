package com.sur.player.net;

import android.util.Xml;

import com.sur.player.model.Episode;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * WEEK 11 TOPIC: XML parsing, and a plain HTTP request.
 *
 * Stations arrive as JSON and are parsed by Gson. Shows arrive as RSS,
 * which is XML, so they are parsed here with XmlPullParser - Android's
 * built-in streaming parser. "Streaming" means it walks the document tag by
 * tag without loading the whole tree into memory, which matters because a
 * podcast feed can be several megabytes.
 *
 * The HTTP request uses HttpURLConnection rather than Retrofit, on purpose:
 * it shows the request being made by hand, and Retrofit's JSON converter
 * would be no help with XML anyway.
 *
 * A feed looks like this, and we want the parts marked <-- :
 *
 *   <rss><channel>
 *     <title>The Show</title>                        <-- feed name
 *     <item>
 *       <title>Episode 12</title>                    <-- episode title
 *       <pubDate>Mon, 15 Sep 2026 06:00:00 GMT</pubDate>   <-- date
 *       <enclosure url="https://.../ep12.mp3" .../>  <-- the audio
 *     </item>
 *     ...
 *   </channel></rss>
 */
public final class RssParser {

    private static final int MAX_EPISODES = 50;
    private static final int TIMEOUT_MS = 15000;

    private RssParser() {
    }

    /**
     * Downloads and parses a feed. Runs on a background thread.
     *
     * @return the episodes, or an empty list if anything went wrong
     */
    public static List<Episode> fetch(String feedUrl) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(feedUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "SurPlayer/1.0");

            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                return new ArrayList<>();
            }

            try (InputStream in = connection.getInputStream()) {
                return parse(in, feedUrl);
            }

        } catch (IOException | XmlPullParserException e) {
            // No internet, a bad URL, or a feed that is not valid XML.
            // The caller shows "could not reach the internet".
            return new ArrayList<>();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    // ---- the parser ------------------------------------------------------

    private static List<Episode> parse(InputStream in, String feedUrl)
            throws XmlPullParserException, IOException {

        List<Episode> episodes = new ArrayList<>();

        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(in, null);

        String feedName = "";
        boolean insideItem = false;

        // Fields of the <item> we are currently reading.
        String title = null;
        String pubDate = null;
        String audioUrl = null;

        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT && episodes.size() < MAX_EPISODES) {

            if (event == XmlPullParser.START_TAG) {
                String tag = parser.getName();

                if ("item".equalsIgnoreCase(tag)) {
                    insideItem = true;
                    title = null;
                    pubDate = null;
                    audioUrl = null;

                } else if ("title".equalsIgnoreCase(tag)) {
                    String text = readText(parser);
                    if (insideItem) {
                        title = text;
                    } else if (feedName.isEmpty()) {
                        // The first <title> outside an <item> is the show name.
                        feedName = text;
                    }

                } else if (insideItem && "pubDate".equalsIgnoreCase(tag)) {
                    pubDate = shortenDate(readText(parser));

                } else if (insideItem && "enclosure".equalsIgnoreCase(tag)) {
                    // The audio file lives in an attribute, not in the text.
                    audioUrl = parser.getAttributeValue(null, "url");
                }

            } else if (event == XmlPullParser.END_TAG
                    && "item".equalsIgnoreCase(parser.getName())) {

                if (title != null && audioUrl != null && !audioUrl.isEmpty()) {
                    Episode episode = new Episode();
                    episode.setTitle(title);
                    episode.setPublishedAt(pubDate == null ? "" : pubDate);
                    episode.setAudioUrl(audioUrl);
                    episode.setFeedUrl(feedUrl);
                    episode.setFeedName(feedName);
                    episodes.add(episode);
                }
                insideItem = false;
            }

            event = parser.next();
        }

        return episodes;
    }

    /** Reads the text between <tag> and </tag>. */
    private static String readText(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        String result = "";
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.getText().trim();
            parser.nextTag();
        }
        return result;
    }

    /** "Mon, 15 Sep 2026 06:00:00 GMT" -> "15 Sep 2026". */
    private static String shortenDate(String pubDate) {
        if (pubDate == null) return "";
        String[] parts = pubDate.split(" ");
        if (parts.length >= 4) {
            return parts[1] + " " + parts[2] + " " + parts[3];
        }
        return pubDate;
    }
}
