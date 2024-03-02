package io.github.gaming32.stillalive.source;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

@FunctionalInterface
public interface ResourceFinder {
    ResourceFinder NULL = (directory, name, extension) -> null;

    byte @Nullable [] findResource(String directory, String name, String extension) throws IOException;

    static ResourceFinder sequential(ResourceFinder... finders) {
        return sequential(List.of(finders));
    }

    static ResourceFinder sequential(List<ResourceFinder> finders) {
        return switch (finders.size()) {
            case 0 -> NULL;
            case 1 -> finders.get(0);
            default -> (directory, name, extension) -> {
                for (final ResourceFinder finder : finders) {
                    final byte[] resource = finder.findResource(directory, name, extension);
                    if (resource != null) {
                        return resource;
                    }
                }
                return null;
            };
        };
    }
}
