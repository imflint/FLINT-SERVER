package kr.flint.content.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.flint.content.domain.ContentGenre;

@Repository
public interface ContentGenreRepository extends JpaRepository<ContentGenre, Long>, ContentGenreRepositoryCustom {
}
