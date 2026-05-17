package kr.flint.admin.domain.content.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.admin.common.AdminAuthorizationService;
import kr.flint.admin.domain.content.dto.request.AdminContentUpdateReq;
import kr.flint.admin.domain.content.dto.response.AdminContentRes;
import kr.flint.admin.domain.content.repository.AdminContentQueryRepository;
import kr.flint.admin.domain.content.repository.AdminContentQueryRepository.ContentGenreRow;
import kr.flint.admin.domain.content.repository.AdminContentQueryRepository.ContentRow;
import kr.flint.content.domain.Content;
import kr.flint.content.domain.MediaType;
import kr.flint.content.dto.ContentWithGenres;
import kr.flint.content.service.ContentService;
import kr.flint.infra.storage.cloudfront.CloudFrontUrlProvider;
import kr.flint.shared.dto.PaginationResponse;
import kr.flint.shared.dto.SliceCursor;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminContentFacade {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final AdminAuthorizationService adminAuthorizationService;
    private final ContentService contentService;
    private final AdminContentQueryRepository queryRepository;
    private final CloudFrontUrlProvider cloudFrontUrlProvider;

    public PaginationResponse<AdminContentRes> getContents(
        Long adminId,
        String keyword,
        MediaType mediaType,
        Long cursor,
        Integer size
    ) {
        adminAuthorizationService.validateAdmin(adminId);
        int safeSize = normalizeSize(size);
        List<Long> contentIds = queryRepository.findContentIds(keyword, mediaType, cursor, safeSize);
        boolean hasNext = contentIds.size() > safeSize;
        List<Long> pageIds = hasNext ? contentIds.subList(0, safeSize) : contentIds;

        Map<Long, ContentRow> contentRows = queryRepository.findContentRows(pageIds)
            .stream()
            .collect(Collectors.toMap(ContentRow::id, Function.identity()));
        Map<Long, List<String>> genreNames = queryRepository.findGenreRows(pageIds)
            .stream()
            .collect(Collectors.groupingBy(
                ContentGenreRow::contentId,
                Collectors.mapping(ContentGenreRow::genreName, Collectors.toList())
            ));

        List<AdminContentRes> data = pageIds.stream()
            .map(contentRows::get)
            .filter(row -> row != null)
            .map(row -> toContentRes(row, genreNames.getOrDefault(row.id(), List.of())))
            .toList();
        String nextCursor = hasNext && !data.isEmpty() ? String.valueOf(data.getLast().id()) : "";
        String currentCursor = cursor != null ? String.valueOf(cursor) : null;
        return PaginationResponse.ofCursor(SliceCursor.of(data, currentCursor, nextCursor));
    }

    @Transactional
    public AdminContentRes updateContent(Long adminId, Long contentId, AdminContentUpdateReq request) {
        adminAuthorizationService.validateAdmin(adminId);
        Content content = contentService.updateByAdmin(contentId, request.toCommand());
        List<String> genreNames = contentService.getContentsWithGenres(List.of(content.getId()))
            .stream()
            .findFirst()
            .map(ContentWithGenres::genreList)
            .orElse(List.of());
        return AdminContentRes.from(content, genreNames, resolveNullableImage(content.getPoster()));
    }

    private AdminContentRes toContentRes(ContentRow row, List<String> genreNames) {
        return new AdminContentRes(
            row.id(),
            row.tmdbId(),
            row.mediaType(),
            row.title(),
            row.year(),
            row.author(),
            row.description(),
            resolveNullableImage(row.poster()),
            row.bookmarkCount(),
            genreNames
        );
    }

    private String resolveNullableImage(String imageUrl) {
        return imageUrl == null ? null : cloudFrontUrlProvider.resolveUrl(imageUrl);
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
