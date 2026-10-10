package kr.flint.batch.service;

import java.util.List;

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
        Long missing = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM content
            WHERE NULLIF(TRIM(title_ko), '') IS NULL AND NULLIF(TRIM(title_en), '') IS NULL
            """, Long.class);
        if (missing == null || missing > 0) {
            throw new IllegalStateException("Search backfill blocked: missing localized titles=" + missing);
        }
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
                UPDATE content SET normalized_title_ko=?, normalized_title_en=?, search_title=?
                WHERE id=? AND CAST(title_ko AS BINARY) <=> CAST(? AS BINARY)
                    AND CAST(title_en AS BINARY) <=> CAST(? AS BINARY)
                """, ContentTitleNormalizer.normalizeNullable(source.titleKo()),
                ContentTitleNormalizer.normalizeNullable(source.titleEn()), searchTitle,
                source.id(), source.titleKo(), source.titleEn());
            if (updated != 1) {
                throw new IllegalStateException("Title changed during search backfill: content=" + source.id());
            }
        }
    }

    public record DocumentSource(long id, String titleKo, String titleEn) { }
}
