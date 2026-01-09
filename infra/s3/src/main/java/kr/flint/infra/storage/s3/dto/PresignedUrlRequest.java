package kr.flint.infra.storage.s3.dto;

import lombok.Builder;

@Builder
public record PresignedUrlRequest(
        String key,
        String contentType
) {

    public static PresignedUrlRequest of(String key, String contentType) {
        return PresignedUrlRequest.builder()
                .key(key)
                .contentType(contentType)
                .build();
    }
}