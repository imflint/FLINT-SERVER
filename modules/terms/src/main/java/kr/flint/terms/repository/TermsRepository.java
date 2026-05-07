package kr.flint.terms.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import kr.flint.terms.domain.Terms;
import kr.flint.terms.domain.TermsType;

public interface TermsRepository extends JpaRepository<Terms, Long> {

	List<Terms> findByActiveAtLessThanEqual(LocalDateTime activeAt);

	List<Terms> findByTypeAndActiveAtLessThanEqual(TermsType type, LocalDateTime activeAt);
}
