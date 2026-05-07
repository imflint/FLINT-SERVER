package kr.flint.api.domain.search.service;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import kr.flint.api.domain.search.dto.response.BookmarkedCollectionSearchRes;
import kr.flint.api.domain.search.dto.response.BookmarkedContentSearchRes;
import kr.flint.api.domain.search.repository.SearchQueryRepository;
import kr.flint.shared.dto.PaginationResponse;
import kr.flint.shared.dto.SliceCursor;
import kr.flint.api.domain.search.dto.response.GetContentSearchRes;
import kr.flint.content.domain.Content;
import kr.flint.content.service.ContentService;
import kr.flint.infra.storage.cloudfront.CloudFrontUrlProvider;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SearchQueryFacade {
	private final ContentService contentService;
	private final SearchQueryRepository searchQueryRepository;
	private final CloudFrontUrlProvider cloudFrontUrlProvider;

	public List<GetContentSearchRes> searchContent(final String keyword) {
		if (!StringUtils.hasText(keyword)) {
			List<Content> contentSearchResList = contentService.getAllContent();
			return contentSearchResList.stream()
				.map(GetContentSearchRes::from)
				.toList();
		}
		List<Content> contentList = contentService.getContentByTitle(keyword).isEmpty() ? List.of() : contentService.getContentByTitle(keyword);
		return contentList.stream()
			.map(GetContentSearchRes::from)
			.toList();
	}

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
		List<BookmarkedCollectionSearchRes> resolvedData = data.stream()
			.map(result -> new BookmarkedCollectionSearchRes(
				result.bookmarkId(),
				result.collectionId(),
				cloudFrontUrlProvider.resolveUrl(result.imageUrl()),
				result.title(),
				result.description()
			))
			.toList();
		String nextCursor = hasNext && !data.isEmpty()
			? String.valueOf(data.get(data.size() - 1).bookmarkId())
			: null;

		return PaginationResponse.ofCursor(SliceCursor.of(resolvedData, null, nextCursor));
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
