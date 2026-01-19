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
public interface UserKeywordRepository extends JpaRepository<UserKeyword, Long>, UserKeywordRepositoryCustom {

    List<UserKeyword> findByUserId(Long userId);

    Optional<UserKeyword> findByUserIdAndKeywordId(Long userId, Long keywordId);

	@Query("""
    SELECT
        uk.ranking as ranking,
        k.image as imageUrl,
        k.level as level,
        k.name as name,
        uk.percentage as percentage
    FROM UserKeyword uk
    JOIN Keyword k ON uk.keywordId = k.id
    WHERE uk.userId = :userId
    ORDER BY uk.percentage DESC
""")
    List<UserKeywordProjection> findUserKeywordsWithDetails(@Param("userId") Long userId);

    // Fliner들의 키워드 조회 (userId 목록으로)
    @Query("SELECT uk FROM UserKeyword uk WHERE uk.userId IN :userIds")
    List<UserKeyword> findByUserIdIn(@Param("userIds") List<Long> userIds);

    // 사용자의 키워드 ID 목록만 조회
    @Query("SELECT uk.keywordId FROM UserKeyword uk WHERE uk.userId = :userId")
    List<Long> findKeywordIdsByUserId(@Param("userId") Long userId);

	boolean existsByUserId(Long userId);

	void deleteAllByUserId(Long userId);
}
