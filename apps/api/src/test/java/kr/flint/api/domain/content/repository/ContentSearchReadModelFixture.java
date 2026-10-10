package kr.flint.api.domain.content.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;

final class ContentSearchReadModelFixture {
    private ContentSearchReadModelFixture() {}

    static void install(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS content_search_stopword(value VARCHAR(30)) ENGINE=InnoDB");
            statement.execute("SET SESSION innodb_ft_user_stopword_table=CONCAT(DATABASE(),'/content_search_stopword')");
            statement.execute("""
                CREATE TABLE IF NOT EXISTS content_search_document(
                    FTS_DOC_ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    content_id BIGINT NOT NULL, media_type ENUM('MOVIE','TV') NOT NULL,
                    normalized_title_ko VARCHAR(255), normalized_title_en VARCHAR(255), search_title TEXT,
                    UNIQUE KEY FTS_DOC_ID_INDEX(FTS_DOC_ID), UNIQUE KEY uk_search_document_content(content_id),
                    KEY idx_search_document_title_ko(normalized_title_ko), KEY idx_search_document_title_en(normalized_title_en),
                    FULLTEXT KEY ft_search_document_title(search_title) WITH PARSER ngram
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);
            for (String ddl : List.of(
                """
                CREATE TRIGGER IF NOT EXISTS content_search_document_insert AFTER INSERT ON content FOR EACH ROW
                INSERT INTO content_search_document(content_id,media_type,normalized_title_ko,normalized_title_en,search_title)
                VALUES(NEW.id,NEW.media_type,NEW.normalized_title_ko,NEW.normalized_title_en,NEW.search_title)
                """,
                """
                CREATE TRIGGER IF NOT EXISTS content_search_document_update AFTER UPDATE ON content FOR EACH ROW
                BEGIN
                    IF NOT (OLD.id <=> NEW.id) OR NOT (OLD.media_type <=> NEW.media_type)
                        OR NOT (CAST(OLD.normalized_title_ko AS BINARY) <=> CAST(NEW.normalized_title_ko AS BINARY))
                        OR NOT (CAST(OLD.normalized_title_en AS BINARY) <=> CAST(NEW.normalized_title_en AS BINARY))
                        OR NOT (CAST(OLD.search_title AS BINARY) <=> CAST(NEW.search_title AS BINARY)) THEN
                        DELETE FROM content_search_document WHERE content_id=OLD.id;
                        INSERT INTO content_search_document(content_id,media_type,normalized_title_ko,normalized_title_en,search_title)
                        VALUES(NEW.id,NEW.media_type,NEW.normalized_title_ko,NEW.normalized_title_en,NEW.search_title);
                    END IF;
                END
                """,
                """
                CREATE TRIGGER IF NOT EXISTS content_search_document_delete AFTER DELETE ON content FOR EACH ROW
                DELETE FROM content_search_document WHERE content_id=OLD.id
                """)) {
                statement.execute(ddl);
            }
        }
    }
}
