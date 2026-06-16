package kr.flint.api.domain.user.repository;

import static kr.flint.collection.domain.QCollection.*;
import static kr.flint.collection.domain.QCollectionContent.*;
import static kr.flint.collection.domain.QCollectionContentImage.collectionContentImage;
import static kr.flint.content.domain.QContent.*;
import static kr.flint.user.domain.QUser.*;
import static kr.flint.api.common.query.CollectionQueryConditions.*;

import java.util.List;

import org.springframework.util.CollectionUtils;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;

import kr.flint.api.domain.user.dto.response.CollectionContentImageDto;
import kr.flint.api.domain.user.dto.response.CollectionWithUserDto;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UserCollectionRepositoryCustomImpl implements UserCollectionRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<CollectionWithUserDto> findAllCollectionsWithUserByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }

        return queryFactory
            .select(Projections.constructor(CollectionWithUserDto.class,
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
	                isVisibleCollection()
	            )
            .orderBy(collection.id.desc())
            .fetch();
    }

    @Override
    public List<CollectionWithUserDto> findPublicCollectionsWithUserByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }

        return queryFactory
            .select(Projections.constructor(CollectionWithUserDto.class,
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
                    isVisiblePublicCollection()
                )
            .orderBy(collection.id.desc())
            .fetch();
    }

    @Override
    public List<CollectionWithUserDto> findAllCollectionsWithUserByIdIn(List<Long> collectionIds) {
        if (CollectionUtils.isEmpty(collectionIds)) {
            return List.of();
        }

        return queryFactory
            .select(Projections.constructor(CollectionWithUserDto.class,
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
	                isVisibleCollection()
	            )
            .orderBy(collection.id.desc())
            .fetch();
    }

    @Override
    public List<CollectionWithUserDto> findPublicCollectionsWithUserByIdIn(List<Long> collectionIds) {
        if (CollectionUtils.isEmpty(collectionIds)) {
            return List.of();
        }

        return queryFactory
            .select(Projections.constructor(CollectionWithUserDto.class,
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
                    isVisiblePublicCollection()
                )
            .orderBy(collection.id.desc())
            .fetch();
    }

    @Override
    public List<CollectionContentImageDto> findContentImagesByCollectionIds(List<Long> collectionIds) {
        if (CollectionUtils.isEmpty(collectionIds)) {
            return List.of();
        }

        return queryFactory
            .select(Projections.constructor(CollectionContentImageDto.class,
                collectionContent.collection.id,
                collectionContentImage.imageKey,
                content.poster
            ))
            .from(collectionContent)
            .join(content).on(collectionContent.contentId.eq(content.id))
            .leftJoin(collectionContentImage).on(
                collectionContentImage.collectionContent.id.eq(collectionContent.id)
                    .and(collectionContentImage.sortOrder.eq(0))
            )
            .where(collectionContent.collection.id.in(collectionIds))
            .orderBy(
                collectionContent.collection.id.asc(),
                collectionContent.sortOrder.asc()
            )
            .fetch();
    }
}
