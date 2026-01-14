package kr.flint.content.repository;

import static kr.flint.content.domain.QContentGenre.contentGenre;
import static kr.flint.content.domain.QGenre.genre;

import java.util.List;

import com.querydsl.jpa.impl.JPAQueryFactory;

import kr.flint.content.domain.ContentGenre;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ContentGenreRepositoryCustomImpl implements ContentGenreRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    @Override
    public List<ContentGenre> findAllByContentIdsWithGenre(List<Long> contentIds) {
        return queryFactory
            .selectFrom(contentGenre)
            .join(contentGenre.genre, genre).fetchJoin()
            .where(contentGenre.content.id.in(contentIds))
            .fetch();
    }
}
