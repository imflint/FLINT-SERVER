package kr.flint.ott.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.ott.domain.OttProvider;
import kr.flint.ott.domain.OttUser;
import kr.flint.ott.dto.GetOttResponse;
import kr.flint.ott.repository.OttContentRepository;
import kr.flint.ott.repository.OttProviderRepository;
import kr.flint.ott.repository.OttUserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OttService {
	private final OttContentRepository ottContentRepository;
	private final OttProviderRepository ottProviderRepository;
	private final OttUserRepository ottUserRepository;

	public List<GetOttResponse> getOttList(final Long userId, final Long contentId) {
		return ottUserRepository.getSubscribeOttList(userId, contentId);
	}

	@Transactional
	public void createUserOtts(final Long userId, final List<Long> ottProviderIds) {
		if (CollectionUtils.isEmpty(ottProviderIds)) {
			return;
		}

		List<OttProvider> providers = ottProviderRepository.findAllById(ottProviderIds);

		List<OttUser> ottUsers = providers.stream()
			.map(provider -> OttUser.create(provider, userId))
			.toList();

		ottUserRepository.saveAll(ottUsers);
	}
}
