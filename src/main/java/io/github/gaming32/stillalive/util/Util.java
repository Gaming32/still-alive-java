package io.github.gaming32.stillalive.util;

import com.connorhaigh.javavpk.core.Archive;
import com.connorhaigh.javavpk.core.Directory;
import com.connorhaigh.javavpk.core.Entry;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

public class Util {
    public static final BigDecimal TO_MILLIS = BigDecimal.valueOf(1000L);

    @Contract("null, _ -> null")
    public static <T, R> @Nullable R map(@Nullable T value, Function<T, R> mapper) {
        return value != null ? mapper.apply(value) : null;
    }

    public static Entry findEntry(Archive archive, String directory, String name, String extension) {
        return archive.getDirectories()
            .stream()
            .filter(d -> d.getPath().equals(directory))
            .map(Directory::getEntries)
            .flatMap(List::stream)
            .filter(e -> e.getFileName().equals(name) && e.getExtension().equals(extension))
            .findFirst()
            .orElse(null);
    }

    public static void startThread(String name, ThrowableRunnable action) {
        final Thread thread = new Thread(name) {
            @Override
            public void run() {
                try {
                    action.run();
                } catch (Exception e) {
                    getUncaughtExceptionHandler().uncaughtException(this, e);
                }
            }
        };
        thread.setDaemon(true);
        thread.start();
    }
}
