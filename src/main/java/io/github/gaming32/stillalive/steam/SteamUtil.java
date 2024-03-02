package io.github.gaming32.stillalive.steam;

import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinReg;
import io.github.gaming32.stillalive.util.Util;
import net.platinumdigitalgroup.jvdf.VDFNode;
import net.platinumdigitalgroup.jvdf.VDFParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class SteamUtil {
    public static final Path STEAM_DIR = Platform.isWindows() ? findWindowsSteamDir() : findPosixSteamDir();

    private static final VDFParser VDF_PARSER = new VDFParser();
    private static final VDFNode LIBRARY_FOLDERS = Util.map(STEAM_DIR, steamDir -> {
        try {
            return readVdf(steamDir.resolve("steamapps/libraryfolders.vdf")).getSubNode("libraryfolders");
        } catch (Exception e) {
            System.err.println("Failed to read libraryfolders.vdf");
            e.printStackTrace();
            return null;
        }
    });

    public static Path findGamePath(int gameId) {
        if (LIBRARY_FOLDERS == null) {
            return null;
        }
        final String gameIdStr = Integer.toString(gameId);

        Path libraryPath = null;
        for (int i = 0; i < LIBRARY_FOLDERS.size(); i++) {
            final VDFNode library = LIBRARY_FOLDERS.getSubNode(Integer.toString(i));
            if (library.getSubNode("apps").containsKey(gameIdStr)) {
                libraryPath = Path.of(library.getString("path"));
                break;
            }
        }
        if (libraryPath == null) {
            return null;
        }

        final VDFNode manifest;
        try {
            manifest = readVdf(libraryPath.resolve("steamapps/appmanifest_" + gameIdStr + ".acf"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        final String relativeGamePath = manifest.getSubNode("AppState").getString("installdir");

        return libraryPath.resolve("steamapps/common").resolve(relativeGamePath);
    }

    private static VDFNode readVdf(Path path) throws IOException {
        return VDF_PARSER.parse(Files.readAllLines(path).toArray(String[]::new));
    }

    private static Path findWindowsSteamDir() {
        for (final String registryKey : List.of("SOFTWARE\\Wow6432Node\\Valve\\Steam", "SOFTWARE\\Valve\\Steam")) {
            try {
                return Path.of(Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE, registryKey, "InstallPath"));
            } catch (Win32Exception e) {
                if (e.getErrorCode() != WinError.ERROR_FILE_NOT_FOUND) {
                    throw e;
                }
            }
        }
        System.err.println("Unable to find Steam installation (Windows)");
        return null;
    }

    private static Path findPosixSteamDir() {
        final Path home = Path.of(System.getProperty("user.home"));
        return Stream.of(
                ".local/share/Steam", // .deb
                ".var/app/com.valvesoftware.Steam/data/Steam", // FlatPak
                "Library/Application Support/Steam", // macOS
                "snap/steam/common/.local/share/Steam" // Snap
            )
            .map(home::resolve)
            .filter(Files::isDirectory)
            .findFirst()
            .orElseGet(() -> {
                System.err.println("Unable to find Steam installation (POSIX)");
                return null;
            });
    }
}
