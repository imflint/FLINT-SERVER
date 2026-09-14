package kr.flint.ott.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.flint.ott.domain.OttContent;
import kr.flint.ott.domain.OttProvider;
import kr.flint.ott.dto.GetOttResponse;

@Repository
public interface OttContentRepository extends JpaRepository<OttContent, Long> {
	boolean existsByOttProviderAndContentId(OttProvider ottProvider, Long contentId);

	@Query("""
		select new kr.flint.ott.dto.GetOttResponse(provider.id, provider.name, provider.logoUrl)
		from OttContent ottContent
		join ottContent.ottProvider provider
		where ottContent.contentId = :contentId
		  and provider.active = true
		order by provider.displayPriority asc, provider.id asc
		""")
	List<GetOttResponse> findAllActiveProvidersByContentId(@Param("contentId") Long contentId);
}
