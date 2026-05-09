package kr.flint.ott.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.flint.ott.domain.OttUser;
import kr.flint.ott.dto.GetOttResponse;

@Repository
public interface OttUserRepository extends JpaRepository<OttUser, Long> {
	@Query("""
    select new kr.flint.ott.dto.GetOttResponse(
        op.id,
        op.name,
        op.logoUrl
    )
    from OttUser ou
    join ou.ottProvider op
    join OttContent oc
        on oc.ottProvider = op
    where ou.userId = :userId
      and oc.contentId = :contentId
""")
	List<GetOttResponse> getSubscribeOttList(
		@Param("userId") Long userId,
		@Param("contentId") Long contentId
	);

	void deleteAllByUserId(Long userId);
}
