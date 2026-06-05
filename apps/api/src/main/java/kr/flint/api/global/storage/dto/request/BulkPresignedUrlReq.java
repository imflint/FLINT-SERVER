package kr.flint.api.global.storage.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.flint.api.global.storage.service.StorageUploadTarget;
import kr.flint.infra.storage.enums.StoragePathType;
import kr.flint.shared.storage.FileExtension;

@Schema(description = "다건 Presigned URL 발급 요청")
public record BulkPresignedUrlReq(
    @Schema(description = "발급할 URL 대상 목록 (1~20개)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "발급 대상 목록은 필수 입력값입니다")
    @Size(min = 1, max = 20, message = "발급 대상은 1개 이상 20개 이하여야 합니다")
    List<@NotNull(message = "발급 대상은 필수 입력값입니다") @Valid Item> items
) {
    public List<StorageUploadTarget> toTargets() {
        return items.stream()
            .map(Item::toTarget)
            .toList();
    }

    @Schema(description = "Presigned URL 발급 대상")
    public record Item(
        @Schema(description = "저장 경로 타입", example = "COLLECTION_CONTENT", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "저장 경로 타입은 필수 입력값입니다")
        StoragePathType pathType,

        @Schema(description = "파일 확장자", example = "JPG", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "파일 확장자는 필수 입력값입니다")
        FileExtension extension
    ) {
        public StorageUploadTarget toTarget() {
            return StorageUploadTarget.of(pathType, extension);
        }
    }
}
