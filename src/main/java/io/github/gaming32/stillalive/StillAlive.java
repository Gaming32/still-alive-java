package io.github.gaming32.stillalive;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.BasicTextImage;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.graphics.TextImage;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.ansi.ANSITerminal;
import com.googlecode.lanterna.terminal.ansi.UnixLikeTerminal;
import com.googlecode.lanterna.terminal.swing.SwingTerminalFrame;
import com.googlecode.lanterna.terminal.swing.TerminalEmulatorDeviceConfiguration;
import io.github.gaming32.stillalive.source.ResourceFinder;
import io.github.gaming32.stillalive.source.SourceMounts;
import io.github.gaming32.stillalive.steam.SteamGames;
import io.github.gaming32.stillalive.util.Util;
import net.platinumdigitalgroup.jvdf.VDFNode;
import net.platinumdigitalgroup.jvdf.VDFParser;
import org.jetbrains.annotations.Contract;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.swing.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StillAlive {
    public static final String TITLE = "Still Alive";
    public static final int SCREEN_HEIGHT = 32;
    public static final int SCREEN_HEIGHT_HALF = 16;
    public static final int SCREEN_WIDTH_HALF = 55;
    public static final int CREDITS_COL_START = SCREEN_WIDTH_HALF + 2;

    public static void main(String[] args) throws Exception {
        if (SteamGames.PORTAL_PATH == null) {
            fail("Couldn't find Portal installation");
        }

        final ResourceFinder resourceFinder;
        try {
            resourceFinder = SourceMounts.mountGame(SteamGames.PORTAL_PATH, SteamGames.PORTAL_PATH.resolve("portal"));
        } catch (IOException e) {
            if (System.console() == null) {
                JOptionPane.showMessageDialog(null, e.toString(), TITLE, JOptionPane.ERROR_MESSAGE);
            }
            throw e;
        }

        final byte[] creditsEntry = resourceFinder.findResource("scripts", "credits", "txt");
        if (creditsEntry == null) {
            fail("Couldn't find credits.txt");
        }
        final VDFNode creditsVdf = new VDFParser()
            .parse(new String(creditsEntry, StandardCharsets.UTF_8))
            .getSubNode("credits.txt");
        final VDFNode creditsParams = creditsVdf.getSubNode("CreditsParams");

        final byte[] stillAliveEntry = resourceFinder.findResource("sound/music", "portal_still_alive", "mp3");
        if (stillAliveEntry == null) {
            fail("Couldn't find portal_still_alive.mp3");
        }
        final AudioInputStream mp3Stream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(stillAliveEntry));
        final AudioFormat mp3Format = mp3Stream.getFormat();
        final AudioFormat pcmFormat = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            mp3Format.getSampleRate(),
            16,
            mp3Format.getChannels(),
            16 * mp3Format.getChannels() / 8,
            mp3Format.getSampleRate(),
            mp3Format.isBigEndian()
        );
        final AudioInputStream pcmStream = AudioSystem.getAudioInputStream(pcmFormat, mp3Stream);
        final Clip audioClip = AudioSystem.getClip();
        audioClip.open(pcmStream);
        ((FloatControl)audioClip.getControl(FloatControl.Type.MASTER_GAIN)).setValue(20f * (float)Math.log10(0.2));

        // TODO: Language choice?
        final byte[] translationsEntry = resourceFinder.findResource("resource", "portal_english", "txt");
        if (translationsEntry == null) {
            fail("Couldn't find translations file");
        }
        final Map<String, String> translations = loadTranslations(translationsEntry);

        final String[] textColorString = creditsParams.getString("color").split(" ");
        final TextColor bgColor = TextColor.ANSI.BLACK;
        final TextColor textColor = new TextColor.RGB(
            Integer.parseInt(textColorString[0]),
            Integer.parseInt(textColorString[1]),
            Integer.parseInt(textColorString[2])
        );

        final List<CreditsEvent> events = CreditsEvent.create(creditsVdf, translations);

        try (
            TerminalScreen screen = new DefaultTerminalFactory()
                .setUnixTerminalCtrlCBehaviour(UnixLikeTerminal.CtrlCBehaviour.CTRL_C_KILLS_APPLICATION)
                .setInitialTerminalSize(new TerminalSize(SCREEN_WIDTH_HALF * 2 + 2, SCREEN_HEIGHT + 4))
                .setTerminalEmulatorTitle(TITLE)
                .setTerminalEmulatorDeviceConfiguration(new TerminalEmulatorDeviceConfiguration(
                    2000, 500, TerminalEmulatorDeviceConfiguration.CursorStyle.UNDER_BAR, textColor, false
                ))
                .createScreen()
        ) {
            screen.startScreen();
            if (screen.getTerminal() instanceof ANSITerminal) {
                // Disable blink and set cursor to color
                System.out.printf("\u001b[?12l\u001b]1 2;rgb:%02x/%02x/%02x\u0007", textColor.getRed(), textColor.getGreen(), textColor.getBlue());
            }
            screen.getTerminal().setBackgroundColor(bgColor);
            screen.getTerminal().setForegroundColor(textColor);
            screen.getTerminal().setCursorVisible(true);
            final TextGraphics graphics = screen.newTextGraphics();
            graphics.setBackgroundColor(bgColor);
            graphics.setForegroundColor(textColor);
            screen.setCursorPosition(TerminalPosition.OFFSET_1x1);
            drawMainBoxes(graphics);
            screen.refresh();

            int songRow = 1;
            int songColumn = 2;
            int creditsColumn = 0;
            boolean cursorInCredits = false;

            final int creditsHeight = SCREEN_HEIGHT_HALF - 2;
            final TextImage creditsImage = new BasicTextImage(SCREEN_WIDTH_HALF - 2, creditsHeight);

            long startTime = System.currentTimeMillis();
            for (final CreditsEvent event : events) {
                final long targetTime = event.time();
                while (System.currentTimeMillis() - startTime < targetTime) {
                    Thread.onSpinWait();
                }
                if (screen.getTerminal() instanceof SwingTerminalFrame frame && !frame.isDisplayable()) break;
                screen.doResizeIfNecessary();
                screen.pollInput();
                drawMainBoxes(graphics);
                if (event instanceof CreditsEvent.CharacterEvent characterEvent) {
                    final char character = characterEvent.character();
                    if (!characterEvent.forCredits()) {
                        if (character == '\n') {
                            songRow++;
                            songColumn = 2;
                        } else {
                            graphics.setCharacter(songColumn, songRow, character);
                            songColumn++;
                        }
                    } else {
                        if (character == '\n') {
                            creditsImage.scrollLines(0, creditsHeight - 1, 1);
                            graphics.drawImage(new TerminalPosition(CREDITS_COL_START, 1), creditsImage);
                            creditsColumn = 0;
                        } else {
                            graphics.setCharacter(CREDITS_COL_START + creditsColumn, SCREEN_HEIGHT_HALF - 2, character);
                            creditsImage.setCharacterAt(
                                creditsColumn, creditsHeight - 1,
                                TextCharacter.fromCharacter(character, textColor, bgColor)[0]
                            );
                            creditsColumn++;
                        }
                    }
                } else if (event instanceof CreditsEvent.SimpleEvent simpleEvent) {
                    switch (simpleEvent.type()) {
                        case CLEAR_SCREEN -> {
                            graphics.fillRectangle(
                                TerminalPosition.OFFSET_1x1,
                                new TerminalSize(SCREEN_WIDTH_HALF - 2, SCREEN_HEIGHT - 2),
                                ' '
                            );
                            songRow = 1;
                            songColumn = 2;
                        }
                        case START_SONG -> Util.startThread("MusicPlayer", audioClip::start);
                    }
                } else if (event instanceof CreditsEvent.CursorEvent cursorEvent) {
                    cursorInCredits = cursorEvent.toCredits();
                } else if (event instanceof CreditsEvent.AsciiArtEvent asciiArtEvent) {
                    graphics.fillRectangle(
                        new TerminalPosition(SCREEN_WIDTH_HALF, SCREEN_HEIGHT_HALF),
                        new TerminalSize(SCREEN_WIDTH_HALF + 1, SCREEN_HEIGHT_HALF + 4),
                        ' '
                    );
                    final int startColumn = SCREEN_WIDTH_HALF + 8;
                    int row = SCREEN_HEIGHT_HALF;
                    int column = startColumn;
                    final String art = asciiArtEvent.art();
                    for (int i = 0; i < art.length(); i++) {
                        final char c = art.charAt(i);
                        if (c == '\n') {
                            row++;
                            column = startColumn;
                            continue;
                        }
                        graphics.setCharacter(column, row, c);
                        column++;
                    }
                }
                if (!cursorInCredits) {
                    screen.setCursorPosition(new TerminalPosition(songColumn, songRow));
                } else {
                    screen.setCursorPosition(new TerminalPosition(CREDITS_COL_START + creditsColumn, SCREEN_HEIGHT_HALF - 2));
                }
                screen.refresh();
            }
        } finally {
            audioClip.close();
            pcmStream.close();
            mp3Stream.close();
        }
    }

    private static void drawMainBoxes(TextGraphics graphics) {
        graphics.drawLine(0, 0, SCREEN_WIDTH_HALF - 1, 0, '-');
        graphics.drawLine(SCREEN_WIDTH_HALF + 1, 0, SCREEN_WIDTH_HALF * 2, 0, '-');
        graphics.drawLine(0, 1, 0, SCREEN_HEIGHT - 1, '|');
        graphics.drawLine(SCREEN_WIDTH_HALF - 1, 1, SCREEN_WIDTH_HALF - 1, SCREEN_HEIGHT - 1, '|');
        graphics.drawLine(SCREEN_WIDTH_HALF, 1, SCREEN_WIDTH_HALF, SCREEN_HEIGHT_HALF - 1, '|');
        graphics.drawLine(SCREEN_WIDTH_HALF * 2 + 1, 1, SCREEN_WIDTH_HALF * 2 + 1, SCREEN_HEIGHT_HALF - 1, '|');
        graphics.drawLine(0, SCREEN_HEIGHT, SCREEN_WIDTH_HALF - 1, SCREEN_HEIGHT, '-');
        graphics.drawLine(SCREEN_WIDTH_HALF + 1, SCREEN_HEIGHT_HALF - 1, SCREEN_WIDTH_HALF * 2, SCREEN_HEIGHT_HALF - 1, '_');
    }

    private static Map<String, String> loadTranslations(byte[] data) {
        return new VDFParser()
            .parse(removeBom(new String(data, StandardCharsets.UTF_16LE)))
            .getSubNode("lang")
            .getSubNode("Tokens")
            .entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> (String)e.getValue()[0], (a, b) -> b, LinkedHashMap::new));
    }

    private static String removeBom(String text) {
        if (text.startsWith("\ufeff")) {
            return text.substring(1);
        }
        return text;
    }

    @Contract("_ -> fail")
    private static void fail(String message) {
        if (System.console() != null) {
            System.err.println(message);
        } else {
            JOptionPane.showMessageDialog(null, message, TITLE, JOptionPane.ERROR_MESSAGE);
        }
        System.exit(1);
    }
}
