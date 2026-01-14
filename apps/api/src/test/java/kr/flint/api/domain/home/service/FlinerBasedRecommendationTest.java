package kr.flint.api.domain.home.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.flint.api.domain.home.dto.projection.CollectionBasicProjection;
import kr.flint.api.domain.home.repository.HomeCollectionRepository;
import kr.flint.collection.domain.CollectionKeyword;
import kr.flint.collection.repository.CollectionKeywordRepository;
import kr.flint.taste.domain.UserKeyword;
import kr.flint.taste.repository.UserKeywordRepository;

@ExtendWith(MockitoExtension.class)
class FlinerBasedRecommendationTest {

    @Mock
    private UserKeywordRepository userKeywordRepository;

    @Mock
    private CollectionKeywordRepository collectionKeywordRepository;

    @Mock
    private HomeCollectionRepository homeCollectionRepository;

    @InjectMocks
    private FlinerBasedRecommendation recommendation;

    private static final Long USER_ID = 1L;
    private static final Long FLINER_1 = 100L;
    private static final Long FLINER_2 = 200L;
    private static final Long COLLECTION_1 = 1000L;
    private static final Long COLLECTION_2 = 2000L;
    private static final Long KEYWORD_1 = 10L;
    private static final Long KEYWORD_2 = 20L;
    private static final Long KEYWORD_3 = 30L;

    @Nested
    @DisplayName("recommend")
    class Recommend {

        @Test
        @DisplayName("사용자 키워드가 없으면 빈 목록 반환")
        void emptyUserKeywords() {
            // given
            when(userKeywordRepository.findKeywordIdsByUserId(USER_ID)).thenReturn(List.of());

            // when
            List<Long> result = recommendation.recommend(USER_ID, 10);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Fliner가 없으면 빈 목록 반환")
        void noFliners() {
            // given
            when(userKeywordRepository.findKeywordIdsByUserId(USER_ID)).thenReturn(List.of(KEYWORD_1, KEYWORD_2));
            when(homeCollectionRepository.findAllFlinerIds()).thenReturn(List.of());

            // when
            List<Long> result = recommendation.recommend(USER_ID, 10);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Fliner와 키워드 매칭이 없으면 빈 목록 반환")
        void noMatching() {
            // given
            when(userKeywordRepository.findKeywordIdsByUserId(USER_ID)).thenReturn(List.of(KEYWORD_1));
            when(homeCollectionRepository.findAllFlinerIds()).thenReturn(List.of(FLINER_1));
            when(userKeywordRepository.findByUserIdIn(List.of(FLINER_1))).thenReturn(List.of(
                UserKeyword.create(FLINER_1, KEYWORD_3, 100) // 다른 키워드
            ));

            // when
            List<Long> result = recommendation.recommend(USER_ID, 10);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("키워드 중복률 높은 Fliner의 컬렉션 우선 추천")
        void prioritizeHighOverlapFliner() {
            // given
            when(userKeywordRepository.findKeywordIdsByUserId(USER_ID)).thenReturn(List.of(KEYWORD_1, KEYWORD_2));
            when(homeCollectionRepository.findAllFlinerIds()).thenReturn(List.of(FLINER_1, FLINER_2));

            // Fliner1: KEYWORD_1만 가짐 (50% 중복)
            // Fliner2: KEYWORD_1, KEYWORD_2 가짐 (100% 중복)
            when(userKeywordRepository.findByUserIdIn(List.of(FLINER_1, FLINER_2))).thenReturn(List.of(
                UserKeyword.create(FLINER_1, KEYWORD_1, 50),
                UserKeyword.create(FLINER_2, KEYWORD_1, 50),
                UserKeyword.create(FLINER_2, KEYWORD_2, 50)
            ));

            // 각 Fliner의 컬렉션
            when(homeCollectionRepository.findPublicCollectionsByFlinerIds(List.of(FLINER_2, FLINER_1)))
                .thenReturn(List.of(
                    createCollectionProjection(COLLECTION_1, FLINER_1),
                    createCollectionProjection(COLLECTION_2, FLINER_2)
                ));

            // 컬렉션 키워드
            when(collectionKeywordRepository.findByCollectionIdIn(List.of(COLLECTION_1, COLLECTION_2)))
                .thenReturn(List.of(
                    createCollectionKeyword(COLLECTION_1, KEYWORD_1),
                    createCollectionKeyword(COLLECTION_2, KEYWORD_1),
                    createCollectionKeyword(COLLECTION_2, KEYWORD_2)
                ));

            // when
            List<Long> result = recommendation.recommend(USER_ID, 10);

            // then
            assertThat(result).isNotEmpty();
            // Fliner2의 컬렉션이 먼저 (중복률 높음)
            assertThat(result.get(0)).isEqualTo(COLLECTION_2);
        }

        @Test
        @DisplayName("최대 개수 제한 적용")
        void respectMaxSize() {
            // given
            when(userKeywordRepository.findKeywordIdsByUserId(USER_ID)).thenReturn(List.of(KEYWORD_1));
            when(homeCollectionRepository.findAllFlinerIds()).thenReturn(List.of(FLINER_1));
            when(userKeywordRepository.findByUserIdIn(List.of(FLINER_1))).thenReturn(List.of(
                UserKeyword.create(FLINER_1, KEYWORD_1, 100)
            ));
            when(homeCollectionRepository.findPublicCollectionsByFlinerIds(List.of(FLINER_1)))
                .thenReturn(List.of(
                    createCollectionProjection(COLLECTION_1, FLINER_1),
                    createCollectionProjection(COLLECTION_2, FLINER_1)
                ));
            when(collectionKeywordRepository.findByCollectionIdIn(List.of(COLLECTION_1, COLLECTION_2)))
                .thenReturn(List.of(
                    createCollectionKeyword(COLLECTION_1, KEYWORD_1),
                    createCollectionKeyword(COLLECTION_2, KEYWORD_1)
                ));

            // when
            List<Long> result = recommendation.recommend(USER_ID, 1);

            // then
            assertThat(result).hasSize(1);
        }
    }

    private CollectionBasicProjection createCollectionProjection(Long id, Long userId) {
        return new CollectionBasicProjection() {
            @Override
            public Long getId() { return id; }
            @Override
            public Long getUserId() { return userId; }
            @Override
            public LocalDateTime getCreatedAt() { return LocalDateTime.now(); }
        };
    }

    private CollectionKeyword createCollectionKeyword(Long collectionId, Long keywordId) {
        return CollectionKeyword.create(collectionId, keywordId);
    }
}
