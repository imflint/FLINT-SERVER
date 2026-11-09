package kr.flint.system.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.flint.system.domain.AppPlatform;
import kr.flint.system.domain.AppVersion;

@Repository
public interface AppVersionRepository extends JpaRepository<AppVersion, Long> {

    Optional<AppVersion> findByPlatform(AppPlatform platform);

    List<AppVersion> findAllByOrderByPlatformAsc();
}
