package kr.flint.taste.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.flint.taste.domain.Keyword;

@Repository
public interface KeywordRepository extends JpaRepository<Keyword, Long> {
    Optional<Keyword> findByName(String name);

	List<Keyword> findAllByName(String name);

	List<Keyword> findAllByNameIn(Collection<String> names);
}
