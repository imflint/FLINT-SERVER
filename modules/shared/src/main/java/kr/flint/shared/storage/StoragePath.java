package kr.flint.shared.storage;

import java.util.Set;

public interface StoragePath {

    String getPath();

    Set<String> getAllowedExtensions();

    default boolean isAllowedExtension(String extension) {
        return getAllowedExtensions().contains(extension.toLowerCase());
    }
}
