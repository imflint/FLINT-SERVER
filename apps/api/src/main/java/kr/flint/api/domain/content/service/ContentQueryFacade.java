package kr.flint.api.domain.content.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import kr.flint.api.domain.content.dto.ContentSearchCondition;
import kr.flint.api.domain.content.dto.GetContentDetailRes;
import kr.flint.api.domain.content.dto.SearchGenre;
import kr.flint.api.domain.content.repository.ContentQueryRepository;
import kr.flint.api.domain.content.repository.ContentQueryRepository.ContentSearchRow;
import kr.flint.api.domain.search.dto.response.GetContentSearchRes;
import kr.flint.content.domain.MediaType;
import kr.flint.ott.dto.GetOttResponse;
import kr.flint.ott.service.OttService;
import kr.flint.shared.dto.PaginationResponse;
import kr.flint.shared.exception.ErrorCode;
import kr.flint.shared.exception.GeneralException;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentQueryFacade {
	private static final int MAX_SEARCH_SIZE = 50;

	private final OttService ottService;
	private final ContentQueryRepository contentQueryRepository;

	public List<GetOttResponse> getOttList(final Long userId, final Long contentId) {
		List<GetOttResponse> ottList = ottService.getOttList(userId, contentId);
		return ottList.isEmpty() ? List.of() : ottList;
	}

	public List<GetContentDetailRes> getContentDetailList(final Long userId) {
		List<GetContentDetailRes> contentList = contentQueryRepository.getContentDetailList(userId);
		if (contentList.isEmpty()) {
			return new ArrayList<>();
		}
		return contentList;
	}

	public PaginationResponse<GetContentSearchRes> getContentSearchList(
		final String keyword,
		final List<SearchGenre> genres,
		final MediaType mediaType,
		final int cursor,
		final int size
	) {
		validateSearchRequest(cursor, size);
		String normalizedKeyword = normalizeKeyword(keyword);
		List<String> genreNames = toGenreNames(genres);
		ContentSearchCondition condition = ContentSearchCondition.of(
			normalizedKeyword,
			genreNames,
			mediaType,
			cursor,
			size
		);
		List<ContentSearchRow> page =
			contentQueryRepository.searchContents(condition);
		boolean hasNext = page.size() > size;
		List<ContentSearchRow> rows = hasNext ? page.subList(0, size) : page;
		List<GetContentSearchRes> data = rows.stream()
			.map(ContentSearchRow::toResponse)
			.toList();
		String nextCursor = hasNext ? String.valueOf(cursor + 1) : null;
		return PaginationResponse.ofCursor(data, nextCursor);
	}

	private void validateSearchRequest(int cursor, int size) {
		if (cursor < 1) {
			throw new GeneralException(ErrorCode.INVALID_INPUT, "cursor는 1 이상이어야 합니다.");
		}
		if (size < 1) {
			throw new GeneralException(ErrorCode.INVALID_INPUT, "size는 1 이상이어야 합니다.");
		}
		if (size > MAX_SEARCH_SIZE) {
			throw new GeneralException(ErrorCode.INVALID_INPUT, "size는 50 이하여야 합니다.");
		}
	}

	private String normalizeKeyword(String keyword) {
		if (!StringUtils.hasText(keyword)) {
			return null;
		}
		return keyword.trim();
	}

	private List<String> toGenreNames(List<SearchGenre> genres) {
		if (genres == null || genres.isEmpty()) {
			return List.of();
		}

		return genres.stream()
			.filter(Objects::nonNull)
			.map(SearchGenre::genreName)
			.distinct()
			.toList();
	}

}
