package kr.flint.content.repository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import kr.flint.content.domain.GenreCode;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.flint.content.domain.Genre;

@Repository
public interface GenreRepository extends JpaRepository<Genre, Long> {
	boolean existsByName(String name);

	Optional<Genre> findByName(String name);
	Optional<Genre> findByCode(GenreCode code);
	List<Genre> findAllByCodeIn(Collection<GenreCode> codes);
}
