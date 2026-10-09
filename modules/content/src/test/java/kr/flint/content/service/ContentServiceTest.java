package kr.flint.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import kr.flint.content.domain.Content;
import kr.flint.content.domain.Genre;
import kr.flint.content.domain.GenreCode;
import kr.flint.content.domain.MediaType;
import kr.flint.content.dto.ContentUpdateCommand;
import kr.flint.content.exception.ContentErrorCode;
import kr.flint.content.exception.ContentException;
import kr.flint.content.repository.ContentGenreRepository;
import kr.flint.content.repository.ContentRepository;
import kr.flint.content.repository.GenreRepository;

@ExtendWith(MockitoExtension.class)
class ContentServiceTest {

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private ContentGenreRepository contentGenreRepository;

    @Mock
    private GenreRepository genreRepository;

    @InjectMocks
    private ContentService contentService;

    @Test
    @DisplayName("관리자 콘텐츠 수정은 허용된 메타데이터만 변경")
    void updateByAdmin() {
        Content content = Content.create(100L, MediaType.MOVIE, "기존 제목", 2024, "기존 감독", "기존 설명", "old.jpg");
        ReflectionTestUtils.setField(content, "id", 1L);
        content.increaseBookmarkCount();
        when(contentRepository.findById(1L)).thenReturn(Optional.of(content));
        when(genreRepository.findAllByCodeIn(java.util.Set.of(GenreCode.SCIENCE_FICTION)))
            .thenReturn(List.of(Genre.create("SF")));

        Content result = contentService.updateByAdmin(1L, ContentUpdateCommand.of(
            "새 제목",
            2026,
            "새 감독",
            "새 설명",
            "new.jpg",
            List.of("SF")
        ));

        assertThat(result.getTmdbId()).isEqualTo(100L);
        assertThat(result.getMediaType()).isEqualTo(MediaType.MOVIE);
        assertThat(result.getBookmarkCount()).isEqualTo(1);
        assertThat(result.getTitle()).isEqualTo("새 제목");
        assertThat(result.getYear()).isEqualTo(2026);
        assertThat(result.getAuthor()).isEqualTo("새 감독");
        assertThat(result.getDescription()).isEqualTo("새 설명");
        assertThat(result.getPoster()).isEqualTo("new.jpg");
        verify(contentGenreRepository).deleteAllByContent(content);
        verify(contentGenreRepository).saveAll(any());
    }

    @Test
    @DisplayName("콘텐츠 ID 목록 검증은 존재하지 않는 콘텐츠가 있으면 예외")
    void validateContentIdsExist() {
        when(contentRepository.countByIdIn(List.of(1L, 2L))).thenReturn(1L);

        assertThatThrownBy(() -> contentService.validateContentIdsExist(List.of(1L, 2L)))
            .isInstanceOf(ContentException.class)
            .extracting("errorCode")
            .isEqualTo(ContentErrorCode.CONTENT_NOT_FOUND);
    }

    @Test
    void adminGenreAliasesResolveOnceWithoutCreatingNewMasters() {
        Content content = Content.create(100L, MediaType.MOVIE,"title",2020,null,null,"poster");
        ReflectionTestUtils.setField(content,"id",1L);
        when(contentRepository.findById(1L)).thenReturn(Optional.of(content));
        Genre action=Genre.create("액션");
        when(genreRepository.findAllByCodeIn(java.util.Set.of(GenreCode.ACTION))).thenReturn(List.of(action));
        contentService.updateByAdmin(1L,ContentUpdateCommand.of(null,null,null,null,null,
            List.of("Action & Adventure","ACTION","액션")));
        var captor=org.mockito.ArgumentCaptor.forClass(Iterable.class);
        verify(contentGenreRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        org.mockito.Mockito.verify(genreRepository,org.mockito.Mockito.never()).save(any());
    }

    @Test
    void unknownGenreRejectsBeforeChangingMetadata() {
        assertThatThrownBy(() -> contentService.updateByAdmin(1L,ContentUpdateCommand.of(
            "changed",null,null,null,null,List.of("unregistered"))))
            .isInstanceOf(kr.flint.shared.exception.GeneralException.class);
        org.mockito.Mockito.verifyNoInteractions(contentRepository,contentGenreRepository,genreRepository);
    }
}
