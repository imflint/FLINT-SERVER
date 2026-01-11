package kr.flint.infra.storage.enums;

import kr.flint.shared.storage.FileExtension;
import kr.flint.shared.storage.StoragePath;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

import static kr.flint.shared.storage.FileExtension.*;

@Getter
@RequiredArgsConstructor
public enum StoragePathType implements StoragePath {

    USER_PROFILE("user/profile", Extensions.IMAGE);

    private final String path;
    private final Set<FileExtension> allowedExtensions;

    private static class Extensions {
        static final Set<FileExtension> IMAGE = Set.of(JPG, JPEG, PNG);
    }
}