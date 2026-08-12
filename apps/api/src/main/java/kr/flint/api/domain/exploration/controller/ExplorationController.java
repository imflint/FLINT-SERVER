package kr.flint.api.domain.exploration.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.flint.api.domain.exploration.controller.spec.ExplorationControllerDocs;
import kr.flint.api.domain.exploration.dto.response.ExplorationSessionRes;
import kr.flint.api.domain.exploration.service.ExplorationQueryFacade;
import kr.flint.api.global.security.annotation.CurrentUser;
import kr.flint.shared.dto.response.SuccessCode;
import kr.flint.shared.dto.response.SuccessResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/exploration")
public class ExplorationController implements ExplorationControllerDocs {

	private final ExplorationQueryFacade explorationQueryFacade;

	@Override
	@GetMapping
	public ResponseEntity<SuccessResponse<ExplorationSessionRes>> getExplorationSession(
		@CurrentUser Long userId
	) {
		return ResponseEntity.ok(
			SuccessResponse.of(SuccessCode.SUCCESS_FETCH, explorationQueryFacade.getSession(userId))
		);
	}

	@Override
	@PostMapping("/next")
	public ResponseEntity<SuccessResponse<ExplorationSessionRes>> advanceExplorationSession(
		@CurrentUser Long userId
	) {
		return ResponseEntity.ok(
			SuccessResponse.of(SuccessCode.SUCCESS_FETCH, explorationQueryFacade.advance(userId))
		);
	}
}
