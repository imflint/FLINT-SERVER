package kr.flint.api.domain.user.repository;

import static kr.flint.collection.domain.QCollection.*;
import static kr.flint.user.domain.QUser.*;

import java.util.List;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;

import kr.flint.api.domain.user.dto.response.CollectionWithUserProjection;
import kr.flint.api.domain.user.dto.response.CollectionWithUserProjectionImpl;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UserCollectionRepositoryCustomImpl implements UserCollectionRepositoryCustom {

	private final JPAQueryFactory queryFactory;

	@Override
	public List<CollectionWithUserProjection> findAllCollectionsWithUserByUserId(Long userId) {
		List<CollectionWithUserProjectionImpl> result = queryFactory
			.select(Projections.constructor(CollectionWithUserProjectionImpl.class,
				collection.id,
				collection.title,
				collection.image,
				user.profileImage,
				user.nickname
			))
			.from(collection)
			.join(user).on(collection.userId.eq(user.id))
			.where(collection.userId.eq(userId))
			.orderBy(collection.id.desc())
			.fetch();

		return List.copyOf(result);
	}

	@Override
	public List<CollectionWithUserProjection> findPublicCollectionsWithUserByUserId(Long userId) {
		List<CollectionWithUserProjectionImpl> result = queryFactory
			.select(Projections.constructor(CollectionWithUserProjectionImpl.class,
				collection.id,
				collection.title,
				collection.image,
				user.profileImage,
				user.nickname
			))
			.from(collection)
			.join(user).on(collection.userId.eq(user.id))
			.where(
				collection.userId.eq(userId),
				collection.isPublic.isTrue()
			)
			.orderBy(collection.id.desc())
			.fetch();

		return List.copyOf(result);
	}

	@Override
	public List<CollectionWithUserProjection> findAllCollectionsWithUserByIdIn(List<Long> collectionIds) {
		if (collectionIds == null || collectionIds.isEmpty()) {
			return List.of();
		}

		List<CollectionWithUserProjectionImpl> result = queryFactory
			.select(Projections.constructor(CollectionWithUserProjectionImpl.class,
				collection.id,
				collection.title,
				collection.image,
				user.profileImage,
				user.nickname
			))
			.from(collection)
			.join(user).on(collection.userId.eq(user.id))
			.where(collection.id.in(collectionIds))
			.orderBy(collection.id.desc())
			.fetch();

		return List.copyOf(result);
	}

	@Override
	public List<CollectionWithUserProjection> findPublicCollectionsWithUserByIdIn(List<Long> collectionIds) {
		if (collectionIds == null || collectionIds.isEmpty()) {
			return List.of();
		}

		List<CollectionWithUserProjectionImpl> result = queryFactory
			.select(Projections.constructor(CollectionWithUserProjectionImpl.class,
				collection.id,
				collection.title,
				collection.image,
				user.profileImage,
				user.nickname
			))
			.from(collection)
			.join(user).on(collection.userId.eq(user.id))
			.where(
				collection.id.in(collectionIds),
				collection.isPublic.isTrue()
			)
			.orderBy(collection.id.desc())
			.fetch();

		return List.copyOf(result);
	}
}
