package io.github.gaming32.stillalive.source;

import com.connorhaigh.javavpk.core.Archive;
import com.connorhaigh.javavpk.core.Entry;
import com.connorhaigh.javavpk.exceptions.ArchiveException;
import com.connorhaigh.javavpk.exceptions.EntryException;
import io.github.gaming32.stillalive.util.Util;
import net.platinumdigitalgroup.jvdf.VDFNode;
import net.platinumdigitalgroup.jvdf.VDFParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SourceMounts {
    private static final String GAMEINFO_PATH = "|gameinfo_path|";
    private static final String ALL_SOURCE_ENGINE_PATHS = "|all_source_engine_paths|";

    public static ResourceFinder mountDirectory(Path dir) {
        return (directory, name, extension) -> {
            final Path file = dir.resolve(directory).resolve(name + '.' + extension);
            return Files.exists(file) ? Files.readAllBytes(file) : null;
        };
    }

    public static ResourceFinder mountVpk(File vpkPath) throws IOException {
        final String vpkName = vpkPath.getName();
        if (!vpkName.endsWith(".vpk")) {
            throw new IllegalArgumentException(vpkPath + " doesn't have .vpk extension");
        }
        final File dirPath = new File(vpkPath.getParentFile(), vpkName.substring(0, vpkName.lastIndexOf('.')) + "_dir.vpk");
        if (!dirPath.isFile()) {
            return ResourceFinder.NULL;
        }
        try {
            final Archive archive = new Archive(dirPath);
            archive.load();
            return (directory, name, extension) -> {
                final Entry entry = Util.findEntry(archive, directory, name, extension);
                if (entry == null) {
                    return null;
                }
                try {
                    return entry.readData();
                } catch (ArchiveException e) {
                    throw new IOException(e);
                }
            };
        } catch (ArchiveException | EntryException e) {
            throw new IOException(e);
        }
    }

    public static ResourceFinder mountPath(Path path) throws IOException {
        if (path.toString().endsWith(".vpk")) {
            final ResourceFinder result = mountVpk(path.toFile());
            if (result != ResourceFinder.NULL) {
                return result;
            }
        }
        return mountDirectory(path);
    }

    public static ResourceFinder mountGame(Path engineDir, Path gameDir) throws IOException {
        return ResourceFinder.sequential(getGameMounts(engineDir, gameDir));
    }

    public static List<ResourceFinder> getGameMounts(Path engineDir, Path gameDir) throws IOException {
        final VDFNode searchPaths = new VDFParser()
            .parse(Files.readString(gameDir.resolve("gameinfo.txt"), StandardCharsets.UTF_8))
            .getSubNode("GameInfo")
            .getSubNode("FileSystem")
            .getSubNode("SearchPaths");
        final List<ResourceFinder> result = new ArrayList<>(searchPaths.getEntryList().size());
        for (final var entry : searchPaths.getEntryList()) {
            String target = ((String)entry.getValue());
            boolean allInDirectory = target.endsWith("/*");
            if (allInDirectory) {
                target = target.substring(0, target.length() - 2);
            }

            final Path targetPath;
            if (target.startsWith(GAMEINFO_PATH)) {
                targetPath = gameDir.resolve(target.substring(GAMEINFO_PATH.length()));
            } else if (target.startsWith(ALL_SOURCE_ENGINE_PATHS)) {
                targetPath = engineDir.resolve(target.substring(ALL_SOURCE_ENGINE_PATHS.length()));
            } else {
                targetPath = engineDir.resolve(target);
            }

            if (!allInDirectory) {
                result.add(mountPath(targetPath));
            } else if (Files.exists(targetPath)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetPath)) {
                    for (final Path path : stream) {
                        result.add(mountPath(path));
                    }
                }
            }
        }
        return result;
    }
}
