package kr.flint.batch.job.ott;

import java.util.List;

public record OttSyncDraft(Long contentId, List<String> providerNames) {
}
