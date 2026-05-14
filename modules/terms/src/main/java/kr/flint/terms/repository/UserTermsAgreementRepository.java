package kr.flint.terms.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import kr.flint.terms.domain.TermsContext;
import kr.flint.terms.domain.UserTermsAgreement;

public interface UserTermsAgreementRepository extends JpaRepository<UserTermsAgreement, Long> {

	@Query("""
		select a.termsId
		from UserTermsAgreement a
		where a.userId = :userId
			and a.termsId in :termsIds
			and (
				a.context = :context
				or (:includeLegacySignup = true and a.context is null)
			)
	""")
	List<Long> findAgreedTermsIdsByUserIdAndContextAndTermsIdIn(
		@Param("userId") Long userId,
		@Param("context") TermsContext context,
		@Param("termsIds") Collection<Long> termsIds,
		@Param("includeLegacySignup") boolean includeLegacySignup
	);
}
