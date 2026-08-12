package kr.flint.exploration.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.flint.exploration.domain.UserExplorationProgress;

@Repository
public interface UserExplorationProgressRepository extends JpaRepository<UserExplorationProgress, Long> {

	Optional<UserExplorationProgress> findByUserId(Long userId);
}
