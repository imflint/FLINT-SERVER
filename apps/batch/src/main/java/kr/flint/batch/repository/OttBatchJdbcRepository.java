package kr.flint.batch.repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import io.hypersistence.tsid.TSID;
import kr.flint.batch.job.ott.OttSyncDraft;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class OttBatchJdbcRepository {

    private static final Map<String, List<String>> LEGACY_ALIASES = Map.of(
        "netflix", List.of("Netflix"),
        "tving", List.of("Tving", "TVING"),
        "coupangplay", List.of("CoupangPlay", "Coupang Play"),
        "wavve", List.of("Wavve", "wavve"),
        "disneyplus", List.of("Disney+", "Disney Plus"),
        "watcha", List.of("Watcha")
    );

    private final JdbcTemplate jdbcTemplate;
	private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

	@Transactional
	public void synchronizeProviderMaster(List<OttSyncDraft.Provider> providers) {
		Map<Long, OttSyncDraft.Provider> uniqueProviders = new LinkedHashMap<>();
		for (OttSyncDraft.Provider provider : providers) {
			if (provider != null && provider.tmdbProviderId() != null
				&& provider.name() != null && !provider.name().isBlank()) {
				uniqueProviders.put(provider.tmdbProviderId(), provider);
			}
		}

		attachLegacyAliases(uniqueProviders.values());
		upsertProviders(uniqueProviders.values());
		deactivateMissingProviders(uniqueProviders.keySet());
	}

    public void replaceProviders(List<OttSyncDraft> drafts) {
        if (CollectionUtils.isEmpty(drafts)) {
            return;
        }

        List<OttSyncDraft> successfulDrafts = drafts.stream()
            .filter(draft -> draft != null && draft.contentId() != null)
            .toList();
        if (successfulDrafts.isEmpty()) {
            return;
        }

        Map<Long, OttSyncDraft.Provider> providers = collectProviders(successfulDrafts);
        attachLegacyAliases(providers.values());
        upsertProviders(providers.values());
        Map<Long, Long> providerIds = findProviderIds(providers.keySet());

        replaceContentLinks(successfulDrafts, providerIds);
    }

    public void linkProviders(List<OttSyncDraft> drafts) {
        replaceProviders(drafts);
    }

    private Map<Long, OttSyncDraft.Provider> collectProviders(List<OttSyncDraft> drafts) {
        Map<Long, OttSyncDraft.Provider> providers = new LinkedHashMap<>();
        for (OttSyncDraft draft : drafts) {
            for (OttSyncDraft.Provider provider : draft.providers()) {
                if (provider.tmdbProviderId() != null && provider.name() != null && !provider.name().isBlank()) {
                    providers.put(provider.tmdbProviderId(), provider);
                }
            }
        }
        return providers;
    }

    private void attachLegacyAliases(java.util.Collection<OttSyncDraft.Provider> providers) {
        for (OttSyncDraft.Provider provider : providers) {
            List<String> aliases = LEGACY_ALIASES.get(normalizeName(provider.name()));
            if (aliases == null) {
                continue;
            }
            MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tmdbProviderId", provider.tmdbProviderId())
                .addValue("displayPriority", provider.displayPriority())
                .addValue("aliases", aliases);
            namedParameterJdbcTemplate.update("""
                UPDATE ott_provider
                SET tmdb_provider_id = :tmdbProviderId,
                    display_priority = :displayPriority,
                    active = TRUE
                WHERE tmdb_provider_id IS NULL
                  AND name IN (:aliases)
                """, params);
        }
    }

    private void upsertProviders(java.util.Collection<OttSyncDraft.Provider> providers) {
        if (providers.isEmpty()) {
            return;
        }
        String sql = """
            INSERT INTO ott_provider (
                id, name, logo_url, url, tmdb_provider_id, display_priority, active
            ) VALUES (?, ?, ?, ?, ?, ?, TRUE)
            ON DUPLICATE KEY UPDATE
                name = VALUES(name),
                logo_url = VALUES(logo_url),
                display_priority = VALUES(display_priority),
                active = TRUE
            """;
        List<OttSyncDraft.Provider> rows = new ArrayList<>(providers);
        jdbcTemplate.batchUpdate(sql, rows, rows.size(), (ps, provider) -> {
            ps.setLong(1, TSID.Factory.getTsid().toLong());
            ps.setString(2, provider.name());
            ps.setString(3, provider.logoUrl() == null ? "" : provider.logoUrl());
            ps.setString(4, "");
            ps.setLong(5, provider.tmdbProviderId());
            ps.setInt(6, provider.displayPriority());
        });
    }

	private Map<Long, Long> findProviderIds(Set<Long> tmdbProviderIds) {
        if (tmdbProviderIds.isEmpty()) {
            return Map.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("tmdbProviderIds", new ArrayList<>(tmdbProviderIds));
        return namedParameterJdbcTemplate.query("""
            SELECT id, tmdb_provider_id
            FROM ott_provider
            WHERE tmdb_provider_id IN (:tmdbProviderIds)
            """, params, rs -> {
            Map<Long, Long> result = new LinkedHashMap<>();
            while (rs.next()) {
                result.put(rs.getLong("tmdb_provider_id"), rs.getLong("id"));
            }
            return result;
        });
    }

	private void deactivateMissingProviders(Set<Long> activeTmdbProviderIds) {
		if (activeTmdbProviderIds.isEmpty()) {
			jdbcTemplate.update("UPDATE ott_provider SET active = FALSE WHERE tmdb_provider_id IS NOT NULL");
			return;
		}
		MapSqlParameterSource params = new MapSqlParameterSource()
			.addValue("activeTmdbProviderIds", new ArrayList<>(activeTmdbProviderIds));
		namedParameterJdbcTemplate.update("""
			UPDATE ott_provider
			SET active = FALSE
			WHERE tmdb_provider_id IS NOT NULL
			  AND tmdb_provider_id NOT IN (:activeTmdbProviderIds)
			""", params);
	}

    private void replaceContentLinks(List<OttSyncDraft> drafts, Map<Long, Long> providerIds) {
        Set<Long> contentIds = new LinkedHashSet<>();
        for (OttSyncDraft draft : drafts) {
            contentIds.add(draft.contentId());
        }

        MapSqlParameterSource deleteParams = new MapSqlParameterSource()
            .addValue("contentIds", new ArrayList<>(contentIds));
        namedParameterJdbcTemplate.update(
            "DELETE FROM ott_content WHERE content_id IN (:contentIds)",
            deleteParams
        );

        List<OttContentRow> rows = new ArrayList<>();
        Set<OttContentKey> seen = new LinkedHashSet<>();
        for (OttSyncDraft draft : drafts) {
            for (OttSyncDraft.Provider provider : draft.providers()) {
                Long providerId = providerIds.get(provider.tmdbProviderId());
                if (providerId == null) {
                    throw new IllegalStateException("OTT provider was not found after upsert: " + provider.tmdbProviderId());
                }
                OttContentKey key = new OttContentKey(draft.contentId(), providerId);
                if (seen.add(key)) {
                    rows.add(new OttContentRow(draft.contentId(), providerId, draft.contentUrl()));
                }
            }
        }
        if (rows.isEmpty()) {
            return;
        }

        String sql = """
            INSERT INTO ott_content (id, content_id, ott_provider_id, content_url)
            VALUES (?, ?, ?, ?)
            """;
        jdbcTemplate.batchUpdate(sql, rows, rows.size(), (ps, row) -> {
            ps.setLong(1, TSID.Factory.getTsid().toLong());
            ps.setLong(2, row.contentId());
            ps.setLong(3, row.providerId());
            ps.setString(4, row.contentUrl());
        });
    }

    private String normalizeName(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private record OttContentKey(Long contentId, Long providerId) {
    }

    private record OttContentRow(Long contentId, Long providerId, String contentUrl) {
    }
}
