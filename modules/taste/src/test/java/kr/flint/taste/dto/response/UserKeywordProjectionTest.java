package kr.flint.taste.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import kr.flint.taste.domain.KeywordColor;
import kr.flint.taste.domain.KeywordLevel;

class UserKeywordProjectionTest {

	@Test
	@DisplayName("DB level 이 어긋나 있어도 색상은 카탈로그(키워드 이름) 기준으로 내려간다")
	void colorFollowsCatalogNotStoredLevel() {
		// 한국은 카탈로그에서 LV5(문화권) = ORANGE 인데 DB 에는 LV4 로 저장돼 있는 상황
		UserKeywordProjection projection = projection("한국", KeywordLevel.LV4, null);

		assertThat(projection.resolveColor()).isEqualTo(KeywordColor.ORANGE);
	}

	@Test
	@DisplayName("카탈로그에 없는 이름은 DB level 색상으로 떨어진다")
	void colorFallsBackToStoredLevelForUnknownName() {
		UserKeywordProjection projection = projection("카탈로그에 없는 키워드", KeywordLevel.LV2, null);

		assertThat(projection.resolveColor()).isEqualTo(KeywordColor.GREEN);
	}

	@Test
	@DisplayName("아이콘이 등록되지 않으면 레벨 기본 아이콘 키를 내려준다")
	void iconKeyFallsBackToLevelDefault() {
		assertThat(projection("한국", KeywordLevel.LV4, null).resolveIconKey())
			.isEqualTo(KeywordLevel.LV5.getDefaultIconKey());
		assertThat(projection("한국", KeywordLevel.LV4, "  ").resolveIconKey())
			.isEqualTo(KeywordLevel.LV5.getDefaultIconKey());
	}

	@Test
	@DisplayName("등록된 아이콘이 있으면 그대로 내려준다")
	void iconKeyUsesRegisteredImage() {
		assertThat(projection("한국", KeywordLevel.LV5, "keywords/logo/korea.png").resolveIconKey())
			.isEqualTo("keywords/logo/korea.png");
	}

	private UserKeywordProjection projection(String name, KeywordLevel level, String imageUrl) {
		return new UserKeywordProjection() {
			@Override public int getRanking() { return 1; }
			@Override public String getImageUrl() { return imageUrl; }
			@Override public KeywordLevel getLevel() { return level; }
			@Override public String getName() { return name; }
			@Override public Integer getPercentage() { return 50; }
		};
	}
}
