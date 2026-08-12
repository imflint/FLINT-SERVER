package kr.flint.api.domain.exploration.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

import static kr.flint.api.common.query.CollectionQueryConditions.isVisiblePublicCollection;
import static kr.flint.collection.domain.QCollection.collection;
import static kr.flint.collection.domain.QCollectionContent.collectionContent;
import static kr.flint.content.domain.QContent.content;

@Repository
@RequiredArgsConstructor
public class ExplorationQueryRepository {

	private final JPAQueryFactory jpaQueryFactory;

	// 탐색 세션용 작품을 id 오름차순으로 조회한다.
	// cursor = 직전 세션에서 마지막으로 소진한 작품 id (exclusive). 신규 작품은 항상 더 큰 id라 뒤에만 붙으므로 기존 세션 윈도우가 흔들리지 않는다.
	// 노출 정책: 공개(VISIBLE)인 공개 컬렉션에 속한 작품만 노출한다. 작품이 여러 컬렉션에 속해도 서브쿼리로 1행만 유지한다.
	public List<ExploreContentRow> findSession(Long cursor, int size) {
		return jpaQueryFactory
			.select(Projections.constructor(
				ExploreContentRow.class,
				content.id,
				content.title,
				content.poster,
				content.year
			))
			.from(content)
			.where(
				cursor != null ? content.id.gt(cursor) : null,
				content.id.in(
					JPAExpressions
						.select(collectionContent.contentId)
						.from(collectionContent)
						.join(collectionContent.collection, collection)
						.where(isVisiblePublicCollection())
				)
			)
			.orderBy(content.id.asc())
			.limit(size)
			.fetch();
	}

	public record ExploreContentRow(
		Long contentId,
		String title,
		String poster,
		int year
	) {}
}
