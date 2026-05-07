package kr.flint.api.domain.home.repository;

import static kr.flint.api.common.query.CollectionQueryConditions.*;
import static kr.flint.collection.domain.QCollection.*;
import static kr.flint.collection.domain.QCollectionContent.*;
import static kr.flint.content.domain.QContent.*;
import static kr.flint.user.domain.QUser.*;

import java.util.List;

import org.springframework.util.CollectionUtils;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;

import kr.flint.api.domain.home.dto.projection.CollectionBasicProjection;
import kr.flint.api.domain.home.dto.projection.CollectionBasicProjectionImpl;
import kr.flint.api.domain.home.dto.projection.CollectionCardDto;
import kr.flint.api.domain.home.dto.projection.CollectionContentImageDto;
import kr.flint.user.domain.UserRole;
import kr.flint.user.domain.UserStatus;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class HomeCollectionRepositoryCustomImpl implements HomeCollectionRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<CollectionCardDto> findCollectionCardsWithUser(List<Long> collectionIds) {
        if (CollectionUtils.isEmpty(collectionIds)) {
            return List.of();
        }

        return queryFactory
            .select(Projections.constructor(CollectionCardDto.class,
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
                collectionContent.customImage,
                content.poster
            ))
            .from(collectionContent)
            .join(content).on(collectionContent.contentId.eq(content.id))
            .where(collectionContent.collection.id.in(collectionIds))
            .orderBy(collectionContent.id.asc())
            .fetch();
    }

    @Override
    public List<Long> findAllFlinerIds() {
        return queryFactory
            .select(user.id)
            .from(user)
            .where(
                user.userRole.eq(UserRole.FLINER),
                user.status.eq(UserStatus.ACTIVE)
            )
            .fetch();
    }

    @Override
    public List<CollectionBasicProjection> findPublicCollectionsByFlinerIds(List<Long> flinerIds) {
        if (CollectionUtils.isEmpty(flinerIds)) {
            return List.of();
        }

        List<CollectionBasicProjectionImpl> result = queryFactory
            .select(Projections.constructor(CollectionBasicProjectionImpl.class,
                collection.id,
                collection.userId,
                collection.createdAt
            ))
            .from(collection)
            .where(
                collection.userId.in(flinerIds),
                collection.isPublic.isTrue(),
                hasValidDescription(),
                hasContentWithValidReason()
            )
            .fetch();

        return List.copyOf(result);
    }

    @Override
    public List<Long> findPopularPublicCollectionIds(int limit) {
        return queryFactory
            .select(collection.id)
            .from(collection)
            .where(
                collection.isPublic.isTrue(),
                hasValidDescription(),
                hasContentWithValidReason()
            )
            .orderBy(collection.bookmarkCount.desc(), collection.createdAt.desc())
            .limit(limit)
            .fetch();
    }
}
