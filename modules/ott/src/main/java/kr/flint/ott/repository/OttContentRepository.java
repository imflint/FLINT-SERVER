package kr.flint.ott.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.flint.ott.domain.OttContent;
import kr.flint.ott.domain.OttProvider;

@Repository
public interface OttContentRepository extends JpaRepository<OttContent, Long> {
	boolean existsByOttProviderAndContentId(OttProvider ottProvider, Long contentId);
}
