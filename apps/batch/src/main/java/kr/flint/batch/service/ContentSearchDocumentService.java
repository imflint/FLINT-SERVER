package kr.flint.batch.service;

import java.util.List;
import java.util.Objects;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.batch.repository.TmdbContentAdmissionJdbcRepository;
import kr.flint.content.domain.ContentTitleNormalizer;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ContentSearchDocumentService {
    private final JdbcTemplate jdbcTemplate;
    private final TmdbContentAdmissionJdbcRepository admissionRepository;

    public void preflight() {
        if (!admissionRepository.schemaReady()) {
            throw new IllegalStateException("TMDB admission DDL is required for search backfill");
        }
        Integer columns = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='content'
                AND COLUMN_NAME IN ('word_title_ko','word_title_en') AND DATA_TYPE='text'
            """, Integer.class);
        if (columns == null || columns != 2) {
            throw new IllegalStateException("Word title columns are required before search backfill");
        }
        Long missing = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM content
            WHERE NULLIF(TRIM(title_ko), '') IS NULL AND NULLIF(TRIM(title_en), '') IS NULL
            """, Long.class);
        if (missing == null || missing > 0) {
            throw new IllegalStateException("Search backfill blocked: missing localized titles=" + missing);
        }
    }

    public void preflightCharacters() {
        if (!admissionRepository.schemaReady()) {
            throw new IllegalStateException("TMDB admission DDL is required for character backfill");
        }
        Integer objects = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA=DATABASE()
            AND ROUTINE_NAME='flint_refresh_content_search_characters' AND ROUTINE_TYPE='PROCEDURE'
            """, Integer.class);
        if (!Integer.valueOf(1).equals(objects)) {
            throw new IllegalStateException("Character synchronization DDL is required before backfill");
        }
    }

    @Transactional
    public void rewriteCharacters(List<? extends DocumentSource> sources) {
        admissionRepository.lockWrites();
        for (DocumentSource source : sources) {
            var rows = jdbcTemplate.queryForList("""
                SELECT title_ko,title_en,normalized_title_ko,normalized_title_en FROM content WHERE id=? FOR UPDATE
                """, source.id());
            if (rows.size() != 1 || !Objects.equals(rows.getFirst().get("title_ko"), source.titleKo())
                || !Objects.equals(rows.getFirst().get("title_en"), source.titleEn())) {
                throw new IllegalStateException("Title changed during character backfill: content=" + source.id());
            }
            String ko = ContentTitleNormalizer.normalizeNullable(source.titleKo());
            String en = ContentTitleNormalizer.normalizeNullable(source.titleEn());
            if (!Objects.equals(rows.getFirst().get("normalized_title_ko"), ko)
                || !Objects.equals(rows.getFirst().get("normalized_title_en"), en)) {
                throw new IllegalStateException("Stale normalized titles before character backfill: content=" + source.id());
            }
            jdbcTemplate.update("CALL flint_refresh_content_search_characters(?,?,?)", source.id(), ko, en);
            Map<String, Integer> expected = new HashMap<>();
            Stream.of(ko, en).filter(Objects::nonNull).forEach(title -> title.codePoints().forEach(codePoint -> {
                String character = new String(Character.toChars(codePoint));
                expected.put(character, character.equals(ko) || character.equals(en) ? 0 : 1);
            }));
            Map<String, Integer> actual = new HashMap<>();
            jdbcTemplate.query("SELECT character_key,exact_match_rank FROM content_search_character WHERE content_id=?",
                rs -> { actual.put(rs.getString(1), rs.getInt(2)); }, source.id());
            if (!actual.equals(expected)) {
                throw new IllegalStateException("Character index mismatch: content=" + source.id());
            }
        }
    }

    public void verifyCharacterOrphans() {
        Long orphans = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM content_search_character p LEFT JOIN content c ON c.id=p.content_id WHERE c.id IS NULL
            """, Long.class);
        if (!Long.valueOf(0).equals(orphans)) throw new IllegalStateException("Character index has orphan rows");
    }

    @Transactional
    public void rewrite(List<? extends DocumentSource> sources) {
        admissionRepository.lockWrites();
        for (DocumentSource source : sources) {
            String searchTitle = ContentTitleNormalizer.buildSearchTitle(source.titleKo(), source.titleEn());
            if (searchTitle == null || searchTitle.isBlank()) {
                throw new IllegalStateException("Missing localized title: content=" + source.id());
            }
            int updated = jdbcTemplate.update("""
                UPDATE content SET normalized_title_ko=?, normalized_title_en=?, search_title=?, word_title_ko=?, word_title_en=?
                WHERE id=? AND CAST(title_ko AS BINARY) <=> CAST(? AS BINARY)
                    AND CAST(title_en AS BINARY) <=> CAST(? AS BINARY)
                """, ContentTitleNormalizer.normalizeNullable(source.titleKo()),
                ContentTitleNormalizer.normalizeNullable(source.titleEn()), searchTitle,
                ContentTitleNormalizer.normalizeWordsNullable(source.titleKo()), ContentTitleNormalizer.normalizeWordsNullable(source.titleEn()),
                source.id(), source.titleKo(), source.titleEn());
            if (updated != 1) {
                throw new IllegalStateException("Title changed during search backfill: content=" + source.id());
            }
        }
    }

    @Transactional
    public void rewriteWords(List<? extends DocumentSource> sources) {
        admissionRepository.lockWrites();
        for (DocumentSource source : sources) {
            int updated = jdbcTemplate.update("""
                UPDATE content SET word_title_ko=?, word_title_en=?
                WHERE id=? AND CAST(title_ko AS BINARY) <=> CAST(? AS BINARY)
                    AND CAST(title_en AS BINARY) <=> CAST(? AS BINARY)
                """, ContentTitleNormalizer.normalizeWordsNullable(source.titleKo()),
                ContentTitleNormalizer.normalizeWordsNullable(source.titleEn()), source.id(), source.titleKo(), source.titleEn());
            if (updated != 1) {
                throw new IllegalStateException("Title changed during word backfill: content=" + source.id());
            }
        }
    }

    public record DocumentSource(long id, String titleKo, String titleEn) { }
}
