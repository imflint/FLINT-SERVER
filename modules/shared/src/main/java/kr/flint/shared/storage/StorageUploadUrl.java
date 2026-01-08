package kr.flint.shared.storage;

import lombok.Builder;

@Builder
public record StorageUploadUrl(
        String uploadUrl,
        String key
) {

    public static StorageUploadUrl of(String uploadUrl, String key) {
        return StorageUploadUrl.builder()
                .uploadUrl(uploadUrl)
                .key(key)
                .build();
    }
}
