package kr.flint.api.storage.service;

import kr.flint.api.config.AppleOAuthTestConfig;
import kr.flint.api.config.GptTestConfig;
import kr.flint.api.config.RedisTestConfig;
import kr.flint.api.config.S3TestConfig;
import kr.flint.api.global.storage.exception.StorageErrorCode;
import kr.flint.api.global.storage.exception.StorageException;
import kr.flint.api.global.storage.service.StorageFacade;
import kr.flint.api.global.storage.service.StorageUploadTarget;
import kr.flint.infra.storage.enums.StoragePathType;
import kr.flint.shared.storage.FileExtension;
import kr.flint.shared.storage.StorageUploadUrl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
@Import({S3TestConfig.class, RedisTestConfig.class, GptTestConfig.class, AppleOAuthTestConfig.class})
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
            StorageUploadUrl response = storageFacade.getUploadUrl(StoragePathType.USER_PROFILE, FileExtension.JPG);

            // then
            assertThat(response.uploadUrl()).isNotBlank();
            assertThat(response.key()).startsWith("user/profile/");
            assertThat(response.key()).endsWith(".jpg");
        }

        @Test
        @DisplayName("허용되지 않은 확장자 - INVALID_FILE_EXTENSION")
        void invalidExtension() {
            // when & then (USER_PROFILE은 JPG, JPEG, PNG만 허용)
            assertThatThrownBy(() -> storageFacade.getUploadUrl(StoragePathType.USER_PROFILE, FileExtension.PDF))
                    .isInstanceOf(StorageException.class)
                    .extracting("errorCode")
                    .isEqualTo(StorageErrorCode.INVALID_FILE_EXTENSION);
        }
    }

    @Nested
    @DisplayName("getUploadUrls")
    class GetUploadUrls {

        @Test
        @DisplayName("각 item의 pathType과 extension으로 URL 발급")
        void success() {
            // when
            List<StorageUploadUrl> responses = storageFacade.getUploadUrls(List.of(
                    StorageUploadTarget.of(StoragePathType.COLLECTION_THUMBNAIL, FileExtension.PNG),
                    StorageUploadTarget.of(StoragePathType.COLLECTION_CONTENT, FileExtension.JPG),
                    StorageUploadTarget.of(StoragePathType.USER_PROFILE, FileExtension.JPEG)
            ));

            // then
            assertThat(responses).hasSize(3);
            assertThat(responses)
                    .extracting(StorageUploadUrl::key)
                    .doesNotHaveDuplicates();
            assertThat(responses.get(0).key()).startsWith("collection/thumbnail/");
            assertThat(responses.get(0).key()).endsWith(".png");
            assertThat(responses.get(1).key()).startsWith("collection/content/");
            assertThat(responses.get(1).key()).endsWith(".jpg");
            assertThat(responses.get(2).key()).startsWith("user/profile/");
            assertThat(responses.get(2).key()).endsWith(".jpeg");
            assertThat(responses)
                    .extracting(StorageUploadUrl::uploadUrl)
                    .allSatisfy(uploadUrl -> assertThat(uploadUrl).isNotBlank());
        }

        @Test
        @DisplayName("허용되지 않은 확장자 - INVALID_FILE_EXTENSION")
        void invalidExtension() {
            // when & then (COLLECTION_CONTENT는 JPG, JPEG, PNG만 허용)
            assertThatThrownBy(() -> storageFacade.getUploadUrls(List.of(
                    StorageUploadTarget.of(StoragePathType.COLLECTION_CONTENT, FileExtension.JPG),
                    StorageUploadTarget.of(StoragePathType.COLLECTION_CONTENT, FileExtension.PDF)
            )))
                    .isInstanceOf(StorageException.class)
                    .extracting("errorCode")
                    .isEqualTo(StorageErrorCode.INVALID_FILE_EXTENSION);
        }
    }
}
