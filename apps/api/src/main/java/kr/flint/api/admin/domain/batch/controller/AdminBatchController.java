package kr.flint.api.admin.domain.batch.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.validation.Valid;

import kr.flint.api.admin.domain.batch.controller.spec.AdminBatchControllerDocs;
import kr.flint.api.admin.domain.batch.dto.response.BatchJobExecutionRes;
import kr.flint.api.admin.domain.batch.dto.response.TmdbSyncRunRes;
import kr.flint.api.admin.domain.batch.dto.request.LanguageCleanupExecuteReq;
import kr.flint.api.admin.domain.batch.dto.response.LanguageCleanupManifestRes;
import kr.flint.api.admin.domain.batch.service.TmdbLanguageCleanupService;
import kr.flint.api.admin.domain.batch.service.TmdbBatchCommandFacade;
import kr.flint.content.domain.MediaType;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/batch")
public class AdminBatchController implements AdminBatchControllerDocs {

	private final TmdbBatchCommandFacade tmdbBatchCommandFacade;
	private final TmdbLanguageCleanupService tmdbLanguageCleanupService;

	@Override
	@PostMapping("/daily-sync")
	public BatchJobExecutionRes triggerDailySync(
		@RequestParam(required = false) LocalDate businessDate
	) {
		return tmdbBatchCommandFacade.triggerDailySync(businessDate);
	}

	@Override
	@PostMapping("/monthly-reconcile")
	public BatchJobExecutionRes triggerMonthlyReconcile(
		@RequestParam(required = false) YearMonth businessMonth
	) {
		return tmdbBatchCommandFacade.triggerMonthlyReconcile(businessMonth);
	}

	@Override
	@PostMapping("/language-cleanup/classify")
	public BatchJobExecutionRes triggerLanguageClassification(
		@RequestParam(required = false) YearMonth businessMonth
	) {
		return tmdbBatchCommandFacade.triggerLanguageClassification(businessMonth);
	}

	@Override
	@GetMapping("/runs")
	public List<TmdbSyncRunRes> getRuns(@RequestParam(defaultValue = "20") int limit) {
		return tmdbBatchCommandFacade.getRuns(limit);
	}

	@Override
	@GetMapping("/runs/{runId}")
	public TmdbSyncRunRes getRun(@PathVariable Long runId) {
		return tmdbBatchCommandFacade.getRun(runId);
	}

	@Override
	@PostMapping("/language-cleanup/preview")
	public LanguageCleanupManifestRes previewLanguageCleanup() {
		return tmdbLanguageCleanupService.preview();
	}

	@Override
	@PostMapping("/language-cleanup/execute")
	public LanguageCleanupManifestRes executeLanguageCleanup(
		@Valid @RequestBody LanguageCleanupExecuteReq request
	) {
		return tmdbLanguageCleanupService.execute(request);
	}

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
