package kr.flint.admin.domain.content.service;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.admin.common.AdminAuthorizationService;
import kr.flint.admin.domain.content.dto.request.AdminContentUpdateReq;
import kr.flint.admin.domain.content.dto.response.AdminContentRes;
import kr.flint.content.domain.Content;
import kr.flint.content.dto.ContentWithGenres;
import kr.flint.content.service.ContentService;
import kr.flint.infra.storage.cloudfront.CloudFrontUrlProvider;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminContentFacade {

    private final AdminAuthorizationService adminAuthorizationService;
    private final ContentService contentService;
    private final CloudFrontUrlProvider cloudFrontUrlProvider;

    @Transactional
    public AdminContentRes updateContent(Long adminUserId, Long contentId, AdminContentUpdateReq request) {
        adminAuthorizationService.validateAdmin(adminUserId);
        Content content = contentService.updateByAdmin(contentId, request.toCommand());
        List<String> genreNames = contentService.getContentsWithGenres(List.of(content.getId()))
            .stream()
            .findFirst()
            .map(ContentWithGenres::genreList)
            .orElse(List.of());
        return AdminContentRes.from(content, genreNames, resolveNullableImage(content.getPoster()));
    }

    private String resolveNullableImage(String imageUrl) {
        return imageUrl == null ? null : cloudFrontUrlProvider.resolveUrl(imageUrl);
    }
}
