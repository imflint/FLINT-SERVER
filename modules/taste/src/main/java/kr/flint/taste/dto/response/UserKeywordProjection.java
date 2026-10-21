package kr.flint.taste.dto.response;

import kr.flint.taste.domain.KeywordCatalog;
import kr.flint.taste.domain.KeywordColor;
import kr.flint.taste.domain.KeywordLevel;

public interface UserKeywordProjection {
	int getRanking();

	String getImageUrl();

	KeywordLevel getLevel();

    String getName();

    Integer getPercentage();

	/**
	 * 색상은 카탈로그(키워드 이름)가 정본이고, 카탈로그에 없는 이름만 DB level 로 떨어진다.
	 * DB level 이 어긋나 있어도 응답 색상은 흔들리지 않는다.
	 */
	default KeywordColor resolveColor() {
		return KeywordCatalog.colorOf(getName(), getLevel());
	}

	/** 키워드 아이콘이 등록되지 않았으면 레벨 기본 아이콘 키로 대체한다. 절대 null 이 아니다. */
	default String resolveIconKey() {
		String imageUrl = getImageUrl();
		if (imageUrl != null && !imageUrl.isBlank()) {
			return imageUrl;
		}
		return KeywordCatalog.levelOf(getName(), getLevel()).getDefaultIconKey();
	}
}
