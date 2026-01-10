package kr.flint.infra.storage.s3.presigner;

import kr.flint.infra.storage.s3.dto.PresignedUrlRequest;
import kr.flint.infra.storage.s3.dto.PresignedUrlResponse;
import kr.flint.infra.storage.s3.properties.S3Properties;
import kr.flint.shared.storage.FileExtension;
import kr.flint.shared.storage.StorageUploadUrl;
import kr.flint.shared.storage.StorageUrlProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
@RequiredArgsConstructor
public class PresignedUrlGenerator implements StorageUrlProvider {

    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    @Override
    public StorageUploadUrl generateUploadUrl(String key, FileExtension fileExtension) {
        PresignedUrlResponse response = generatePresignedUploadUrl(
                PresignedUrlRequest.of(key, fileExtension)
        );
        return StorageUploadUrl.of(response.url(), response.key());
    }

    public PresignedUrlResponse generatePresignedUploadUrl(PresignedUrlRequest request) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.bucket())
                .key(request.key())
                .contentType(request.getContentType())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(s3Properties.presignExpiration())
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        return PresignedUrlResponse.of(
                presignedRequest.url().toString(),
                request.key(),
                presignedRequest.expiration()
        );
    }
}