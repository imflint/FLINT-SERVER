package kr.flint.content.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import kr.flint.content.domain.Content;
import kr.flint.content.domain.ContentGenre;
import kr.flint.content.domain.Genre;
import kr.flint.content.domain.MediaType;
import kr.flint.content.dto.ContentUpdateCommand;
import kr.flint.content.dto.ContentUpsertCommand;
import kr.flint.content.dto.ContentWithGenres;
import kr.flint.content.exception.ContentErrorCode;
import kr.flint.content.exception.ContentException;
import kr.flint.content.repository.ContentGenreRepository;
import kr.flint.content.repository.ContentRepository;
import kr.flint.content.repository.GenreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class ContentService {
    private final ContentRepository contentRepository;
    private final ContentGenreRepository contentGenreRepository;
    private final GenreRepository genreRepository;

    public Content getContentById(final Long contentId) {
        return contentRepository.findById(contentId)
            .orElseThrow(() -> new ContentException(ContentErrorCode.CONTENT_NOT_FOUND));
    }

    public void validateContentIdsExist(final List<Long> contentIds) {
        if (CollectionUtils.isEmpty(contentIds)) {
            return;
        }

        List<Long> distinctContentIds = contentIds.stream().distinct().toList();
        long expectedCount = distinctContentIds.size();
        long actualCount = contentRepository.countByIdIn(distinctContentIds);
        if (actualCount != expectedCount) {
            throw new ContentException(ContentErrorCode.CONTENT_NOT_FOUND);
        }
    }

    @Transactional
    public void increaseBookmarkCount(final Long contentId) {
        Content content = getContentById(contentId);
        content.increaseBookmarkCount();
    }

    @Transactional
    public void decreaseBookmarkCount(final Long contentId) {
        Content content = getContentById(contentId);
        content.decreaseBookmarkCount();
    }

    @Transactional
    public Content tmdbToDb(final Content content, List<Genre> genreList) {
        Content savedContent = contentRepository.save(content);
        List<Genre> savedGenreList = genreList != null ? genreRepository.saveAll(genreList) : List.of();
        List<ContentGenre> contentGenreList = savedGenreList.stream()
            .map(genre -> ContentGenre.create(savedContent, genre))
            .toList();

        contentGenreRepository.saveAll(contentGenreList);

        return savedContent;
    }

    // 배치 적재용: 이미 존재하면 메타데이터 갱신, 없으면 신규 생성. 장르는 정규화 후 ContentGenre 동기화.
    @Transactional
    public Content upsertWithGenres(final ContentUpsertCommand command) {
        Content content = contentRepository.findByTmdbIdAndMediaType(command.tmdbId(), command.mediaType())
            .map(existing -> {
                existing.updateMetadata(
                    command.title(),
                    command.year(),
                    command.author(),
                    command.description(),
                    command.poster()
                );
                return existing;
            })
            .orElseGet(() -> contentRepository.save(Content.create(
                command.tmdbId(),
                command.mediaType(),
                command.title(),
                command.year(),
                command.author(),
                command.description(),
                command.poster()
            )));

        syncGenres(content, command.genreNames());
        return content;
    }

    @Transactional
    public Content updateByAdmin(final Long contentId, final ContentUpdateCommand command) {
        Content content = getContentById(contentId);
        content.updateMetadata(
            valueOrCurrent(command.title(), content.getTitle()),
            command.year() != null ? command.year() : content.getYear(),
            valueOrCurrent(command.author(), content.getAuthor()),
            valueOrCurrent(command.description(), content.getDescription()),
            valueOrCurrent(command.poster(), content.getPoster())
        );
        if (command.genreNames() != null) {
            replaceGenres(content, command.genreNames());
        }
        return content;
    }

    private void syncGenres(Content content, List<String> genreNames) {
        if (CollectionUtils.isEmpty(genreNames)) {
            return;
        }

        Map<String, Genre> resolved = new HashMap<>();
        for (String name : new HashSet<>(genreNames)) {
            Genre genre = genreRepository.findByName(name)
                .orElseGet(() -> genreRepository.save(Genre.create(name)));
            resolved.put(name, genre);
        }

        Set<Long> alreadyLinked = contentGenreRepository.findAllByContentIdsWithGenre(List.of(content.getId())).stream()
            .map(cg -> cg.getGenre().getId())
            .collect(Collectors.toSet());

        List<ContentGenre> toAdd = resolved.values().stream()
            .filter(g -> !alreadyLinked.contains(g.getId()))
            .map(g -> ContentGenre.create(content, g))
            .toList();
        if (!toAdd.isEmpty()) {
            contentGenreRepository.saveAll(toAdd);
        }
    }

    private void replaceGenres(Content content, List<String> genreNames) {
        contentGenreRepository.deleteAllByContent(content);
        contentGenreRepository.flush();
        if (CollectionUtils.isEmpty(genreNames)) {
            return;
        }

        List<ContentGenre> contentGenres = genreNames.stream()
            .distinct()
            .map(name -> genreRepository.findByName(name)
                .orElseGet(() -> genreRepository.save(Genre.create(name))))
            .map(genre -> ContentGenre.create(content, genre))
            .toList();
        contentGenreRepository.saveAll(contentGenres);
    }

    private String valueOrCurrent(String requested, String current) {
        return requested != null ? requested : current;
    }

    public boolean checkGenre(final String genre) {
        log.debug("장르 존재 여부 확인. genre={}", genre);
        return genreRepository.existsByName(genre);
    }

    public Genre getGenre(final String genreName) {
        return genreRepository.findByName(genreName)
            .orElseThrow(() -> new ContentException(ContentErrorCode.GENRE_NOT_FOUND));
    }

    public Content getContentByTmdbIdAndMediaType(final Long tmdbId, final MediaType mediaType) {
        return contentRepository.findByTmdbIdAndMediaType(tmdbId, mediaType)
            .orElse(null);
    }

    public List<Content> getContentByTitle(final String title) {
        return contentRepository.findAllByTitleContaining(title);
    }

    public List<Content> getContentByTitle(final String title, final int limit) {
        return contentRepository.findAllByTitleContaining(title, PageRequest.of(0, limit));
    }

    public List<Content> getAllContent() {
        return contentRepository.findAll();
    }

    public List<Content> getRecentContent(final int limit) {
        return contentRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit));
    }

    public List<Content> getPopularContents(final int limit) {
        return contentRepository.findAllByOrderByBookmarkCountDescIdDesc(PageRequest.of(0, limit));
    }

    // 콘텐츠 ID 목록으로 콘텐츠 + 장르 정보 조회
    public List<ContentWithGenres> getContentsWithGenres(List<Long> contentIds) {
        if (CollectionUtils.isEmpty(contentIds)) {
            return List.of();
        }

        List<Content> contents = contentRepository.findAllById(contentIds);
        List<ContentGenre> contentGenres = contentGenreRepository.findAllByContentIdsWithGenre(contentIds);

        // contentId -> genre names 매핑
        Map<Long, List<String>> genreMap = contentGenres.stream()
            .collect(Collectors.groupingBy(
                cg -> cg.getContent().getId(),
                Collectors.mapping(cg -> cg.getGenre().getName(), Collectors.toList())
            ));

        return contents.stream()
            .map(content -> ContentWithGenres.of(
                content,
                genreMap.getOrDefault(content.getId(), List.of())
            ))
            .toList();
    }

    public void incContentBookmarkCount(final Long contentId) {
        contentRepository.incBookmarkCount(contentId);
    }
}
