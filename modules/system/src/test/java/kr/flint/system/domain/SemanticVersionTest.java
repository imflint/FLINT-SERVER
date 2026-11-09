package kr.flint.system.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SemanticVersionTest {

	@ParameterizedTest
	@DisplayName("x.y.z 형식만 파싱한다")
	@ValueSource(strings = {"0.0.0", "1.2.3", "10.0.1", " 1.2.3 "})
	void parsesValidVersions(String value) {
		assertThat(SemanticVersion.isValid(value)).isTrue();
	}

	@ParameterizedTest
	@DisplayName("형식이 어긋난 값은 거부한다")
	@ValueSource(strings = {"1.2", "1.2.3.4", "v1.2.3", "1.2.x", "1..3", "1.2.3-beta", "01.2.3", "", " "})
	void rejectsInvalidVersions(String value) {
		assertThat(SemanticVersion.isValid(value)).isFalse();
		assertThat(SemanticVersion.parse(value)).isEmpty();
	}

	@ParameterizedTest
	@DisplayName("null 도 거부한다")
	@NullAndEmptySource
	void rejectsNull(String value) {
		assertThat(SemanticVersion.isValid(value)).isFalse();
	}

	@ParameterizedTest
	@DisplayName("자리별로 숫자 비교한다 (문자열 비교가 아니다)")
	@CsvSource({
		"1.2.3, 1.2.3, 0",
		"1.2.4, 1.2.3, 1",
		"1.2.3, 1.2.4, -1",
		"1.10.0, 1.9.0, 1",
		"2.0.0, 1.99.99, 1",
		"1.0.0, 0.99.99, 1"
	})
	void comparesNumerically(String left, String right, int expectedSign) {
		int actual = SemanticVersion.parse(left).orElseThrow()
			.compareTo(SemanticVersion.parse(right).orElseThrow());
		assertThat(Integer.signum(actual)).isEqualTo(expectedSign);
	}

	@ParameterizedTest
	@DisplayName("최소 지원 버전보다 낮은 앱만 강제 업데이트 대상")
	@CsvSource({
		"1.2.3, 1.2.2, true",
		"1.2.3, 1.2.3, false",
		"1.2.3, 1.3.0, false",
		"1.2.3, 0.9.9, true",
		"0.0.0, 0.0.0, false"
	})
	void decidesUpdateRequirement(String minSupported, String clientVersion, boolean expected) {
		SemanticVersion min = SemanticVersion.parse(minSupported).orElseThrow();
		SemanticVersion client = SemanticVersion.parse(clientVersion).orElseThrow();

		assertThat(min.requiresUpdateFrom(client)).isEqualTo(expected);
	}

	@Test
	@DisplayName("ZERO 는 어떤 버전도 강제 업데이트시키지 않는다")
	void zeroNeverForcesUpdate() {
		assertThat(SemanticVersion.ZERO.requiresUpdateFrom(SemanticVersion.ZERO)).isFalse();
		assertThat(SemanticVersion.ZERO.requiresUpdateFrom(new SemanticVersion(1, 0, 0))).isFalse();
	}

	@Test
	@DisplayName("toString 은 정규화된 x.y.z")
	void normalizesToString() {
		assertThat(SemanticVersion.parse(" 1.2.3 ").orElseThrow()).hasToString("1.2.3");
	}
}
