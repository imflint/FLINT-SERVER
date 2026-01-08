package kr.flint.infra.storage.enums;

import kr.flint.shared.storage.StoragePath;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

@Getter
@RequiredArgsConstructor
public enum StoragePathType implements StoragePath {

    USER_PROFILE("user/profile", Extensions.IMAGE);

    private final String path;
    private final Set<String> allowedExtensions;

    private static class Extensions {
        static final Set<String> IMAGE = Set.of("jpg", "jpeg", "png");
    }
}