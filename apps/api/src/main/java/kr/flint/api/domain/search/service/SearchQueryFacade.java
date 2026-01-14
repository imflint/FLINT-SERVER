package kr.flint.api.domain.search.service;

import java.util.List;

import org.springframework.stereotype.Component;

import kr.flint.api.domain.search.dto.response.BookmarkedCollectionSearchRes;
import kr.flint.api.domain.search.dto.response.BookmarkedContentSearchRes;
import kr.flint.api.domain.search.repository.SearchQueryRepository;
import kr.flint.shared.dto.PaginationResponse;
import kr.flint.shared.dto.SliceCursor;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SearchQueryFacade {

	private final SearchQueryRepository searchQueryRepository;

	public PaginationResponse<BookmarkedCollectionSearchRes> searchBookmarkedCollections(
		final Long userId,
		final String keyword,
		final Long cursor,
		final int size
	) {
		List<BookmarkedCollectionSearchRes> results =
			searchQueryRepository.searchBookmarkedCollections(userId, keyword, cursor, size);

		boolean hasNext = results.size() > size;
		List<BookmarkedCollectionSearchRes> data = hasNext ? results.subList(0, size) : results;
		String nextCursor = hasNext && !data.isEmpty()
			? String.valueOf(data.get(data.size() - 1).bookmarkId())
			: null;

		return PaginationResponse.ofCursor(SliceCursor.of(data, null, nextCursor));
	}

	public PaginationResponse<BookmarkedContentSearchRes> searchBookmarkedContents(
		final Long userId,
		final String keyword,
		final Long cursor,
		final int size
	) {
		List<BookmarkedContentSearchRes> results =
			searchQueryRepository.searchBookmarkedContents(userId, keyword, cursor, size);

		boolean hasNext = results.size() > size;
		List<BookmarkedContentSearchRes> data = hasNext ? results.subList(0, size) : results;
		String nextCursor = hasNext && !data.isEmpty()
			? String.valueOf(data.get(data.size() - 1).bookmarkId())
			: null;

		return PaginationResponse.ofCursor(SliceCursor.of(data, null, nextCursor));
	}
}
