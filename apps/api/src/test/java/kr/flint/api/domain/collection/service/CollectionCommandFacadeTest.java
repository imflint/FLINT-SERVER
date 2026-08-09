package kr.flint.api.domain.collection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.flint.api.domain.collection.dto.request.AddContentReq;
import kr.flint.api.domain.collection.dto.request.CreateCollectionReq;
import kr.flint.api.domain.collection.dto.request.UpdateCollectionReq;
import kr.flint.collection.dto.CollectionCreateCommand;
import kr.flint.collection.dto.CollectionUpdateCommand;
import kr.flint.collection.service.CollectionService;
import kr.flint.content.service.ContentService;
import kr.flint.user.service.UserService;

@ExtendWith(MockitoExtension.class)
class CollectionCommandFacadeTest {

    @Mock
    private CollectionService collectionService;

    @Mock
    private UserService userService;

    @Mock
    private ContentService contentService;

    @InjectMocks
    private CollectionCommandFacade collectionCommandFacade;

    @Nested
    @DisplayName("createCollection")
    class CreateCollection {

        @Test
        @DisplayName("대표 이미지가 없으면 null로 컬렉션을 생성")
        void createWithNullImage() {
            CreateCollectionReq request = createRequest(null);
            when(collectionService.createCollection(eq(1L), any(CollectionCreateCommand.class), isNull()))
                .thenReturn(10L);

            Long collectionId = collectionCommandFacade.createCollection(1L, request);

            assertThat(collectionId).isEqualTo(10L);
            verify(contentService).validateContentIdsExist(List.of(100L));
            verify(collectionService).createCollection(eq(1L), any(CollectionCreateCommand.class), isNull());
        }

        @Test
        @DisplayName("대표 이미지가 있으면 전달받은 값을 유지")
        void createWithRequestedImage() {
            CreateCollectionReq request = createRequest("collection/thumbnail/image.jpg");
            ArgumentCaptor<CollectionCreateCommand> commandCaptor = ArgumentCaptor.forClass(CollectionCreateCommand.class);
            when(collectionService.createCollection(eq(1L), any(CollectionCreateCommand.class), eq(request.imageUrl())))
                .thenReturn(10L);

            collectionCommandFacade.createCollection(1L, request);

            verify(collectionService).createCollection(eq(1L), commandCaptor.capture(), eq(request.imageUrl()));
            assertThat(commandCaptor.getValue().imageUrl()).isEqualTo(request.imageUrl());
        }
    }

    @Nested
    @DisplayName("updateCollection")
    class UpdateCollection {

        @Test
        @DisplayName("대표 이미지가 공백이면 null로 컬렉션을 수정")
        void updateWithBlankImage() {
            UpdateCollectionReq request = updateRequest("   ");

            collectionCommandFacade.updateCollection(1L, 10L, request);

            verify(contentService).validateContentIdsExist(List.of(100L));
            verify(collectionService).updateCollection(eq(1L), eq(10L), any(CollectionUpdateCommand.class), isNull());
        }
    }

    private CreateCollectionReq createRequest(String imageUrl) {
        return new CreateCollectionReq(
            imageUrl,
            "컬렉션 제목",
            "컬렉션 설명",
            true,
            List.of(contentRequest())
        );
    }

    private UpdateCollectionReq updateRequest(String imageUrl) {
        return new UpdateCollectionReq(
            imageUrl,
            "컬렉션 제목",
            "컬렉션 설명",
            true,
            List.of(contentRequest())
        );
    }

    private AddContentReq contentRequest() {
        return new AddContentReq(100L, false, "선정 이유", List.of());
    }
}
