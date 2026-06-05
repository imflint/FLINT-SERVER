package kr.flint.api.global.storage.service;

import kr.flint.infra.storage.enums.StoragePathType;
import kr.flint.shared.storage.FileExtension;

public record StorageUploadTarget(
        StoragePathType pathType,
        FileExtension fileExtension
) {
    public static StorageUploadTarget of(StoragePathType pathType, FileExtension fileExtension) {
        return new StorageUploadTarget(pathType, fileExtension);
    }
}
