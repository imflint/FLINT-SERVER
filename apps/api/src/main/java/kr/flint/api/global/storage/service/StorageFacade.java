package kr.flint.api.global.storage.service;

import kr.flint.api.global.storage.exception.StorageErrorCode;
import kr.flint.api.global.storage.exception.StorageException;
import kr.flint.infra.storage.enums.StoragePathType;
import kr.flint.shared.storage.FileExtension;
import kr.flint.shared.storage.StorageKeyGenerator;
import kr.flint.shared.storage.StorageUploadUrl;
import kr.flint.shared.storage.StorageUrlProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StorageFacade {

    private final StorageUrlProvider storageUrlProvider;

    public StorageUploadUrl getUploadUrl(StoragePathType pathType, FileExtension fileExtension) {
        validateExtension(pathType, fileExtension);

        String key = StorageKeyGenerator.generate(pathType, fileExtension);

        return storageUrlProvider.generateUploadUrl(key, fileExtension);
    }

    private void validateExtension(StoragePathType pathType, FileExtension fileExtension) {
        if (!pathType.getAllowedExtensions().contains(fileExtension)) {
            throw new StorageException(StorageErrorCode.INVALID_FILE_EXTENSION);
        }
    }
}
