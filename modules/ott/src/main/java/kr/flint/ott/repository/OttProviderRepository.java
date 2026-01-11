package kr.flint.ott.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.flint.ott.domain.OttProvider;

@Repository
public interface OttProviderRepository extends JpaRepository<OttProvider, Long> {

}
