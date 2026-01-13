package kr.flint.ott.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.flint.ott.domain.OttProvider;

@Repository
public interface OttProviderRepository extends JpaRepository<OttProvider, Long> {

	Optional<OttProvider> findByName(String name);
}
