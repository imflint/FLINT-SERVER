package kr.flint.api.home;

import org.springframework.stereotype.Service;

@Service
public class HomeQueryFacade {

    // TODO: MVP에서는 apps:api에서 여러 모듈의 Service를 조합하여 구현
    // 추후 규모가 커지면 modules:home으로 분리 가능
    //
    // 주요 기능:
    // - 추천 컬렉션 조회 (CollectionService)
    // - 최근 북마크한 콘텐츠 조회 (BookmarkService + ContentService)
    // - 북마크한 컬렉션 조회 (BookmarkService + CollectionService)

}
