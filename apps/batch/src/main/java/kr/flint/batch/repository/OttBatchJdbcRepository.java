package kr.flint.batch.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import io.hypersistence.tsid.TSID;
import kr.flint.batch.job.ott.OttSyncDraft;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class OttBatchJdbcRepository {

	private final JdbcTemplate jdbcTemplate;
	private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

	public void linkProviders(List<OttSyncDraft> drafts) {
		if (CollectionUtils.isEmpty(drafts)) {
			return;
		}

		List<ProviderLink> providerLinks = extractProviderLinks(drafts);
		if (providerLinks.isEmpty()) {
			return;
		}

		Set<String> providerNames = new LinkedHashSet<>();
		for (ProviderLink providerLink : providerLinks) {
			providerNames.add(providerLink.providerName());
		}

		Map<String, ProviderRow> providers = findProviders(providerNames);
		List<OttContentRow> rows = resolveRows(providerLinks, providers);
		if (rows.isEmpty()) {
			return;
		}

		String sql = """
			INSERT IGNORE INTO ott_content (id, content_id, ott_provider_id, content_url)
			VALUES (?, ?, ?, ?)
			""";

		jdbcTemplate.batchUpdate(sql, rows, rows.size(), (ps, row) -> {
			ps.setLong(1, TSID.Factory.getTsid().toLong());
			ps.setLong(2, row.contentId());
			ps.setLong(3, row.providerId());
			ps.setString(4, row.contentUrl());
		});
	}

	private List<ProviderLink> extractProviderLinks(List<OttSyncDraft> drafts) {
		List<ProviderLink> providerLinks = new ArrayList<>();
		for (OttSyncDraft draft : drafts) {
			if (draft == null || draft.contentId() == null || CollectionUtils.isEmpty(draft.providerNames())) {
				continue;
			}
			for (String providerName : draft.providerNames()) {
				if (providerName == null || providerName.isBlank()) {
					continue;
				}
				providerLinks.add(new ProviderLink(draft.contentId(), providerName.trim()));
			}
		}
		return providerLinks;
	}

	private Map<String, ProviderRow> findProviders(Set<String> providerNames) {
		String sql = """
			SELECT id, name, url
			FROM ott_provider
			WHERE name IN (:names)
			""";

		MapSqlParameterSource params = new MapSqlParameterSource()
			.addValue("names", new ArrayList<>(providerNames));

		return namedParameterJdbcTemplate.query(sql, params, rs -> {
			Map<String, ProviderRow> result = new HashMap<>();
			while (rs.next()) {
				ProviderRow provider = new ProviderRow(
					rs.getLong("id"),
					rs.getString("name"),
					rs.getString("url")
				);
				ProviderRow previous = result.put(provider.name(), provider);
				if (previous != null) {
					throw new IllegalStateException("Duplicate OTT provider name: " + provider.name());
				}
			}
			return result;
		});
	}

	private List<OttContentRow> resolveRows(List<ProviderLink> providerLinks, Map<String, ProviderRow> providers) {
		List<OttContentRow> rows = new ArrayList<>();
		Set<OttContentKey> seen = new LinkedHashSet<>();

		for (ProviderLink providerLink : providerLinks) {
			ProviderRow provider = providers.get(providerLink.providerName());
			if (provider == null) {
				continue;
			}

			OttContentKey key = new OttContentKey(providerLink.contentId(), provider.id());
			if (seen.add(key)) {
				rows.add(new OttContentRow(providerLink.contentId(), provider.id(), provider.url()));
			}
		}

		return rows;
	}

	private record ProviderLink(Long contentId, String providerName) {
	}

	private record ProviderRow(Long id, String name, String url) {
	}

	private record OttContentKey(Long contentId, Long providerId) {
	}

	private record OttContentRow(Long contentId, Long providerId, String contentUrl) {
	}
}
