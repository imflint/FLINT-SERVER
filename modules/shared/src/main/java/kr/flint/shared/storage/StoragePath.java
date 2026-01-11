package kr.flint.shared.storage;

import java.util.Set;

public interface StoragePath {

    String getPath();

    Set<FileExtension> getAllowedExtensions();
}
