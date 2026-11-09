package kr.flint.api.domain.system.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.api.domain.system.dto.response.AppVersionRes;
import kr.flint.system.domain.AppPlatform;
import kr.flint.system.service.AppVersionService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SystemQueryFacade {

	private final AppVersionService appVersionService;

	public AppVersionRes getAppVersion(AppPlatform platform) {
		return AppVersionRes.from(appVersionService.getByPlatform(platform));
	}
}
