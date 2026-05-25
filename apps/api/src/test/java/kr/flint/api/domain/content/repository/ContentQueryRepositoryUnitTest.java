package kr.flint.api.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import kr.flint.api.domain.content.dto.ContentSearchCondition;
import kr.flint.api.domain.content.repository.ContentQueryRepository.ContentSearchRow;

class ContentQueryRepositoryUnitTest {

	private final NamedParameterJdbcTemplate namedParameterJdbcTemplate =
		org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
	private final ContentQueryRepository contentQueryRepository =
		new ContentQueryRepository(null, namedParameterJdbcTemplate);

	@Test
	@DisplayName("cursor 페이지 번호를 offset 파라미터로 변환한다")
	void usesPageCursorAsOffset() {
		// given
		givenEmptyResult();

		// when
		contentQueryRepository.searchContents(ContentSearchCondition.of(null, List.of(), null, 3, 20));

		// then
		QueryCall queryCall = captureQueryCall();
		assertThat(queryCall.params().getValue("limit")).isEqualTo(21);
		assertThat(queryCall.params().getValue("offset")).isEqualTo(40L);
	}

	@Test
	@DisplayName("2자 이상 keyword는 FULLTEXT 검색을 사용한다")
	void usesFullTextForKeyword() {
		// given
		givenEmptyResult();

		// when
		contentQueryRepository.searchContents(ContentSearchCondition.of("눈물", List.of(), null, 1, 20));

		// then
		QueryCall queryCall = captureQueryCall();
		assertThat(queryCall.sql()).contains("MATCH(c.title) AGAINST");
		assertThat(queryCall.params().getValue("keyword")).isEqualTo("눈물");
	}

	@Test
	@DisplayName("중복 장르명은 정규화 후 단일 조건으로 조회한다")
	void deduplicatesGenreNames() {
		// given
		givenEmptyResult();

		// when
		contentQueryRepository.searchContents(ContentSearchCondition.of(null, List.of("액션", " 액션 "), null, 1, 20));

		// then
		QueryCall queryCall = captureQueryCall();
		assertThat(queryCall.params().getValue("genreNames")).isEqualTo(List.of("액션"));
		assertThat(queryCall.params().getValue("genreCount")).isEqualTo(1);
	}

	@Test
	@DisplayName("1자 keyword는 기존 호환을 위해 LIKE 검색을 사용한다")
	void usesLikeFallbackForOneCharacterKeyword() {
		// given
		givenEmptyResult();

		// when
		contentQueryRepository.searchContents(ContentSearchCondition.of("눈", List.of(), null, 1, 20));

		// then
		QueryCall queryCall = captureQueryCall();
		assertThat(queryCall.sql()).contains("c.title LIKE");
		assertThat(queryCall.sql()).doesNotContain("MATCH(c.title) AGAINST");
		assertThat(queryCall.params().getValue("keyword")).isEqualTo("눈");
	}

	private void givenEmptyResult() {
		when(namedParameterJdbcTemplate.query(
			anyString(),
			any(MapSqlParameterSource.class),
			anyRowMapper()
		)).thenReturn(List.of());
	}

	private QueryCall captureQueryCall() {
		ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
		verify(namedParameterJdbcTemplate).query(
			sqlCaptor.capture(),
			paramsCaptor.capture(),
			anyRowMapper()
		);
		return new QueryCall(sqlCaptor.getValue(), paramsCaptor.getValue());
	}

	@SuppressWarnings("unchecked")
	private RowMapper<ContentSearchRow> anyRowMapper() {
		return any(RowMapper.class);
	}

	private record QueryCall(String sql, MapSqlParameterSource params) {
	}
}
