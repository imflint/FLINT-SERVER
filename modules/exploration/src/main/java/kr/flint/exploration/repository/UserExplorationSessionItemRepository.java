package kr.flint.exploration.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.flint.exploration.domain.UserExplorationSessionItem;

@Repository
public interface UserExplorationSessionItemRepository extends JpaRepository<UserExplorationSessionItem, Long> {

	List<UserExplorationSessionItem> findAllByUserIdAndSessionVersionOrderByPosition(
		Long userId,
		int sessionVersion
	);

	void deleteAllByUserId(Long userId);
}
