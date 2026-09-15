package kr.flint.batch.job.ott;

import java.util.List;

public record OttSyncDraft(
    Long contentId,
    String contentUrl,
    List<Provider> providers
) {
    public OttSyncDraft {
        providers = providers == null ? List.of() : List.copyOf(providers);
    }

    public record Provider(
        Long tmdbProviderId,
        String name,
        String logoUrl,
        int displayPriority
    ) {
    }
}
