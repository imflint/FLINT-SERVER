package kr.flint.taste.repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import io.hypersistence.tsid.TSID;
import kr.flint.taste.domain.UserKeyword;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class UserKeywordRepositoryCustomImpl implements UserKeywordRepositoryCustom {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void replaceAll(Long userId, List<UserKeyword> userKeywords) {
        jdbcTemplate.update("DELETE FROM user_keywords WHERE user_id = ?", userId);

        if (userKeywords.isEmpty()) {
            return;
        }

        String sql = """
            INSERT INTO user_keywords (id, user_id, keyword_id, percentage, ranking, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                percentage = VALUES(percentage),
                ranking = VALUES(ranking),
                updated_at = VALUES(updated_at)
            """;

        LocalDateTime now = LocalDateTime.now();
        Timestamp timestamp = Timestamp.valueOf(now);

        jdbcTemplate.batchUpdate(sql, userKeywords, userKeywords.size(),
            (PreparedStatement ps, UserKeyword uk) -> {
                ps.setLong(1, TSID.Factory.getTsid().toLong());
                ps.setLong(2, userId);
                ps.setLong(3, uk.getKeywordId());
                ps.setInt(4, uk.getPercentage());
                ps.setInt(5, uk.getRanking());
                ps.setTimestamp(6, timestamp);
                ps.setTimestamp(7, timestamp);
            });
    }
}
