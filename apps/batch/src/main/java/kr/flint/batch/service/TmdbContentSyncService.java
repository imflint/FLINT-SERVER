package kr.flint.batch.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import kr.flint.batch.job.ContentSyncDraft;
import kr.flint.batch.repository.ContentBatchJdbcRepository;
import kr.flint.batch.repository.ContentBatchJdbcRepository.ContentIdentity;
import kr.flint.batch.repository.OttBatchJdbcRepository;
import kr.flint.batch.repository.TmdbContentAdmissionJdbcRepository;
import kr.flint.batch.repository.TmdbContentAdmissionJdbcRepository.ExistingContent;
import kr.flint.batch.repository.TmdbContentAdmissionJdbcRepository.TitleKey;
import kr.flint.content.dto.ContentCatalogStatus;
import kr.flint.content.dto.ContentUpsertCommand;
import kr.flint.shared.exception.ErrorCode;
import kr.flint.shared.exception.GeneralException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TmdbContentSyncService {

    private final TmdbContentAdmissionJdbcRepository admissionRepository;
    private final ContentBatchJdbcRepository contentRepository;
    private final OttBatchJdbcRepository ottRepository;

    public void ensureSchemaReady() {
        if (!admissionRepository.schemaReady() || !contentRepository.genreSchemaReady()) {
            throw new GeneralException(ErrorCode.CONFLICT, "TMDB admission DDL 또는 표준 장르 이관이 완료되지 않았습니다.");
        }
    }

    @Transactional
    public void synchronize(List<ContentSyncDraft> drafts) {
        if (drafts.isEmpty()) {
            return;
        }
        drafts = drafts.stream().filter(java.util.Objects::nonNull).filter(d -> d.content() != null).toList();
        admissionRepository.lockWrites();
        List<ContentUpsertCommand> validated = contentRepository.validateGenres(drafts.stream()
            .filter(ContentSyncDraft::persistContent).map(ContentSyncDraft::content).toList());
        Map<ContentIdentity, ContentUpsertCommand> byIdentity = new HashMap<>();
        validated.forEach(command -> byIdentity.put(identity(command), command));
        List<Admission> admitted = admit(drafts.stream().map(draft -> {
            ContentUpsertCommand command = draft.persistContent()
                ? byIdentity.get(identity(draft.content())) : draft.content();
            return new ContentSyncDraft(command, command.syncable() ? draft.ott() : null, draft.persistContent());
        }).toList(), false);
        contentRepository.classifyAll(admitted.stream().map(a -> a.draft().content()).toList());
        Set<ContentIdentity> preserveTitles = preservedIdentities(admitted);
        contentRepository.restoreRegistryTitles(preserveTitles);
        List<ContentSyncDraft> accepted = admitted.stream().map(Admission::draft)
            .filter(ContentSyncDraft::persistContent).filter(d -> d.content().syncable()).toList();
        List<ContentUpsertCommand> commands = accepted.stream().map(ContentSyncDraft::content).toList();
        contentRepository.upsertClassified(commands, preserveTitles);
        Map<ContentIdentity, Long> ids = contentRepository.findContentIdsFor(commands);
        ottRepository.replaceProviders(accepted.stream().filter(d -> d.ott() != null).map(draft -> {
            ContentIdentity identity = identity(draft.content());
            Long contentId = ids.get(identity);
            if (contentId == null) {
                throw new IllegalStateException("Content was not found after upsert: " + identity);
            }
            return draft.ott().toDraft(contentId);
        }).toList());
    }

    @Transactional
    public TitlePromotionProgress promoteTitleChunk(long afterId, int limit) {
        admissionRepository.lockWrites();
        var candidates = admissionRepository.findPromotionCandidates(afterId, limit);
        if (candidates.isEmpty()) {
            return new TitlePromotionProgress(afterId, 0, 0);
        }
        List<Admission> admitted = admit(candidates.stream()
            .map(c -> ContentSyncDraft.classified(c.command()).classificationOnly()).toList(), true);
        List<ContentUpsertCommand> updates = admitted.stream().filter(a -> !a.preserveTitles())
            .map(a -> a.draft().content()).filter(ContentUpsertCommand::syncable).toList();
        contentRepository.promoteTitles(updates);
        contentRepository.restoreRegistryTitles(preservedIdentities(admitted));
        return new TitlePromotionProgress(candidates.getLast().id(), candidates.size(), updates.size());
    }

    // DB 소유권과 같은 chunk의 예약을 함께 비교한다. 분류 전용은 실제 기존 제목의 소유권을 해제하지 않는다.
    private List<Admission> admit(List<ContentSyncDraft> drafts, boolean promotion) {
        Map<ContentIdentity, ContentSyncDraft> latest = new LinkedHashMap<>();
        for (ContentSyncDraft draft : drafts) {
            if (draft != null && draft.content() != null) {
                ContentUpsertCommand command = draft.content();
                if (command.syncable() && !StringUtils.hasText(command.titleKo()) && !StringUtils.hasText(command.titleEn())) {
                    command = ContentUpsertCommand.classified(command.tmdbId(), command.mediaType(),
                        ContentCatalogStatus.INELIGIBLE_LANGUAGE, null);
                    draft = new ContentSyncDraft(command, null, draft.persistContent());
                }
                latest.put(identity(command), draft);
            }
        }
        List<ContentUpsertCommand> commands = latest.values().stream().map(ContentSyncDraft::content)
            .filter(ContentUpsertCommand::syncable).toList();
        Map<ContentIdentity, ExistingContent> existing = admissionRepository.findExisting(commands);
        Map<ContentIdentity, TitleKey> candidateKeys = admissionRepository.candidateKeys(commands);
        Map<TitleKey, Set<ContentIdentity>> owners = new HashMap<>();
        existing.values().forEach(c -> reserve(owners, c.titleKey(), c.identity()));
        admissionRepository.findTitleOwners(new ArrayList<>(candidateKeys.values()))
            .forEach(c -> reserve(owners, c.titleKey(), c.identity()));
        List<ContentSyncDraft> ordered = new ArrayList<>(latest.values());
        ordered.sort(Comparator.comparing(d -> !existing.containsKey(identity(d.content()))));
        List<Admission> result = new ArrayList<>();
        for (ContentSyncDraft draft : ordered) {
            ContentUpsertCommand command = draft.content();
            ContentIdentity identity = identity(command);
            TitleKey target = candidateKeys.get(identity);
            ExistingContent current = existing.get(identity);
            boolean conflict = command.syncable() && target != null
                && owners.getOrDefault(target, Set.of()).stream().anyMatch(owner -> !owner.equals(identity));
            boolean preserve = false;
            if (conflict) {
                if (current == null || current.year() != command.year()) {
                    command = command.duplicateTitle("Duplicate display title, media type and year: " + command.year());
                    draft = new ContentSyncDraft(command, null, draft.persistContent());
                } else {
                    preserve = true;
                    command = command.preservingTitles(current.titleKo(), current.titleEn());
                    draft = new ContentSyncDraft(command, draft.ott(), draft.persistContent());
                }
            }
            if (command.syncable()) {
                if (!preserve && current != null && (draft.persistContent() || promotion)
                    && current.titleKey() != null) {
                    owners.getOrDefault(current.titleKey(), Set.of()).remove(identity);
                }
                reserve(owners, preserve ? current.titleKey() : target, identity);
            }
            result.add(new Admission(draft, preserve));
        }
        return result;
    }

    private void reserve(Map<TitleKey, Set<ContentIdentity>> owners, TitleKey key, ContentIdentity identity) {
        if (key != null) {
            owners.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(identity);
        }
    }

    private Set<ContentIdentity> preservedIdentities(List<Admission> admitted) {
        Set<ContentIdentity> result = new LinkedHashSet<>();
        admitted.stream().filter(Admission::preserveTitles).forEach(a -> result.add(identity(a.draft().content())));
        return result;
    }

    private ContentIdentity identity(ContentUpsertCommand command) {
        return TmdbContentAdmissionJdbcRepository.identity(command);
    }

    private record Admission(ContentSyncDraft draft, boolean preserveTitles) {
    }

    public record TitlePromotionProgress(long lastId, int processedCount, int promotedCount) {
    }
}
