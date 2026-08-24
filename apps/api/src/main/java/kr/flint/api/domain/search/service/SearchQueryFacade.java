package kr.flint.api.domain.search.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import kr.flint.api.domain.home.dto.projection.CollectionContentImageDto;
import kr.flint.api.domain.home.repository.HomeCollectionRepository;
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
	private final HomeCollectionRepository homeCollectionRepository;
	private final CloudFrontUrlProvider cloudFrontUrlProvider;

	private static final int POPULAR_CONTENT_LIMIT = 30;

	public List<GetContentSearchRes> searchContent(final String keyword) {
		List<Content> contentList = StringUtils.hasText(keyword)
			? contentService.getContentByTitle(keyword)
			: contentService.getPopularContents(POPULAR_CONTENT_LIMIT);
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

		List<Long> collectionIds = data.stream().map(BookmarkedCollectionSearchRes::collectionId).toList();
		Map<Long, List<String>> contentImagesMap = buildContentImagesMap(collectionIds);

		List<BookmarkedCollectionSearchRes> resolvedData = data.stream()
			.map(r -> new BookmarkedCollectionSearchRes(
				r.bookmarkId(),
				r.collectionId(),
				cloudFrontUrlProvider.resolveUrl(r.imageUrl()),
				r.title(),
				r.description(),
				r.bookmarkCount(),
				r.isBookmarked(),
				r.userId(),
				r.nickname(),
				cloudFrontUrlProvider.resolveUrl(r.profileImageUrl()),
				contentImagesMap.getOrDefault(r.collectionId(), List.of()).stream()
					.map(cloudFrontUrlProvider::resolveUrl)
					.toList()
			))
			.toList();
		String nextCursor = hasNext && !data.isEmpty()
			? String.valueOf(data.get(data.size() - 1).bookmarkId())
			: null;

		return PaginationResponse.ofCursor(SliceCursor.of(resolvedData, null, nextCursor));
	}

	private Map<Long, List<String>> buildContentImagesMap(List<Long> collectionIds) {
		if (collectionIds.isEmpty()) {
			return Map.of();
		}
		List<CollectionContentImageDto> images = homeCollectionRepository.findContentImagesByCollectionIds(collectionIds);
		Map<Long, List<String>> map = new LinkedHashMap<>();
		for (CollectionContentImageDto img : images) {
			String image = img.poster();
			if (StringUtils.hasText(image)) {
				map.computeIfAbsent(img.collectionId(), k -> new ArrayList<>()).add(image);
			}
		}
		return map;
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
