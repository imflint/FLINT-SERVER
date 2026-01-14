package kr.flint.taste.repository;

import static kr.flint.taste.domain.QCollectionKeyword.*;
import static kr.flint.shared.util.QueryDslUtil.*;

import java.util.List;

import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CollectionKeywordRepositoryCustomImpl implements CollectionKeywordRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public void deleteByCollectionIdAndKeywordIdIn(Long collectionId, List<Long> keywordIds) {
        queryFactory
            .delete(collectionKeyword)
            .where(
                collectionKeyword.collectionId.eq(collectionId),
                onNotEmpty(keywordIds, ids -> collectionKeyword.keywordId.in(ids))
            )
            .execute();
    }

    @Override
    public void deleteByCollectionId(Long collectionId) {
        queryFactory
            .delete(collectionKeyword)
            .where(collectionKeyword.collectionId.eq(collectionId))
            .execute();
    }
}
