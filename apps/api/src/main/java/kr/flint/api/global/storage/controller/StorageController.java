package kr.flint.api.global.storage.controller;

import kr.flint.api.global.storage.controller.spec.StorageControllerDocs;
import kr.flint.api.global.storage.dto.request.BulkPresignedUrlReq;
import kr.flint.api.global.storage.dto.response.BulkStorageUploadUrlRes;
import kr.flint.api.global.storage.service.StorageFacade;
import kr.flint.infra.storage.enums.StoragePathType;
import kr.flint.shared.storage.FileExtension;
import kr.flint.shared.storage.StorageUploadUrl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/storage")
@RequiredArgsConstructor
public class StorageController implements StorageControllerDocs {

    private final StorageFacade storageFacade;

    @Override
    @GetMapping("/presigned-url")
    public StorageUploadUrl getUploadUrl(
            @RequestParam StoragePathType pathType,
            @RequestParam FileExtension extension
    ) {
        return storageFacade.getUploadUrl(pathType, extension);
    }

    @Override
    @PostMapping("/presigned-urls")
    public BulkStorageUploadUrlRes getUploadUrls(
            @Valid @RequestBody BulkPresignedUrlReq request
    ) {
        return BulkStorageUploadUrlRes.of(
                storageFacade.getUploadUrls(request.toTargets())
        );
    }
}
