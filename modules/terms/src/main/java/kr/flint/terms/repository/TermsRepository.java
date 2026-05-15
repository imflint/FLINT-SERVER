package kr.flint.terms.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import kr.flint.terms.domain.Terms;
import kr.flint.terms.domain.TermsContext;
import kr.flint.terms.domain.TermsType;

public interface TermsRepository extends JpaRepository<Terms, Long> {

	boolean existsByTypeAndVersion(TermsType type, Integer version);

	@Query("""
		select count(t) > 0
		from Terms t
		where t.type = :type
			and t.version = :version
			and (
				t.context = :context
				or (:includeLegacySignup = true and t.context is null)
			)
	""")
	boolean existsByContextAndTypeAndVersion(
		@Param("context") TermsContext context,
		@Param("type") TermsType type,
		@Param("version") Integer version,
		@Param("includeLegacySignup") boolean includeLegacySignup
	);

	List<Terms> findByActiveAtLessThanEqual(LocalDateTime activeAt);

	List<Terms> findByTypeAndActiveAtLessThanEqual(TermsType type, LocalDateTime activeAt);

	@Query("""
		select t
		from Terms t
		where t.activeAt <= :activeAt
			and (
				t.context = :context
				or (:includeLegacySignup = true and t.context is null)
			)
	""")
	List<Terms> findByContextAndActiveAtLessThanEqual(
		@Param("context") TermsContext context,
		@Param("activeAt") LocalDateTime activeAt,
		@Param("includeLegacySignup") boolean includeLegacySignup
	);

	@Query("""
		select t
		from Terms t
		where t.type = :type
			and t.activeAt <= :activeAt
			and (
				t.context = :context
				or (:includeLegacySignup = true and t.context is null)
			)
	""")
	List<Terms> findByContextAndTypeAndActiveAtLessThanEqual(
		@Param("context") TermsContext context,
		@Param("type") TermsType type,
		@Param("activeAt") LocalDateTime activeAt,
		@Param("includeLegacySignup") boolean includeLegacySignup
	);
}
