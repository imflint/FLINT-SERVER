package kr.flint.system.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.flint.shared.domain.BaseTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 플랫폼별 강제 업데이트 정책. 플랫폼당 한 행만 존재한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "app_versions",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_app_version_platform", columnNames = {"platform"})
    }
)
public class AppVersion extends BaseTime {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, unique = true)
    private AppPlatform platform;

    /** 최소 지원 버전(x.y.z). 앱 버전이 이보다 낮으면 강제 업데이트 대상이다. */
    @Column(name = "min_supported_version", nullable = false, length = 32)
    private String minSupportedVersion;

    /** 스토어 이동 주소 */
    @Column(name = "store_path", nullable = false, length = 500)
    private String storePath;

    @Builder(access = AccessLevel.PRIVATE)
    private AppVersion(AppPlatform platform, String minSupportedVersion, String storePath) {
        this.platform = platform;
        this.minSupportedVersion = minSupportedVersion;
        this.storePath = storePath;
    }

    public static AppVersion create(AppPlatform platform, String minSupportedVersion, String storePath) {
        return AppVersion.builder()
            .platform(platform)
            .minSupportedVersion(normalizeVersion(minSupportedVersion))
            .storePath(normalizePath(storePath))
            .build();
    }

    public void change(String minSupportedVersion, String storePath) {
        this.minSupportedVersion = normalizeVersion(minSupportedVersion);
        this.storePath = normalizePath(storePath);
    }

    public SemanticVersion minSupported() {
        // 저장 시 검증을 통과한 값만 들어오므로 파싱 실패는 발생하지 않는다
        return SemanticVersion.parse(minSupportedVersion).orElse(SemanticVersion.ZERO);
    }

    /** 운영/테스트용 판정 헬퍼. 실제 팝업 노출 판단은 앱이 한다. */
    public boolean requiresUpdate(String clientVersion) {
        return SemanticVersion.parse(clientVersion)
            .map(version -> minSupported().requiresUpdateFrom(version))
            .orElse(false);
    }

    private static String normalizeVersion(String value) {
        return SemanticVersion.parse(value)
            .map(SemanticVersion::toString)
            .orElseThrow(() -> new IllegalArgumentException("최소 지원 버전은 x.y.z 형식이어야 합니다: " + value));
    }

    private static String normalizePath(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("스토어 주소는 필수입니다.");
        }
        return value.trim();
    }
}
