package kr.flint.api.domain.home.repository;

import static kr.flint.collection.domain.QCollection.*;
import static kr.flint.user.domain.QUser.*;

import java.util.List;

import org.springframework.util.CollectionUtils;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;

import kr.flint.api.domain.home.dto.projection.CollectionBasicProjection;
import kr.flint.api.domain.home.dto.projection.CollectionBasicProjectionImpl;
import kr.flint.api.domain.home.dto.projection.CollectionCardProjection;
import kr.flint.api.domain.home.dto.projection.CollectionCardProjectionImpl;
import kr.flint.user.domain.UserRole;
import kr.flint.user.domain.UserStatus;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class HomeCollectionRepositoryCustomImpl implements HomeCollectionRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<CollectionCardProjection> findCollectionCardsWithUser(List<Long> collectionIds) {
        if (CollectionUtils.isEmpty(collectionIds)) {
            return List.of();
        }

        List<CollectionCardProjectionImpl> result = queryFactory
            .select(Projections.constructor(CollectionCardProjectionImpl.class,
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
            .fetch();

        return List.copyOf(result);
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
                collection.isPublic.isTrue()
            )
            .fetch();

        return List.copyOf(result);
    }
}
