package kr.flint.api.domain.content.repository;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ContentWordSearchSchemaRepository {
    private final JdbcTemplate jdbcTemplate;

    public void validate() {
        Integer minimum = jdbcTemplate.queryForObject("SELECT @@innodb_ft_min_token_size", Integer.class);
        require(Integer.valueOf(1).equals(minimum), "innodb_ft_min_token_size must be 1");
        for (String table : List.of("content", "content_search_document")) {
            Integer columns = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?
                    AND COLUMN_NAME IN ('word_title_ko','word_title_en') AND DATA_TYPE='text'
                    AND IS_NULLABLE='YES'
                """, Integer.class, table);
            require(Integer.valueOf(2).equals(columns), "Missing word columns: " + table);
        }
        String table = "content_search_document";
        for (String index : List.of("ft_search_document_title", "ft_search_document_words")) {
            List<String> fields = jdbcTemplate.queryForList("""
                SELECT COLUMN_NAME FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND INDEX_NAME=?
                    AND INDEX_TYPE='FULLTEXT' AND IS_VISIBLE='YES'
                ORDER BY SEQ_IN_INDEX
                """, String.class, table, index);
            List<String> expected = index.equals("ft_search_document_words")
                ? List.of("word_title_ko", "word_title_en") : List.of("search_title");
            require(fields.equals(expected), "Incorrect search index: " + index);
            String ddl = (String) jdbcTemplate.queryForMap("SHOW CREATE TABLE `" + table + "`").get("Create Table");
            String definition = ddl.lines().filter(line -> line.contains("`" + index + "`")).findFirst().orElse("");
            boolean ngram = definition.toUpperCase(java.util.Locale.ROOT).contains("WITH PARSER `NGRAM`");
            require(!definition.isEmpty() && (index.equals("ft_search_document_title") ? ngram
                    : !definition.toUpperCase(java.util.Locale.ROOT).contains("WITH PARSER")),
                "Incorrect search parser: " + index);
        }
        Long stopwords = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM content_search_stopword", Long.class);
        require(Long.valueOf(0).equals(stopwords), "Title stopword table must be empty");
        List<Map<String, Object>> triggers = jdbcTemplate.queryForList("""
            SELECT TRIGGER_NAME, ACTION_STATEMENT FROM information_schema.TRIGGERS
            WHERE TRIGGER_SCHEMA=DATABASE() AND EVENT_OBJECT_TABLE='content'
                AND TRIGGER_NAME IN ('content_search_document_insert','content_search_document_update')
            """);
        require(triggers.size() == 2 && triggers.stream().allMatch(row -> {
            String statement = row.get("ACTION_STATEMENT").toString().toLowerCase(java.util.Locale.ROOT);
            return statement.contains("word_title_ko") && statement.contains("word_title_en");
        }), "Word search synchronization triggers are required");
        Integer deleteTriggers = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM information_schema.TRIGGERS
            WHERE TRIGGER_SCHEMA=DATABASE() AND EVENT_OBJECT_TABLE='content'
                AND TRIGGER_NAME='content_search_document_delete' AND EVENT_MANIPULATION='DELETE'
            """, Integer.class);
        require(Integer.valueOf(1).equals(deleteTriggers), "Search document delete trigger is required");
    }

    private void require(boolean valid, String message) {
        if (!valid) throw new IllegalStateException("English word search not ready: " + message);
    }
}
