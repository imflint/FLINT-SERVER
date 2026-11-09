package kr.flint.system.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.system.domain.AppPlatform;
import kr.flint.system.domain.AppVersion;
import kr.flint.system.domain.SemanticVersion;
import kr.flint.system.exception.SystemErrorCode;
import kr.flint.system.exception.SystemException;
import kr.flint.system.repository.AppVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class AppVersionService {

	private final AppVersionRepository appVersionRepository;

	public AppVersion getByPlatform(AppPlatform platform) {
		return appVersionRepository.findByPlatform(platform)
			.orElseThrow(() -> new SystemException(SystemErrorCode.APP_VERSION_NOT_FOUND));
	}

	public List<AppVersion> getAll() {
		return appVersionRepository.findAllByOrderByPlatformAsc();
	}

	/**
	 * 플랫폼별 최소 지원 버전과 스토어 주소를 등록하거나 변경한다.
	 *
	 * <p>앱 배포 없이 강제 업데이트를 걸 수 있는 유일한 경로이므로, 형식 검증을 여기서 끝낸다.
	 */
	@Transactional
	public AppVersion upsert(AppPlatform platform, String minSupportedVersion, String storePath) {
		validate(minSupportedVersion, storePath);

		AppVersion appVersion = appVersionRepository.findByPlatform(platform)
			.map(existing -> {
				log.info("앱 최소 지원 버전 변경. platform={}, {} -> {}",
					platform, existing.getMinSupportedVersion(), minSupportedVersion);
				existing.change(minSupportedVersion, storePath);
				return existing;
			})
			.orElseGet(() -> {
				log.info("앱 최소 지원 버전 등록. platform={}, version={}", platform, minSupportedVersion);
				return appVersionRepository.save(AppVersion.create(platform, minSupportedVersion, storePath));
			});
		return appVersion;
	}

	private void validate(String minSupportedVersion, String storePath) {
		if (!SemanticVersion.isValid(minSupportedVersion)) {
			throw new SystemException(SystemErrorCode.INVALID_APP_VERSION_FORMAT);
		}
		if (storePath == null || storePath.isBlank()) {
			throw new SystemException(SystemErrorCode.INVALID_STORE_PATH);
		}
	}
}
