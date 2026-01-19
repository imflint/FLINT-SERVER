package kr.flint.api.domain.user.repository;

import static kr.flint.collection.domain.QCollection.*;
import static kr.flint.collection.domain.QCollectionContent.*;
import static kr.flint.content.domain.QContent.*;
import static kr.flint.user.domain.QUser.*;

import java.util.List;

import org.springframework.util.CollectionUtils;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;

import kr.flint.api.domain.user.dto.response.CollectionContentImageProjection;
import kr.flint.api.domain.user.dto.response.CollectionContentImageProjectionImpl;
import kr.flint.api.domain.user.dto.response.CollectionWithUserProjection;
import kr.flint.api.domain.user.dto.response.CollectionWithUserProjectionImpl;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UserCollectionRepositoryCustomImpl implements UserCollectionRepositoryCustom {

	private final JPAQueryFactory queryFactory;

	@Override
	public List<CollectionWithUserProjection> findAllCollectionsWithUserByUserId(Long userId) {
		if (userId == null) {
			return List.of();
		}

		List<CollectionWithUserProjectionImpl> result = queryFactory
			.select(Projections.constructor(CollectionWithUserProjectionImpl.class,
				collection.id,
				collection.title,
				collection.description,
				collection.image,
				collection.bookmarkCount,
				collection.userId,
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
		if (userId == null) {
			return List.of();
		}

		List<CollectionWithUserProjectionImpl> result = queryFactory
			.select(Projections.constructor(CollectionWithUserProjectionImpl.class,
				collection.id,
				collection.title,
				collection.description,
				collection.image,
				collection.bookmarkCount,
				collection.userId,
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
		if (CollectionUtils.isEmpty(collectionIds)) {
			return List.of();
		}

		List<CollectionWithUserProjectionImpl> result = queryFactory
			.select(Projections.constructor(CollectionWithUserProjectionImpl.class,
				collection.id,
				collection.title,
				collection.description,
				collection.image,
				collection.bookmarkCount,
				collection.userId,
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
		if (CollectionUtils.isEmpty(collectionIds)) {
			return List.of();
		}

		List<CollectionWithUserProjectionImpl> result = queryFactory
			.select(Projections.constructor(CollectionWithUserProjectionImpl.class,
				collection.id,
				collection.title,
				collection.description,
				collection.image,
				collection.bookmarkCount,
				collection.userId,
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

	@Override
	public List<CollectionContentImageProjection> findContentImagesByCollectionIds(List<Long> collectionIds) {
		if (CollectionUtils.isEmpty(collectionIds)) {
			return List.of();
		}

		List<CollectionContentImageProjectionImpl> result = queryFactory
			.select(Projections.constructor(CollectionContentImageProjectionImpl.class,
				collectionContent.collection.id,
				content.poster
			))
			.from(collectionContent)
			.join(content).on(collectionContent.contentId.eq(content.id))
			.where(collectionContent.collection.id.in(collectionIds))
			.orderBy(collectionContent.id.asc())
			.fetch();

		return List.copyOf(result);
	}
}
