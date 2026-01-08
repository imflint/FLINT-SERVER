package kr.flint.api.storage.service;

import kr.flint.api.config.S3TestConfig;
import kr.flint.api.storage.exception.StorageErrorCode;
import kr.flint.api.storage.exception.StorageException;
import kr.flint.shared.storage.StorageUploadUrl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(S3TestConfig.class)
class StorageFacadeTest {

    @Autowired
    private StorageFacade storageFacade;

    @Nested
    @DisplayName("getUploadUrl")
    class GetUploadUrl {

        @Test
        @DisplayName("정상적인 pathType과 extension으로 URL 발급")
        void success() {
            // when
            StorageUploadUrl response = storageFacade.getUploadUrl("USER_PROFILE", "jpg");

            // then
            assertThat(response.uploadUrl()).isNotBlank();
            assertThat(response.key()).startsWith("user/profile/");
            assertThat(response.key()).endsWith(".jpg");
        }

        @Test
        @DisplayName("대소문자 구분 없이 pathType 처리")
        void pathTypeCaseInsensitive() {
            // when
            StorageUploadUrl response = storageFacade.getUploadUrl("user_profile", "png");

            // then
            assertThat(response.key()).startsWith("user/profile/");
        }

        @Test
        @DisplayName("대소문자 구분 없이 extension 처리")
        void extensionCaseInsensitive() {
            // when
            StorageUploadUrl response = storageFacade.getUploadUrl("USER_PROFILE", "JPG");

            // then
            assertThat(response.key()).endsWith(".jpg");
        }

        @Test
        @DisplayName("허용되지 않은 확장자 - INVALID_FILE_EXTENSION")
        void invalidExtension() {
            // when & then
            assertThatThrownBy(() -> storageFacade.getUploadUrl("USER_PROFILE", "exe"))
                    .isInstanceOf(StorageException.class)
                    .extracting("errorCode")
                    .isEqualTo(StorageErrorCode.INVALID_FILE_EXTENSION);
        }

        @Test
        @DisplayName("잘못된 pathType - INVALID_PATH_TYPE")
        void invalidPathType() {
            // when & then
            assertThatThrownBy(() -> storageFacade.getUploadUrl("INVALID_TYPE", "jpg"))
                    .isInstanceOf(StorageException.class)
                    .extracting("errorCode")
                    .isEqualTo(StorageErrorCode.INVALID_PATH_TYPE);
        }
    }
}
