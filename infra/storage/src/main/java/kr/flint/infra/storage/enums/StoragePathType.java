package kr.flint.infra.storage.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.shared.storage.FileExtension;
import kr.flint.shared.storage.StoragePath;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

import static kr.flint.shared.storage.FileExtension.*;

@Schema(description = "S3 저장 경로 타입", enumAsRef = true)
@Getter
@RequiredArgsConstructor
public enum StoragePathType implements StoragePath {

    @Schema(description = "사용자 프로필 이미지 (허용: JPG, JPEG, PNG)")
    USER_PROFILE("user/profile", Extensions.IMAGE);

    private final String path;
    private final Set<FileExtension> allowedExtensions;

    private static class Extensions {
        static final Set<FileExtension> IMAGE = Set.of(JPG, JPEG, PNG);
    }
}