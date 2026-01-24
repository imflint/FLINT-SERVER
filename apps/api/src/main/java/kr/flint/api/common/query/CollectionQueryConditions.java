package kr.flint.api.common.query;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static kr.flint.collection.domain.QCollection.collection;
import static kr.flint.collection.domain.QCollectionContent.collectionContent;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CollectionQueryConditions {

	public static final int DESCRIPTION_MIN_LENGTH = 10;
	public static final int REASON_MIN_LENGTH = 10;

	// 컬렉션 소개(description)가 10자 이상인지 확인
	public static BooleanExpression hasValidDescription() {
		return collection.description.isNotNull()
			.and(collection.description.length().goe(DESCRIPTION_MIN_LENGTH));
	}

	// 최소 하나의 작품에 추천 이유(reason)가 10자 이상인지 확인
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
