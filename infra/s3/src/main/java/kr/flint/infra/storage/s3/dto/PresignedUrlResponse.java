package kr.flint.infra.storage.s3.dto;

import lombok.Builder;

import java.time.Instant;

@Builder
public record PresignedUrlResponse(
        String url,
        String key,
        Instant expiresAt
) {

    public static PresignedUrlResponse of(String url, String key, Instant expiresAt) {
        return PresignedUrlResponse.builder()
                .url(url)
                .key(key)
                .expiresAt(expiresAt)
                .build();
    }
}