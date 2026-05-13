package kr.flint.admin.domain.batch.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import kr.flint.admin.domain.batch.controller.spec.AdminBatchControllerDocs;
import kr.flint.admin.domain.batch.dto.response.BatchJobExecutionRes;
import kr.flint.admin.domain.batch.service.TmdbBatchCommandFacade;
import kr.flint.content.domain.MediaType;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/batch")
public class AdminBatchController implements AdminBatchControllerDocs {

	private final TmdbBatchCommandFacade tmdbBatchCommandFacade;

	@Override
	@PostMapping("/movies")
	public BatchJobExecutionRes triggerMovies(@RequestParam(required = false) String date) throws Exception {
		return tmdbBatchCommandFacade.triggerMovies(date);
	}

	@Override
	@PostMapping("/tv")
	public BatchJobExecutionRes triggerTv(@RequestParam(required = false) String date) throws Exception {
		return tmdbBatchCommandFacade.triggerTv(date);
	}

	@Override
	@PostMapping("/ott")
	public BatchJobExecutionRes triggerOtt(@RequestParam(defaultValue = "MOVIE") MediaType mediaType) throws Exception {
		return tmdbBatchCommandFacade.triggerOtt(mediaType);
	}

	@Override
	@PostMapping("/delta")
	public BatchJobExecutionRes triggerDelta(
		@RequestParam(defaultValue = "MOVIE") MediaType mediaType,
		@RequestParam(required = false) String startDate,
		@RequestParam(required = false) String endDate
	) throws Exception {
		return tmdbBatchCommandFacade.triggerDelta(mediaType, startDate, endDate);
	}
}
