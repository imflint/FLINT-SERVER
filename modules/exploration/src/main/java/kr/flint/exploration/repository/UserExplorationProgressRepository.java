package kr.flint.exploration.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import kr.flint.exploration.domain.UserExplorationProgress;

@Repository
public interface UserExplorationProgressRepository extends JpaRepository<UserExplorationProgress, Long> {

	Optional<UserExplorationProgress> findByUserId(Long userId);

	void deleteAllByUserId(Long userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from UserExplorationProgress p where p.userId = :userId")
	Optional<UserExplorationProgress> findByUserIdForUpdate(@Param("userId") Long userId);

	@Modifying
	@Query(value = """
		INSERT IGNORE INTO user_exploration_progress (
			id, user_id, session_cursor, completed, session_version,
			last_viewed_position, created_at, updated_at
		) VALUES (
			:id, :userId, NULL, FALSE, 1, 0, :now, :now
		)
		""", nativeQuery = true)
	int insertInitialProgress(
		@Param("id") Long id,
		@Param("userId") Long userId,
		@Param("now") java.time.LocalDateTime now
	);
}
