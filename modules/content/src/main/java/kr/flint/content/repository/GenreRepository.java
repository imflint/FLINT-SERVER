package kr.flint.content.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.flint.content.domain.Genre;

@Repository
public interface GenreRepository extends JpaRepository<Genre, Long> {
	boolean existsByName(String name);

	Optional<Genre> findByName(String name);
}
