package kr.flint.api.admin.domain.system.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.api.admin.domain.system.dto.request.AppVersionUpsertReq;
import kr.flint.api.admin.domain.system.dto.response.AdminAppVersionRes;
import kr.flint.system.domain.AppPlatform;
import kr.flint.system.service.AppVersionService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAppVersionFacade {

	private final AppVersionService appVersionService;

	public List<AdminAppVersionRes> getAppVersions() {
		return appVersionService.getAll().stream()
			.map(AdminAppVersionRes::from)
			.toList();
	}

	@Transactional
	public AdminAppVersionRes upsertAppVersion(AppPlatform platform, AppVersionUpsertReq request) {
		return AdminAppVersionRes.from(
			appVersionService.upsert(platform, request.version(), request.path())
		);
	}
}
