package kr.flint.admin.domain.content.repository;

import static kr.flint.content.domain.QContent.content;
import static kr.flint.content.domain.QContentGenre.contentGenre;
import static kr.flint.content.domain.QGenre.genre;

import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;

import kr.flint.content.domain.MediaType;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AdminContentQueryRepository {

    private final JPAQueryFactory queryFactory;

    public List<Long> findContentIds(String keyword, MediaType mediaType, Long cursor, int size) {
        return queryFactory
            .select(content.id)
            .from(content)
            .where(
                keywordCondition(keyword),
                mediaTypeCondition(mediaType),
                cursor != null ? content.id.lt(cursor) : null
            )
            .orderBy(content.id.desc())
            .limit(size + 1L)
            .fetch();
    }

    public List<ContentRow> findContentRows(List<Long> contentIds) {
        if (contentIds == null || contentIds.isEmpty()) {
            return List.of();
        }
        return queryFactory
            .select(Projections.constructor(
                ContentRow.class,
                content.id,
                content.tmdbId,
                content.mediaType,
                content.title,
                content.year,
                content.author,
                content.description,
                content.poster,
                content.bookmarkCount
            ))
            .from(content)
            .where(content.id.in(contentIds))
            .fetch();
    }

    public List<ContentGenreRow> findGenreRows(List<Long> contentIds) {
        if (contentIds == null || contentIds.isEmpty()) {
            return List.of();
        }
        return queryFactory
            .select(Projections.constructor(
                ContentGenreRow.class,
                contentGenre.content.id,
                genre.name
            ))
            .from(contentGenre)
            .join(contentGenre.genre, genre)
            .where(contentGenre.content.id.in(contentIds))
            .orderBy(genre.name.asc())
            .fetch();
    }

    private BooleanExpression keywordCondition(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return content.title.containsIgnoreCase(keyword.trim());
    }

    private BooleanExpression mediaTypeCondition(MediaType mediaType) {
        return mediaType == null ? null : content.mediaType.eq(mediaType);
    }

    public record ContentRow(
        Long id,
        Long tmdbId,
        MediaType mediaType,
        String title,
        int year,
        String author,
        String description,
        String poster,
        int bookmarkCount
    ) {
    }

    public record ContentGenreRow(
        Long contentId,
        String genreName
    ) {
    }
}
