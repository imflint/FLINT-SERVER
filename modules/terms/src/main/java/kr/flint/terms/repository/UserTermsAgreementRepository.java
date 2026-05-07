package kr.flint.terms.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import kr.flint.terms.domain.UserTermsAgreement;

public interface UserTermsAgreementRepository extends JpaRepository<UserTermsAgreement, Long> {
}
