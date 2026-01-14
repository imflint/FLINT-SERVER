package kr.flint.api.domain.home.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.flint.api.domain.home.HomeQueryFacade;
import kr.flint.api.domain.home.controller.spec.HomeControllerDocs;
import kr.flint.api.domain.home.dto.response.RecommendedCollectionsRes;
import kr.flint.api.global.security.annotation.CurrentUser;
import kr.flint.shared.dto.response.SuccessCode;
import kr.flint.shared.dto.response.SuccessResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/home")
@RequiredArgsConstructor
public class HomeController implements HomeControllerDocs {

    private final HomeQueryFacade homeQueryFacade;

    @Override
    @GetMapping("/recommended-collections")
    public ResponseEntity<SuccessResponse<RecommendedCollectionsRes>> getRecommendedCollections(
        @CurrentUser Long userId
    ) {
        return ResponseEntity.ok(
            SuccessResponse.of(
                SuccessCode.SUCCESS_RECOMMENDED_COLLECTIONS_FETCH,
                homeQueryFacade.getRecommendedCollections(userId)
            )
        );
    }
}
