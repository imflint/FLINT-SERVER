package kr.flint.api.domain.home.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.collection.repository.CollectionContentRepository;
import kr.flint.taste.domain.CollectionKeyword;
import kr.flint.taste.repository.CollectionKeywordRepository;
import kr.flint.taste.repository.ContentKeywordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * CollectionKeyword 동기화 서비스
 * 컬렉션 내 콘텐츠 변경 시 CollectionKeyword 테이블을 동기화
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectionKeywordSyncService {

    private final CollectionKeywordRepository collectionKeywordRepository;
    private final ContentKeywordRepository contentKeywordRepository;
    private final CollectionContentRepository collectionContentRepository;

    // 컬렉션에 콘텐츠 추가 시 키워드 동기화
    @Transactional
    public void syncOnContentAdded(Long collectionId, Long contentId) {
        List<Long> contentKeywordIds = contentKeywordRepository.findKeywordIdsByContentId(contentId);
        if (contentKeywordIds.isEmpty()) {
            log.debug("콘텐츠 키워드가 없습니다. contentId={}", contentId);
            return;
        }

        Set<Long> existingKeywordIds = new HashSet<>(
            collectionKeywordRepository.findKeywordIdsByCollectionId(collectionId)
        );

        List<CollectionKeyword> newKeywords = contentKeywordIds.stream()
            .filter(keywordId -> !existingKeywordIds.contains(keywordId))
            .map(keywordId -> CollectionKeyword.create(collectionId, keywordId))
            .toList();

        if (!newKeywords.isEmpty()) {
            collectionKeywordRepository.saveAll(newKeywords);
            log.debug("컬렉션 키워드 추가. id={}, count={}", collectionId, newKeywords.size());
        }
    }

    // 컬렉션에서 콘텐츠 삭제 시 키워드 동기화
    @Transactional
    public void syncOnContentRemoved(Long collectionId, Long contentId) {
        // 삭제된 콘텐츠의 키워드
        Set<Long> removedContentKeywords = new HashSet<>(
            contentKeywordRepository.findKeywordIdsByContentId(contentId)
        );

        if (removedContentKeywords.isEmpty()) {
            return;
        }

        // 남은 콘텐츠들의 키워드
        List<Long> remainingContentIds = collectionContentRepository.findContentIdsByCollectionId(collectionId);
        Set<Long> remainingKeywords = remainingContentIds.isEmpty() ? Set.of() :
            new HashSet<>(contentKeywordRepository.findKeywordIdsByContentIdIn(remainingContentIds));

        // 더 이상 사용하지 않는 키워드만 삭제
        List<Long> keywordsToRemove = removedContentKeywords.stream()
            .filter(k -> !remainingKeywords.contains(k))
            .toList();

        if (!keywordsToRemove.isEmpty()) {
            collectionKeywordRepository.deleteByCollectionIdAndKeywordIdIn(collectionId, keywordsToRemove);
            log.debug("컬렉션 키워드 삭제. id={}, count={}", collectionId, keywordsToRemove.size());
        }
    }

    // 컬렉션 전체 키워드 재동기화 (배치용)
    @Transactional
    public void fullSync(Long collectionId) {
        collectionKeywordRepository.deleteByCollectionId(collectionId);

        List<Long> contentIds = collectionContentRepository.findContentIdsByCollectionId(collectionId);
        if (contentIds.isEmpty()) {
            log.debug("컬렉션에 콘텐츠가 없습니다. id={}", collectionId);
            return;
        }

        Set<Long> keywordIds = new HashSet<>(contentKeywordRepository.findKeywordIdsByContentIdIn(contentIds));
        List<CollectionKeyword> keywords = keywordIds.stream()
            .map(keywordId -> CollectionKeyword.create(collectionId, keywordId))
            .toList();

        if (!keywords.isEmpty()) {
            collectionKeywordRepository.saveAll(keywords);
            log.debug("컬렉션 키워드 전체 동기화. id={}, count={}", collectionId, keywords.size());
        }
    }
}
