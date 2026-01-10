package kr.flint.api.storage.service;

import kr.flint.api.storage.exception.StorageErrorCode;
import kr.flint.api.storage.exception.StorageException;
import kr.flint.infra.storage.enums.StoragePathType;
import kr.flint.shared.storage.StorageKeyGenerator;
import kr.flint.shared.storage.StorageUploadUrl;
import kr.flint.shared.storage.StorageUrlProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StorageFacade {

    private final StorageUrlProvider storageUrlProvider;

    public StorageUploadUrl getUploadUrl(StoragePathType pathType, String extension) {
        String normalizedExtension = extension.toLowerCase().trim();
        validateExtension(pathType, normalizedExtension);

        String key = StorageKeyGenerator.generate(pathType, normalizedExtension);
        String contentType = StorageKeyGenerator.getContentType(normalizedExtension);

        return storageUrlProvider.generateUploadUrl(key, contentType);
    }

    private void validateExtension(StoragePathType pathType, String extension) {
        if (!pathType.isAllowedExtension(extension)) {
            throw new StorageException(StorageErrorCode.INVALID_FILE_EXTENSION);
        }
    }
}
