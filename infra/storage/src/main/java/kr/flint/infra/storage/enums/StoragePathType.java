package kr.flint.infra.storage.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.shared.storage.FileExtension;
import kr.flint.shared.storage.StoragePath;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

import static kr.flint.shared.storage.FileExtension.*;

@Schema(
    description = """
        S3 저장 경로 타입
        - USER_PROFILE: 사용자 프로필 이미지 (허용: JPG, JPEG, PNG)
        - LOGO_IMAGE: 키워드 로고 이미지 (허용: JPG, JPEG, PNG, SVG)
        - COLLECTION_THUMBNAIL: 컬렉션 대표 이미지 (허용: JPG, JPEG, PNG)
        - COLLECTION_CONTENT: 컬렉션 작품별 커스텀 이미지 (허용: JPG, JPEG, PNG)
        """,
    enumAsRef = true
)
@Getter
@RequiredArgsConstructor
public enum StoragePathType implements StoragePath {
    USER_PROFILE("user/profile", Extensions.IMAGE),
    LOGO_IMAGE("keywords/logo", Extensions.LOGO),
    COLLECTION_THUMBNAIL("collection/thumbnail", Extensions.IMAGE),
    COLLECTION_CONTENT("collection/content", Extensions.IMAGE),
    ;

    private final String path;
    private final Set<FileExtension> allowedExtensions;

    private static class Extensions {
        static final Set<FileExtension> IMAGE = Set.of(JPG, JPEG, PNG);
        static final Set<FileExtension> LOGO = Set.of(JPG, JPEG, PNG, SVG);
    }
}
