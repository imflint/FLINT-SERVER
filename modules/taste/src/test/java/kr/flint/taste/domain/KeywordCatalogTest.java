package kr.flint.taste.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class KeywordCatalogTest {

	@Test
	@DisplayName("모든 레벨이 키워드를 가지고 이름은 전체에서 유일하다")
	void catalogHasUniqueNamesForEveryLevel() {
		for (KeywordLevel level : KeywordLevel.values()) {
			assertThat(KeywordCatalog.namesOf(level)).isNotEmpty();
		}
		List<String> names = KeywordCatalog.entries().stream().map(KeywordCatalog.Entry::name).toList();
		assertThat(names).doesNotHaveDuplicates();
		assertThat(names).hasSize(KeywordCatalog.size());
	}

	@Test
	@DisplayName("카탈로그의 모든 키워드는 자기 레벨로 다시 찾아진다")
	void everyCatalogNameResolvesToItsOwnLevel() {
		for (KeywordCatalog.Entry entry : KeywordCatalog.entries()) {
			assertThat(KeywordCatalog.canonicalize(entry.name())).contains(entry.name());
			assertThat(KeywordCatalog.findLevel(entry.name())).contains(entry.level());
			assertThat(KeywordCatalog.colorOf(entry.name(), null)).isEqualTo(entry.level().getColor());
		}
	}

	@ParameterizedTest
	@DisplayName("공백/대소문자/별칭/영문 표기가 흔들려도 정식 이름으로 복구된다")
	@CsvSource({
		"' 드라마 ', 드라마",
		"'로맨 스', 로맨스",
		"'sf', SF",
		"'Sf', SF",
		"'공포', 호러",
		"'애니', 애니메이션",
		"'미국', 북미",
		"'Korea', 한국",
		"'노년', 노년의 삶",
		"'노년의삶', 노년의 삶",
		"'실화기반', 실화 기반",
		"'사회 문제', 사회문제",
		"'긴장감 있는', 긴장되는",
		"'포스트 아포칼립스', 아포칼립스",
		"'ACTION', 액션"
	})
	void canonicalizesDriftedNames(String rawName, String expected) {
		assertThat(KeywordCatalog.canonicalize(rawName)).contains(expected);
	}

	@Test
	@DisplayName("카탈로그에 없는 이름은 fallback 레벨로 떨어진다")
	void unknownNameFallsBackToGivenLevel() {
		assertThat(KeywordCatalog.canonicalize("존재하지 않는 키워드")).isEmpty();
		assertThat(KeywordCatalog.levelOf("존재하지 않는 키워드", KeywordLevel.LV3)).isEqualTo(KeywordLevel.LV3);
		assertThat(KeywordCatalog.levelOf("존재하지 않는 키워드", null)).isEqualTo(KeywordLevel.LV1);
		assertThat(KeywordCatalog.canonicalize(null)).isEmpty();
		assertThat(KeywordCatalog.canonicalize("   ")).isEmpty();
	}

	@Test
	@DisplayName("레벨별 색상은 서로 겹치지 않는다")
	void levelColorsAreDistinct() {
		List<KeywordColor> colors = List.of(KeywordLevel.values()).stream()
			.map(KeywordLevel::getColor)
			.toList();
		assertThat(colors).doesNotHaveDuplicates();
	}
}
