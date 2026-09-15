package kr.flint.api.admin.domain.batch.controller.spec;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.flint.api.admin.domain.batch.dto.response.BatchJobExecutionRes;
import kr.flint.api.admin.domain.batch.dto.response.TmdbSyncRunRes;
import kr.flint.api.admin.domain.batch.dto.request.LanguageCleanupExecuteReq;
import kr.flint.api.admin.domain.batch.dto.response.LanguageCleanupManifestRes;
import kr.flint.content.domain.MediaType;

@Tag(name = "Batch Admin", description = "TMDB 배치 관리 API")
public interface AdminBatchControllerDocs {

	@Operation(summary = "TMDB 일간 동기화 실행", description = "안정적인 DAILY 업무 키로 영화/TV 변경분과 갱신 대상의 제목·OTT를 순차 동기화합니다.")
	@SecurityRequirement(name = "bearerAuth")
	BatchJobExecutionRes triggerDailySync(LocalDate businessDate);

	@Operation(summary = "TMDB 월간 조정 실행", description = "안정적인 MONTHLY 업무 키로 영화/TV ID export를 registry와 비교하고 필요한 상세 정보만 조정합니다.")
	@SecurityRequirement(name = "bearerAuth")
	BatchJobExecutionRes triggerMonthlyReconcile(YearMonth businessMonth);

	@Operation(summary = "TMDB 전체 언어 분류 실행", description = "콘텐츠와 관계를 변경하지 않고 전체 export 상세 응답의 언어 적격 상태만 registry에 기록합니다.")
	@SecurityRequirement(name = "bearerAuth")
	BatchJobExecutionRes triggerLanguageClassification(YearMonth businessMonth);

	@Operation(summary = "TMDB 동기화 실행 목록 조회")
	@SecurityRequirement(name = "bearerAuth")
	List<TmdbSyncRunRes> getRuns(int limit);

	@Operation(summary = "TMDB 동기화 실행 상세 조회")
	@SecurityRequirement(name = "bearerAuth")
	TmdbSyncRunRes getRun(Long runId);

	@Operation(summary = "언어 부적격 콘텐츠 정리 preview", description = "분류 완료 여부를 확인하고 삭제 후보 ID, 건수, SHA-256 해시를 고정합니다.")
	@SecurityRequirement(name = "bearerAuth")
	LanguageCleanupManifestRes previewLanguageCleanup();

	@Operation(summary = "언어 부적격 콘텐츠 정리 실행", description = "RDS 스냅샷 확인과 preview 해시가 일치할 때 500건 단위로 정리합니다.")
	@SecurityRequirement(name = "bearerAuth")
	LanguageCleanupManifestRes executeLanguageCleanup(LanguageCleanupExecuteReq request);

	@Operation(summary = "TMDB 영화 전체 import 실행", description = "호환 API입니다. 지정일이 속한 월의 통합 월간 조정을 요청합니다.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponse(responseCode = "200", description = "영화 import Job 실행 요청 성공", useReturnTypeSchema = true)
	BatchJobExecutionRes triggerMovies(
		@Parameter(description = "TMDB export date. 미입력 시 전일 기준으로 실행합니다. 예: 2026-05-09")
		String date
	) throws Exception;

	@Operation(summary = "TMDB TV 전체 import 실행", description = "호환 API입니다. 지정일이 속한 월의 통합 월간 조정을 요청합니다.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponse(responseCode = "200", description = "TV import Job 실행 요청 성공", useReturnTypeSchema = true)
	BatchJobExecutionRes triggerTv(
		@Parameter(description = "TMDB export date. 미입력 시 전일 기준으로 실행합니다. 예: 2026-05-09")
		String date
	) throws Exception;

	@Operation(summary = "TMDB OTT 동기화 실행", description = "호환 API입니다. 당일 통합 일간 동기화를 요청하며 제목과 OTT를 함께 갱신합니다.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponse(responseCode = "200", description = "OTT sync Job 실행 요청 성공", useReturnTypeSchema = true)
	BatchJobExecutionRes triggerOtt(
		@Parameter(description = "미디어 타입. 기본값은 MOVIE입니다.")
		MediaType mediaType
	) throws Exception;

	@Operation(summary = "TMDB 변경분 동기화 실행", description = "호환 API입니다. 종료일을 업무일로 하는 통합 일간 동기화를 요청합니다.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponse(responseCode = "200", description = "변경분 sync Job 실행 요청 성공", useReturnTypeSchema = true)
	BatchJobExecutionRes triggerDelta(
		@Parameter(description = "미디어 타입. 기본값은 MOVIE입니다.")
		MediaType mediaType,
		@Parameter(description = "변경분 조회 시작일. 예: 2026-05-01")
		String startDate,
		@Parameter(description = "변경분 조회 종료일. 예: 2026-05-09")
		String endDate
	) throws Exception;
}
