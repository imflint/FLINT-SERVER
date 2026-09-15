package kr.flint.api.admin.domain.batch.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LanguageCleanupExecuteReq(
    @NotNull Long manifestId,
    @NotBlank String candidateHash,
    boolean snapshotConfirmed
) {
}
