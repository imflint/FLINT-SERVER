package kr.flint.taste.repository;

import java.util.List;
import java.util.Optional;

import kr.flint.taste.domain.UserKeyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.flint.taste.dto.response.UserKeywordProjection;

@Repository
public interface UserKeywordRepository extends JpaRepository<UserKeyword, Long> {

    List<UserKeyword> findByUserId(Long userId);

    Optional<UserKeyword> findByUserIdAndKeywordId(Long userId, Long keywordId);

    @Query("""
        SELECT k.name as name, uk.percentage as percentage, uk.ranking as ranking, k.image as imageUrl, k.level as level
        FROM UserKeyword uk
        JOIN Keyword k ON uk.keywordId = k.id
        WHERE uk.userId = :userId
        ORDER BY uk.percentage DESC
    """)
    List<UserKeywordProjection> findUserKeywordsWithDetails(@Param("userId") Long userId);

	boolean existsByUserId(Long userId);

	void deleteAllByUserId(Long userId);
}
