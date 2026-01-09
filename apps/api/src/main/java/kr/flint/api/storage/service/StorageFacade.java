package kr.flint.api.storage.service;

import kr.flint.infra.storage.enums.StoragePathType;
import kr.flint.api.storage.exception.StorageErrorCode;
import kr.flint.api.storage.exception.StorageException;
import kr.flint.shared.storage.StorageKeyGenerator;
import kr.flint.shared.storage.StorageUploadUrl;
import kr.flint.shared.storage.StorageUrlProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StorageFacade {

    private final StorageUrlProvider storageUrlProvider;

    public StorageUploadUrl getUploadUrl(String pathType, String extension) {
        StoragePathType storagePathType = parsePathType(pathType);
        String normalizedExtension = extension.toLowerCase().trim();
        validateExtension(storagePathType, normalizedExtension);

        String key = StorageKeyGenerator.generate(storagePathType, normalizedExtension);
        String contentType = StorageKeyGenerator.getContentType(normalizedExtension);

        return storageUrlProvider.generateUploadUrl(key, contentType);
    }

    private StoragePathType parsePathType(String pathType) {
        try {
            return StoragePathType.valueOf(pathType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new StorageException(StorageErrorCode.INVALID_PATH_TYPE);
        }
    }

    private void validateExtension(StoragePathType pathType, String extension) {
        if (!pathType.isAllowedExtension(extension)) {
            throw new StorageException(StorageErrorCode.INVALID_FILE_EXTENSION);
        }
    }
}
