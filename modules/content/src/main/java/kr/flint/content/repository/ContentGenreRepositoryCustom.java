package kr.flint.content.repository;

import java.util.List;

import kr.flint.content.domain.ContentGenre;

public interface ContentGenreRepositoryCustom {
    List<ContentGenre> findAllByContentIdsWithGenre(List<Long> contentIds);
}
