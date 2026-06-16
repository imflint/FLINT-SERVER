package kr.flint.api.domain.content.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import kr.flint.api.domain.content.dto.ContentSearchCondition;
import kr.flint.api.domain.content.dto.ContentSearchCursor;
import kr.flint.api.domain.content.dto.GetBookmarkedContentCountRes;
import kr.flint.api.domain.content.dto.GetContentDetailRes;
import kr.flint.api.domain.content.dto.GetContentListRes;
import kr.flint.api.domain.content.dto.SearchGenre;
import kr.flint.api.domain.content.repository.ContentQueryRepository;
import kr.flint.api.domain.content.repository.ContentQueryRepository.BookmarkedContentRow;
import kr.flint.api.domain.content.repository.ContentQueryRepository.ContentSearchRow;
import kr.flint.api.domain.search.dto.response.GetContentSearchRes;
import kr.flint.bookmark.service.BookmarkQueryService;
import kr.flint.content.domain.MediaType;
import kr.flint.ott.dto.GetOttResponse;
import kr.flint.ott.service.OttService;
import kr.flint.shared.dto.PaginationResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentQueryFacade {
	private final OttService ottService;
	private final ContentQueryRepository contentQueryRepository;
	private final BookmarkQueryService bookmarkQueryService;

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

	public GetContentListRes getUserBookmarkedContentList(
		final Long currentUserId,
		final Long targetUserId
	) {
		List<GetContentDetailRes> contentList = getContentDetailList(targetUserId);
		if (contentList.isEmpty()) {
			return GetContentListRes.from(List.of(), Set.of());
		}

		List<Long> contentIds = contentList.stream()
			.map(GetContentDetailRes::id)
			.toList();
		Set<Long> bookmarkedContentIds = bookmarkQueryService.getBookmarkedContentIdSet(currentUserId, contentIds);
		return GetContentListRes.from(contentList, bookmarkedContentIds);
	}

	public PaginationResponse<GetContentDetailRes> getBookmarkedContentList(
		final Long userId,
		final Long cursor,
		final int size
	) {
		List<BookmarkedContentRow> rows = contentQueryRepository.getBookmarkedContentRows(userId, cursor, size + 1);
		boolean hasNext = rows.size() > size;
		List<BookmarkedContentRow> pageRows = hasNext ? rows.subList(0, size) : rows;
		List<GetContentDetailRes> data = pageRows.stream()
			.map(BookmarkedContentRow::toResponse)
			.toList();
		String nextCursor = hasNext && !pageRows.isEmpty()
			? String.valueOf(pageRows.get(pageRows.size() - 1).bookmarkId())
			: null;

		return PaginationResponse.ofCursor(data, nextCursor);
	}

	public GetBookmarkedContentCountRes getBookmarkedContentCount(final Long userId) {
		return GetBookmarkedContentCountRes.from(bookmarkQueryService.getContentBookmarkCount(userId));
	}

	public PaginationResponse<GetContentSearchRes> getContentSearchList(
		final String keyword,
		final List<SearchGenre> genres,
		final MediaType mediaType,
		final String cursor,
		final int size
	) {
		String normalizedKeyword = normalizeKeyword(keyword);
		List<String> genreNames = toGenreNames(genres);
		ContentSearchCursor decodedCursor = ContentSearchCursor.decodeNullable(cursor);
		ContentSearchCondition condition = ContentSearchCondition.of(
			normalizedKeyword,
			genreNames,
			mediaType,
			decodedCursor,
			size
		);
		List<ContentSearchRow> page =
			contentQueryRepository.searchContents(condition);
		boolean hasNext = page.size() > size;
		List<ContentSearchRow> rows = hasNext ? page.subList(0, size) : page;
		List<GetContentSearchRes> data = rows.stream()
			.map(ContentSearchRow::toResponse)
			.toList();
		String nextCursor = hasNext ? createNextCursor(rows) : null;
		return PaginationResponse.ofCursor(data, nextCursor);
	}

	private String createNextCursor(List<ContentSearchRow> rows) {
		ContentSearchRow last = rows.get(rows.size() - 1);
		return ContentSearchCursor.of(last.bookmarkCount(), last.id()).encode();
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
