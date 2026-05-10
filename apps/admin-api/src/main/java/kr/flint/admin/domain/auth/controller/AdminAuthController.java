package kr.flint.admin.domain.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import kr.flint.admin.domain.auth.controller.spec.AdminAuthControllerDocs;
import kr.flint.admin.domain.auth.dto.request.AdminLoginReq;
import kr.flint.admin.domain.auth.dto.response.AdminLoginRes;
import kr.flint.admin.domain.auth.service.AdminAuthFacade;
import kr.flint.shared.dto.response.SuccessCode;
import kr.flint.shared.dto.response.SuccessResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController implements AdminAuthControllerDocs {

	private final AdminAuthFacade adminAuthFacade;

	@Override
	@PostMapping("/login")
	public ResponseEntity<SuccessResponse<AdminLoginRes>> login(
		@Valid @RequestBody AdminLoginReq request
	) {
		return ResponseEntity
			.ok(SuccessResponse.of(SuccessCode.SUCCESS_LOGIN, adminAuthFacade.login(request)));
	}
}
