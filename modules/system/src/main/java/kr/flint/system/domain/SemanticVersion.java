package kr.flint.system.domain;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * x.y.z 세 자리 버전.
 *
 * <p>버전 비교 자체는 앱이 수행하지만, 서버는 어드민이 저장하는 값의 형식을 반드시 검증해야 한다.
 * 형식이 깨진 최소 버전이 저장되면 전체 사용자가 닫을 수 없는 팝업에 갇히기 때문이다.
 */
public record SemanticVersion(int major, int minor, int patch) implements Comparable<SemanticVersion> {

    private static final Pattern PATTERN = Pattern.compile("^(0|[1-9]\\d{0,8})\\.(0|[1-9]\\d{0,8})\\.(0|[1-9]\\d{0,8})$");

    /** 어떤 버전보다도 낮아 강제 업데이트를 발생시키지 않는 기본값 */
    public static final SemanticVersion ZERO = new SemanticVersion(0, 0, 0);

    public static Optional<SemanticVersion> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        var matcher = PATTERN.matcher(value.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new SemanticVersion(
            Integer.parseInt(matcher.group(1)),
            Integer.parseInt(matcher.group(2)),
            Integer.parseInt(matcher.group(3))
        ));
    }

    public static boolean isValid(String value) {
        return parse(value).isPresent();
    }

    /** 이 버전(최소 지원 버전)을 기준으로 clientVersion 이 강제 업데이트 대상인지 */
    public boolean requiresUpdateFrom(SemanticVersion clientVersion) {
        return clientVersion.compareTo(this) < 0;
    }

    @Override
    public int compareTo(SemanticVersion other) {
        if (major != other.major) {
            return Integer.compare(major, other.major);
        }
        if (minor != other.minor) {
            return Integer.compare(minor, other.minor);
        }
        return Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
