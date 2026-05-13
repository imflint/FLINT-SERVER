package kr.flint.admin.domain.batch.controller.spec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.flint.admin.domain.batch.dto.response.BatchJobExecutionRes;
import kr.flint.content.domain.MediaType;

@Tag(name = "Batch Admin", description = "TMDB 배치 관리 API")
public interface AdminBatchControllerDocs {

	@Operation(summary = "TMDB 영화 전체 import 실행", description = "TMDB export 파일 기준으로 영화 import Job을 실행합니다.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponse(responseCode = "200", description = "영화 import Job 실행 요청 성공", useReturnTypeSchema = true)
	BatchJobExecutionRes triggerMovies(
		@Parameter(description = "TMDB export date. 미입력 시 전일 기준으로 실행합니다. 예: 2026-05-09")
		String date
	) throws Exception;

	@Operation(summary = "TMDB TV 전체 import 실행", description = "TMDB export 파일 기준으로 TV import Job을 실행합니다.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponse(responseCode = "200", description = "TV import Job 실행 요청 성공", useReturnTypeSchema = true)
	BatchJobExecutionRes triggerTv(
		@Parameter(description = "TMDB export date. 미입력 시 전일 기준으로 실행합니다. 예: 2026-05-09")
		String date
	) throws Exception;

	@Operation(summary = "TMDB OTT 동기화 실행", description = "저장된 콘텐츠의 OTT provider 정보를 TMDB 기준으로 동기화합니다.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponse(responseCode = "200", description = "OTT sync Job 실행 요청 성공", useReturnTypeSchema = true)
	BatchJobExecutionRes triggerOtt(
		@Parameter(description = "미디어 타입. 기본값은 MOVIE입니다.")
		MediaType mediaType
	) throws Exception;

	@Operation(summary = "TMDB 변경분 동기화 실행", description = "TMDB changes API 기준으로 변경분 import Job을 실행합니다.")
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
