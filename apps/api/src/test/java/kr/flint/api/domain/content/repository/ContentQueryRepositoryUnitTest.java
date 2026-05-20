package kr.flint.api.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import kr.flint.shared.exception.GeneralException;

class ContentQueryRepositoryUnitTest {

	private final ContentQueryRepository contentQueryRepository = new ContentQueryRepository(null);

	@Test
	@DisplayName("page가 1보다 작으면 검색하지 않고 예외를 던진다")
	void rejectsInvalidPage() {
		assertThatThrownBy(() -> contentQueryRepository.searchContents(null, List.of(), null, 0, 20))
			.isInstanceOf(GeneralException.class)
			.hasMessageContaining("page는 1 이상이어야 합니다.");
	}

	@Test
	@DisplayName("size가 1보다 작으면 검색하지 않고 예외를 던진다")
	void rejectsInvalidSize() {
		assertThatThrownBy(() -> contentQueryRepository.searchContents(null, List.of(), null, 1, 0))
			.isInstanceOf(GeneralException.class)
			.hasMessageContaining("size는 1 이상이어야 합니다.");
	}
}
