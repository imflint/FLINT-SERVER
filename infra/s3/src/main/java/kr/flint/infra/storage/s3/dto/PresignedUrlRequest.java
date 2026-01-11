package kr.flint.infra.storage.s3.dto;

import kr.flint.shared.storage.FileExtension;
import lombok.Builder;

@Builder
public record PresignedUrlRequest(
        String key,
        FileExtension fileExtension
) {

    public static PresignedUrlRequest of(String key, FileExtension fileExtension) {
        return PresignedUrlRequest.builder()
                .key(key)
                .fileExtension(fileExtension)
                .build();
    }

    public String getContentType() {
        return fileExtension.getContentType();
    }
}