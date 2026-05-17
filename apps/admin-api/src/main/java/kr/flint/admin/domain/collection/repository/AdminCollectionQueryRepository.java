package kr.flint.admin.domain.collection.repository;

import static kr.flint.collection.domain.QCollection.collection;
import static kr.flint.collection.domain.QCollectionContent.collectionContent;
import static kr.flint.content.domain.QContent.content;
import static kr.flint.user.domain.QUser.user;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;

import kr.flint.admin.domain.collection.dto.request.AdminCollectionVisibility;
import kr.flint.collection.domain.CollectionModerationStatus;
import kr.flint.content.domain.MediaType;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AdminCollectionQueryRepository {

    private final JPAQueryFactory queryFactory;

    public List<Long> findCollectionIds(
        String keyword,
        AdminCollectionVisibility visibility,
        CollectionModerationStatus moderationStatus,
        int page,
        int size
    ) {
        return queryFactory
            .select(collection.id)
            .from(collection)
            .where(
                keywordCondition(keyword),
                visibilityCondition(visibility),
                moderationStatusCondition(moderationStatus)
            )
            .orderBy(collection.id.desc())
            .offset((long) (page - 1) * size)
            .limit(size)
            .fetch();
    }

    public long countCollections(
        String keyword,
        AdminCollectionVisibility visibility,
        CollectionModerationStatus moderationStatus
    ) {
        Long count = queryFactory
            .select(collection.id.count())
            .from(collection)
            .where(
                keywordCondition(keyword),
                visibilityCondition(visibility),
                moderationStatusCondition(moderationStatus)
            )
            .fetchOne();
        return count != null ? count : 0L;
    }

    public List<CollectionSummaryRow> findCollectionSummaryRows(List<Long> collectionIds) {
        if (collectionIds == null || collectionIds.isEmpty()) {
            return List.of();
        }
        return queryFactory
            .select(Projections.constructor(
                CollectionSummaryRow.class,
                collection.id,
                collection.title,
                collection.description,
                collection.image,
                collection.isPublic,
                collection.moderationStatus,
                collection.bookmarkCount,
                user.id,
                user.nickname,
                collectionContent.id.count(),
                collection.createdAt
            ))
            .from(collection)
            .leftJoin(user).on(user.id.eq(collection.userId))
            .leftJoin(collectionContent).on(collectionContent.collection.id.eq(collection.id))
            .where(collection.id.in(collectionIds))
            .groupBy(
                collection.id,
                collection.title,
                collection.description,
                collection.image,
                collection.isPublic,
                collection.moderationStatus,
                collection.bookmarkCount,
                user.id,
                user.nickname,
                collection.createdAt
            )
            .fetch();
    }

    public CollectionDetailRow findCollectionDetailRow(Long collectionId) {
        return queryFactory
            .select(Projections.constructor(
                CollectionDetailRow.class,
                collection.id,
                collection.title,
                collection.description,
                collection.image,
                collection.isPublic,
                collection.moderationStatus,
                collection.bookmarkCount,
                collection.createdAt,
                user.id,
                user.nickname,
                user.profileImage
            ))
            .from(collection)
            .leftJoin(user).on(user.id.eq(collection.userId))
            .where(collection.id.eq(collectionId))
            .fetchOne();
    }

    public List<CollectionContentRow> findCollectionContentRows(Long collectionId) {
        return queryFactory
            .select(Projections.constructor(
                CollectionContentRow.class,
                content.id,
                content.title,
                content.poster,
                collectionContent.customImage,
                collectionContent.isSpoiler,
                collectionContent.reason,
                content.year,
                content.mediaType
            ))
            .from(collectionContent)
            .join(content).on(content.id.eq(collectionContent.contentId))
            .where(collectionContent.collection.id.eq(collectionId))
            .orderBy(collectionContent.id.asc())
            .fetch();
    }

    private BooleanExpression keywordCondition(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return collection.title.containsIgnoreCase(keyword.trim());
    }

    private BooleanExpression visibilityCondition(AdminCollectionVisibility visibility) {
        if (visibility == null) {
            return null;
        }
        return visibility == AdminCollectionVisibility.PUBLIC ? collection.isPublic.isTrue() : collection.isPublic.isFalse();
    }

    private BooleanExpression moderationStatusCondition(CollectionModerationStatus moderationStatus) {
        return moderationStatus == null ? null : collection.moderationStatus.eq(moderationStatus);
    }

    public record CollectionSummaryRow(
        Long collectionId,
        String title,
        String description,
        String image,
        boolean isPublic,
        CollectionModerationStatus moderationStatus,
        int bookmarkCount,
        Long ownerId,
        String ownerNickname,
        Long contentCount,
        LocalDateTime createdAt
    ) {
    }

    public record CollectionDetailRow(
        Long collectionId,
        String title,
        String description,
        String image,
        boolean isPublic,
        CollectionModerationStatus moderationStatus,
        int bookmarkCount,
        LocalDateTime createdAt,
        Long ownerId,
        String ownerNickname,
        String ownerProfileImage
    ) {
    }

    public record CollectionContentRow(
        Long contentId,
        String title,
        String poster,
        String customImage,
        boolean isSpoiler,
        String reason,
        int year,
        MediaType mediaType
    ) {
    }
}
