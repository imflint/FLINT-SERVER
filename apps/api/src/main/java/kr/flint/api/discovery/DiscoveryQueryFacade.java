package kr.flint.api.discovery;

import org.springframework.stereotype.Service;

@Service
public class DiscoveryQueryFacade {

    // TODO: MVP에서는 apps:api에서 여러 모듈의 Service를 조합하여 구현
    // 추후 규모가 커지면 modules:discovery로 분리 가능
    //
    // 주요 기능:
    // - 공개 컬렉션 랜덤/샘플링 조회 (CollectionService)
    // - 추천 컬렉션 조회 (CollectionService + 추천 로직)

}
