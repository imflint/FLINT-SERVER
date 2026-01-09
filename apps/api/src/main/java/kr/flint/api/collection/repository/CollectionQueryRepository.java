package kr.flint.api.collection.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;

import kr.flint.collection.dto.response.GetCollectionSimpleRes;
import lombok.RequiredArgsConstructor;

import static kr.flint.collection.domain.QCollection.collection;


@Repository
@RequiredArgsConstructor
public class CollectionQueryRepository {
	private final JPAQueryFactory jpaQueryFactory;

	public List<GetCollectionSimpleRes> getCollectionSimpleList(Long cursor, int size){
		return jpaQueryFactory
			.select(Projections.constructor(
				GetCollectionSimpleRes.class,
				collection.id,
				collection.image,
				collection.title,
				collection.description
			))
			.from(collection)
			.where(
				cursor != null ? collection.id.lt(cursor) : null
			)
			.orderBy(collection.id.desc())
			.limit(size + 1L)
			.fetch();
	}
}
