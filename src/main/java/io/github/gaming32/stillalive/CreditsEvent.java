package io.github.gaming32.stillalive;

import io.github.gaming32.stillalive.util.Util;
import net.platinumdigitalgroup.jvdf.VDFNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public sealed interface CreditsEvent {
    record CharacterEvent(long time, char character, boolean forCredits) implements CreditsEvent {
    }

    record SimpleEvent(long time, Type type) implements CreditsEvent {
        enum Type {
            CLEAR_SCREEN,
            START_SONG
        }
    }

    record CursorEvent(long time, boolean toCredits) implements CreditsEvent {
    }

    record AsciiArtEvent(long time, String art) implements CreditsEvent {
    }

    long time();

    static List<CreditsEvent> create(VDFNode credits, Map<String, String> translations) {
        final VDFNode params = credits.getSubNode("CreditsParams");
        final List<CreditsEvent> startSong = List.of(new SimpleEvent(
            getTimeParam(params, "songstarttime"), SimpleEvent.Type.START_SONG
        ));
        final List<CreditsEvent> creditsList = loadCredits(credits, params);
        final List<CreditsEvent> songList = loadSong(credits, translations);
        final long endTime = Math.max(
            creditsList.get(creditsList.size() - 1).time(),
            songList.get(songList.size() - 1).time()
        ) + 5000L;
        final List<CreditsEvent> blinkingCursor = blinkCursor(params, endTime);
        return merge(startSong, creditsList, songList, blinkingCursor);
    }

    private static List<CreditsEvent> loadCredits(VDFNode credits, VDFNode params) {
        final String joined = credits.getSubNode("OutroCreditsNames")
            .getEntryList()
            .stream()
            .map(e -> e.getKey().isBlank() ? "\n" : e.getKey() + "\n")
            .collect(Collectors.joining());
        final List<CreditsEvent> result = new ArrayList<>(joined.length());
        splitText(joined, getTimeParam(params, "scrollcreditsstart"), getTimeParam(params, "scrolltime"), true, result);
        return result;
    }

    private static List<CreditsEvent> loadSong(VDFNode credits, Map<String, String> translations) {
        final Map<Integer, String> asciiArt = createAsciiArt(credits);
        final List<CreditsEvent> result = new ArrayList<>();
        long currentTime = 0L;
        for (final var entry : credits.getSubNode("OutroSongLyrics").getEntryList()) {
            final String segment = entry.getKey();
            final int rBracket = segment.indexOf(']');
            final long time = getTime(segment.substring(1, rBracket));
            String data = segment.substring(rBracket + 1);
            if (data.startsWith("<<<")) {
                final int endAscii = data.indexOf(">>>");
                final int targetArt = Integer.parseInt(data, 3, endAscii, 10);
                result.add(new AsciiArtEvent(currentTime, asciiArt.get(targetArt)));
                data = data.substring(endAscii + 3);
            }

            boolean justDelay = data.equals(" ");
            boolean clearScreen = data.equals("&");
            boolean hasNewLine = !justDelay;
            if (!data.equals("^") && !clearScreen && !justDelay) {
                final StringBuilder message = new StringBuilder();
                for (int i = 0; i < data.length(); i++) {
                    final char c = data.charAt(i);
                    switch (c) {
                        case '*' -> hasNewLine = false;
                        case '#' -> {
                            int endIndex = data.indexOf(' ', i + 1);
                            if (endIndex == -1) {
                                endIndex = data.length();
                            }
                            message.append(translations.get(data.substring(i + 1, endIndex)));
                            i = endIndex;
                        }
                        default -> message.append(c);
                    }
                }
                splitText(message, currentTime, time, false, result);
            }
            currentTime += time;
            if (clearScreen) {
                result.add(new SimpleEvent(currentTime, SimpleEvent.Type.CLEAR_SCREEN));
            } else if (hasNewLine) {
                result.add(new CharacterEvent(currentTime, '\n', false));
            }
        }
        return result;
    }

    private static Map<Integer, String> createAsciiArt(VDFNode credits) {
        final Map<Integer, String> result = new HashMap<>();
        result.put(0, "");
        for (final var entry : credits.getSubNode("OutroAsciiArt").getEntryList()) {
            final String line = (String)entry.getValue();
            final int rBracket = line.indexOf(']');
            result.merge(
                (int)(getTime(line.substring(1, rBracket)) / 1000L),
                line.substring(rBracket + 1),
                (a, b) -> a + "\n" + b
            );
        }
        return result;
    }

    private static List<CreditsEvent> blinkCursor(VDFNode params, long endTime) {
        final long blinkTime = getTimeParam(params, "cursorblinktime");
        final List<CreditsEvent> result = new ArrayList<>();
        boolean atCredits = true;
        for (long currentTime = blinkTime; currentTime <= endTime; currentTime += blinkTime) {
            result.add(new CursorEvent(currentTime, atCredits));
            atCredits = !atCredits;
        }
        return result;
    }

    private static void splitText(CharSequence text, long startTime, long length, boolean forCredits, List<CreditsEvent> dest) {
        for (int i = 0; i < text.length(); i++) {
            dest.add(new CharacterEvent(startTime + length * i / text.length(), text.charAt(i), forCredits));
        }
    }

    private static long getTimeParam(VDFNode params, String key) {
        return getTime(params.getString(key));
    }

    private static long getTime(String value) {
        return new BigDecimal(value).multiply(Util.TO_MILLIS).longValue();
    }

    @SafeVarargs
    static List<CreditsEvent> merge(List<CreditsEvent>... lists) {
        return Arrays.stream(lists)
            .flatMap(List::stream)
            .sorted(Comparator.comparingLong(CreditsEvent::time))
            .toList();
    }
}
