package kr.flint.api.storage.controller.spec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.flint.infra.storage.enums.StoragePathType;
import kr.flint.shared.exception.ProblemDetail;
import kr.flint.shared.storage.StorageUploadUrl;

@Tag(name = "Storage", description = "파일 업로드 API")
public interface StorageControllerDocs {

    @Operation(
            summary = "파일 업로드용 Presigned URL 발급",
            description = "클라이언트가 직접 S3에 파일을 업로드할 수 있는 Presigned URL을 발급합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Presigned URL 발급 성공",
                    content = @Content(schema = @Schema(implementation = StorageUploadUrl.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 (허용되지 않는 확장자 또는 경로 타입)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    StorageUploadUrl getUploadUrl(
            @Parameter(
                    description = "저장 경로 타입",
                    example = "USER_PROFILE",
                    schema = @Schema(implementation = StoragePathType.class)
            ) String pathType,
            @Parameter(description = "파일 확장자", example = "jpg") String extension
    );
}
