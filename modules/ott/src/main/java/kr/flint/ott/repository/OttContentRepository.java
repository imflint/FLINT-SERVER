package kr.flint.ott.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.flint.ott.domain.OttContent;

@Repository
public interface OttContentRepository extends JpaRepository<OttContent, Long> {
}
