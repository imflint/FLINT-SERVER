package kr.flint.api.domain.exploration.repository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
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
	// cursor = 현재 세션의 시작 경계 (exclusive). 신규 작품은 항상 더 큰 id라 뒤에만 붙으므로 기존 세션 윈도우가 흔들리지 않는다.
	public List<ExploreContentRow> findSession(Long cursor, int size) {
		return jpaQueryFactory
			.select(Projections.constructor(
				ExploreContentRow.class,
				content.id,
				content.title,
				content.description,
				content.poster,
				content.year
			))
			.from(content)
			.where(
				cursor != null ? content.id.gt(cursor) : null,
				isExposable()
			)
			.orderBy(content.id.asc())
			.limit(size)
			.fetch();
	}

	// cursor 뒤에 '완전한 다음 세션(size개)'이 존재하는지 확인한다. (size번째 작품의 존재 여부만 확인)
	public boolean existsFullNextSession(Long cursor, int size) {
		Integer found = jpaQueryFactory
			.selectOne()
			.from(content)
			.where(
				content.id.gt(cursor),
				isExposable()
			)
			.orderBy(content.id.asc())
			.offset(size - 1L)
			.limit(1)
			.fetchFirst();
		return found != null;
	}

	// 작품별 대표 컬렉션 id를 조회한다. (작품이 여러 공개 컬렉션에 속하면 가장 최근 = collection.id 최댓값을 대표로 사용)
	// 반환: contentId -> collectionId
	public Map<Long, Long> findRepresentativeCollectionIds(List<Long> contentIds) {
		if (contentIds.isEmpty()) {
			return Map.of();
		}

		List<Tuple> rows = jpaQueryFactory
			.select(collectionContent.contentId, collection.id.max())
			.from(collectionContent)
			.join(collectionContent.collection, collection)
			.where(
				collectionContent.contentId.in(contentIds),
				isVisiblePublicCollection()
			)
			.groupBy(collectionContent.contentId)
			.fetch();

		return rows.stream()
			.collect(Collectors.toMap(
				tuple -> tuple.get(collectionContent.contentId),
				tuple -> tuple.get(collection.id.max())
			));
	}

	// 노출 정책: 공개(VISIBLE)인 공개 컬렉션에 속한 작품만 노출한다. 작품이 여러 컬렉션에 속해도 서브쿼리로 1행만 유지한다.
	private BooleanExpression isExposable() {
		return content.id.in(
			JPAExpressions
				.select(collectionContent.contentId)
				.from(collectionContent)
				.join(collectionContent.collection, collection)
				.where(isVisiblePublicCollection())
		);
	}

	public record ExploreContentRow(
		Long contentId,
		String title,
		String description,
		String poster,
		int year
	) {}
}
