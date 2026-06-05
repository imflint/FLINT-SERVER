package kr.flint.api.global.storage.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.shared.storage.StorageUploadUrl;

@Schema(description = "다건 Presigned URL 발급 응답")
public record BulkStorageUploadUrlRes(
        @ArraySchema(schema = @Schema(implementation = StorageUploadUrl.class))
        List<StorageUploadUrl> urls
) {
    public BulkStorageUploadUrlRes {
        urls = urls == null ? List.of() : List.copyOf(urls);
    }

    public static BulkStorageUploadUrlRes of(List<StorageUploadUrl> urls) {
        return new BulkStorageUploadUrlRes(urls);
    }
}
