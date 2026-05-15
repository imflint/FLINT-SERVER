package kr.flint.content.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import kr.flint.content.domain.MediaType;
import kr.flint.content.dto.ContentUpdateCommand;
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
        when(genreRepository.findByName("SF")).thenReturn(Optional.of(Genre.create("SF")));

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
}
