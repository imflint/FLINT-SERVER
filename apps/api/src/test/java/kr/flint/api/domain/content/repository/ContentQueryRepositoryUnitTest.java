package kr.flint.api.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import kr.flint.api.domain.content.dto.ContentSearchCursor;
import kr.flint.shared.exception.GeneralException;

class ContentQueryRepositoryUnitTest {

	@Test
	@DisplayName("cursor는 bookmarkCount와 contentId를 URL-safe token으로 인코딩한다")
	void encodesCursor() {
		ContentSearchCursor cursor = ContentSearchCursor.of(3, 801473411402740986L);

		assertThat(ContentSearchCursor.decode(cursor.encode()))
			.isEqualTo(cursor);
	}

	@Test
	@DisplayName("빈 cursor는 첫 페이지로 처리한다")
	void decodesBlankCursorAsFirstPage() {
		assertThat(ContentSearchCursor.decodeNullable(null)).isNull();
		assertThat(ContentSearchCursor.decodeNullable(" ")).isNull();
	}

	@Test
	@DisplayName("올바르지 않은 cursor는 예외를 던진다")
	void rejectsInvalidCursor() {
		assertThatThrownBy(() -> ContentSearchCursor.decode("invalid"))
			.isInstanceOf(GeneralException.class)
			.hasMessageContaining("cursor 형식이 올바르지 않습니다.");
	}

	@Test
	@DisplayName("cursor 직접 디코딩에서는 빈 값을 예외로 처리한다")
	void rejectsBlankCursorOnDirectDecode() {
		assertThatThrownBy(() -> ContentSearchCursor.decode(null))
			.isInstanceOf(GeneralException.class)
			.hasMessageContaining("cursor 형식이 올바르지 않습니다.");
	}

	@Test
	@DisplayName("음수 bookmarkCount cursor는 예외를 던진다")
	void rejectsNegativeBookmarkCount() {
		assertThatThrownBy(() -> ContentSearchCursor.of(-1, 1L))
			.isInstanceOf(GeneralException.class)
			.hasMessageContaining("cursor 형식이 올바르지 않습니다.");
	}
}
