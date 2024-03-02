package io.github.gaming32.stillalive;

import com.connorhaigh.javavpk.core.Archive;
import com.connorhaigh.javavpk.core.Entry;
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
import io.github.gaming32.stillalive.steam.SteamGames;
import io.github.gaming32.stillalive.util.Util;
import javazoom.jl.player.Player;
import net.platinumdigitalgroup.jvdf.VDFNode;
import net.platinumdigitalgroup.jvdf.VDFParser;
import org.jetbrains.annotations.Contract;

import javax.swing.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StillAlive {
    public static final int SCREEN_HEIGHT = 32;
    public static final int SCREEN_HEIGHT_HALF = 16;
    public static final int SCREEN_WIDTH_HALF = 55;
    public static final int CREDITS_COL_START = SCREEN_WIDTH_HALF + 2;

    public static void main(String[] args) throws Exception {
        if (SteamGames.PORTAL_PATH == null) {
            fail("Couldn't find Portal installation");
        }

        final Path portalVpkPath = SteamGames.PORTAL_PATH.resolve("portal/portal_pak_dir.vpk");
        if (!Files.isRegularFile(portalVpkPath)) {
            fail("Couldn't find portal_pak_dir.vpk at " + portalVpkPath);
        }
        final Archive portalVpk = new Archive(portalVpkPath.toFile());
        portalVpk.load();

        final Entry creditsEntry = Util.findEntry(portalVpk, "scripts", "credits", "txt");
        if (creditsEntry == null) {
            fail("Couldn't find credits.txt");
        }
        final VDFNode creditsVdf = new VDFParser()
            .parse(new String(creditsEntry.readData(), StandardCharsets.UTF_8))
            .getSubNode("credits.txt");
        final VDFNode creditsParams = creditsVdf.getSubNode("CreditsParams");

        final Entry stillAliveEntry = Util.findEntry(portalVpk, "sound/music", "portal_still_alive", "mp3");
        if (stillAliveEntry == null) {
            fail("Couldn't find portal_still_alive.mp3");
        }
        final VolumeControlAudioDevice audioDevice = new VolumeControlAudioDevice();
        audioDevice.setVolume(0.2f);
        final Player player = new Player(new ByteArrayInputStream(stillAliveEntry.readData()), audioDevice);

        // TODO: Language choice?
        final Path translationsPath = SteamGames.PORTAL_PATH.resolve("portal/resource/portal_english.txt");
        if (!Files.isRegularFile(translationsPath)) {
            fail("Couldn't find translations file " + translationsPath);
        }
        final Map<String, String> translations = loadTranslations(translationsPath);

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
                .setTerminalEmulatorTitle("Still Alive")
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
                        case START_SONG -> Util.startThread("MusicPlayer", player::play);
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

    private static Map<String, String> loadTranslations(Path path) throws IOException {
        return new VDFParser()
            .parse(Files.readString(path, StandardCharsets.UTF_16LE))
            .getSubNode("lang")
            .getSubNode("Tokens")
            .entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> (String)e.getValue()[0], (a, b) -> b, LinkedHashMap::new));
    }

    @Contract("_ -> fail")
    private static void fail(String message) {
        if (System.console() != null) {
            System.err.println(message);
        } else {
            JOptionPane.showMessageDialog(null, message, "Still Alive", JOptionPane.ERROR_MESSAGE);
        }
        System.exit(1);
    }
}
