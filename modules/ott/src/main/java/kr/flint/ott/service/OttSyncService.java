package kr.flint.ott.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import kr.flint.ott.domain.OttContent;
import kr.flint.ott.domain.OttProvider;
import kr.flint.ott.repository.OttContentRepository;
import kr.flint.ott.repository.OttProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OttSyncService {
	private final OttProviderRepository ottProviderRepository;
	private final OttContentRepository ottContentRepository;

	@Transactional
	public void linkProviders(final Long contentId, final List<String> providerNames) {
		if (contentId == null || CollectionUtils.isEmpty(providerNames)) {
			return;
		}
		for (String name : providerNames) {
			OttProvider provider = ottProviderRepository.findByName(name).orElse(null);
			if (provider == null) {
				continue;
			}
			if (ottContentRepository.existsByOttProviderAndContentId(provider, contentId)) {
				continue;
			}
			ottContentRepository.save(OttContent.create(provider, contentId, provider.getUrl()));
		}
	}
}
