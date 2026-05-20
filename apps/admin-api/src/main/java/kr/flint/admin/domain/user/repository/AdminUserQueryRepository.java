package kr.flint.admin.domain.user.repository;

import static kr.flint.user.domain.QUser.user;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;

import kr.flint.user.domain.UserRole;
import kr.flint.user.domain.UserStatus;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AdminUserQueryRepository {

    private final JPAQueryFactory queryFactory;

    public List<Long> findUserIds(
        String keyword,
        UserStatus status,
        LocalDateTime createdFrom,
        LocalDateTime createdToExclusive,
        int page,
        int size
    ) {
        return queryFactory
            .select(user.id)
            .from(user)
            .where(
                keywordCondition(keyword),
                statusCondition(status),
                createdAtFromCondition(createdFrom),
                createdAtToCondition(createdToExclusive)
            )
            .orderBy(user.id.desc())
            .offset((long) (page - 1) * size)
            .limit(size)
            .fetch();
    }

    public long countUsers(
        String keyword,
        UserStatus status,
        LocalDateTime createdFrom,
        LocalDateTime createdToExclusive
    ) {
        Long count = queryFactory
            .select(user.id.count())
            .from(user)
            .where(
                keywordCondition(keyword),
                statusCondition(status),
                createdAtFromCondition(createdFrom),
                createdAtToCondition(createdToExclusive)
            )
            .fetchOne();
        return count != null ? count : 0L;
    }

    public List<UserRow> findUserRows(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return queryFactory
            .select(Projections.constructor(
                UserRow.class,
                user.id,
                user.nickname,
                user.profileImage,
                user.userRole,
                user.status,
                user.warningCount,
                user.uploadRestrictedAt,
                user.uploadRestrictedUntil,
                user.suspendedAt,
                user.suspendedUntil,
                user.deletedAt,
                user.createdAt,
                user.updatedAt
            ))
            .from(user)
            .where(user.id.in(userIds))
            .orderBy(user.id.desc())
            .fetch();
    }

    public UserRow findUserRow(Long userId) {
        return queryFactory
            .select(Projections.constructor(
                UserRow.class,
                user.id,
                user.nickname,
                user.profileImage,
                user.userRole,
                user.status,
                user.warningCount,
                user.uploadRestrictedAt,
                user.uploadRestrictedUntil,
                user.suspendedAt,
                user.suspendedUntil,
                user.deletedAt,
                user.createdAt,
                user.updatedAt
            ))
            .from(user)
            .where(user.id.eq(userId))
            .fetchOne();
    }

    private BooleanExpression statusCondition(UserStatus status) {
        return status == null ? null : user.status.eq(status);
    }

    private BooleanExpression createdAtFromCondition(LocalDateTime createdFrom) {
        return createdFrom == null ? null : user.createdAt.goe(createdFrom);
    }

    private BooleanExpression createdAtToCondition(LocalDateTime createdToExclusive) {
        return createdToExclusive == null ? null : user.createdAt.lt(createdToExclusive);
    }

    private BooleanBuilder keywordCondition(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }

        String normalizedKeyword = keyword.trim();
        BooleanBuilder builder = new BooleanBuilder(user.nickname.containsIgnoreCase(normalizedKeyword));
        parseLong(normalizedKeyword).ifPresent(id -> builder.or(user.id.eq(id)));
        return builder;
    }

    private java.util.Optional<Long> parseLong(String keyword) {
        try {
            return java.util.Optional.of(Long.parseLong(keyword));
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }

    public record UserRow(
        Long userId,
        String nickname,
        String profileImage,
        UserRole userRole,
        UserStatus status,
        Integer warningCount,
        LocalDateTime uploadRestrictedAt,
        LocalDateTime uploadRestrictedUntil,
        LocalDateTime suspendedAt,
        LocalDateTime suspendedUntil,
        LocalDateTime deletedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {
    }
}
