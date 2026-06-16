package kr.flint.api.common.query;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;

import kr.flint.collection.domain.CollectionModerationStatus;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static kr.flint.collection.domain.QCollection.collection;
import static kr.flint.collection.domain.QCollectionContent.collectionContent;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CollectionQueryConditions {

    public static final int DESCRIPTION_MIN_LENGTH = 5;
    public static final int REASON_MIN_LENGTH = 10;

    // 컬렉션 소개가 5자 이상
    public static BooleanExpression hasValidDescription() {
        return collection.description.isNotNull()
            .and(collection.description.length().goe(DESCRIPTION_MIN_LENGTH));
    }

    public static BooleanExpression isVisiblePublicCollection() {
        return collection.isPublic.isTrue()
            .and(isVisibleCollection());
    }

    public static BooleanExpression isVisibleCollection() {
        return collection.deletedAt.isNull()
            .and(collection.moderationStatus.isNull()
                .or(collection.moderationStatus.eq(CollectionModerationStatus.VISIBLE)));
    }

    // 작품의 추천 이유가 10자 이상
    public static BooleanExpression hasContentWithValidReason() {
        return JPAExpressions
            .selectOne()
            .from(collectionContent)
            .where(
                collectionContent.collection.id.eq(collection.id),
                collectionContent.reason.isNotNull(),
                collectionContent.reason.length().goe(REASON_MIN_LENGTH)
            )
            .exists();
    }
}
