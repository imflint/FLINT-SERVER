package kr.flint.api.domain.exploration.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import kr.flint.api.domain.exploration.controller.spec.ExplorationControllerDocs;
import kr.flint.api.domain.exploration.dto.response.ExplorationSessionRes;
import kr.flint.api.domain.exploration.service.ExplorationQueryFacade;
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
		@RequestParam(required = false) Long cursor
	) {
		return ResponseEntity.ok(
			SuccessResponse.of(SuccessCode.SUCCESS_FETCH, explorationQueryFacade.getSession(cursor))
		);
	}
}
