package kr.flint.api.domain.home.port;

import java.util.List;

/**
 * 추천 컬렉션 조회 계약 (Recommendation Contract)
 * 추천 전략을 교체할 수 있도록 인터페이스로 정의
 */
public interface CollectionRecommendationPort {

    /**
     * 사용자에게 추천할 컬렉션 ID 목록 조회
     * @param userId 사용자 ID
     * @param maxSize 최대 추천 개수
     * @return 추천 컬렉션 ID 목록
     */
    List<Long> recommend(Long userId, int maxSize);
}
