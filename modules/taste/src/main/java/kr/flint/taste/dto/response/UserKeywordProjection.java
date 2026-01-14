package kr.flint.taste.dto.response;

import kr.flint.taste.domain.KeywordLevel;

public interface UserKeywordProjection {
	int getRanking();

	String getImageUrl();

	KeywordLevel getLevel();

    String getName();

    Integer getPercentage();
}
