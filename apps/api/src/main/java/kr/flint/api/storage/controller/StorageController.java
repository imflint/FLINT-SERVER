package kr.flint.api.storage.controller;

import kr.flint.api.storage.controller.spec.StorageControllerDocs;
import kr.flint.api.storage.service.StorageFacade;
import kr.flint.shared.storage.StorageUploadUrl;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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
            @RequestParam String pathType,
            @RequestParam String extension
    ) {
        return storageFacade.getUploadUrl(pathType, extension);
    }
}
