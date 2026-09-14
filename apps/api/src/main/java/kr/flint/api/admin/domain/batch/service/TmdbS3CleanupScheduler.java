package kr.flint.api.admin.domain.batch.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import kr.flint.batch.repository.TmdbS3DeleteQueueJdbcRepository;
import kr.flint.infra.storage.s3.properties.S3Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "flint.batch.s3-cleanup.enabled", havingValue = "true")
@Slf4j
public class TmdbS3CleanupScheduler {

    private static final String ALLOWED_PREFIX = "collection/content/";

    private final TmdbS3DeleteQueueJdbcRepository queueRepository;
    private final S3Client s3Client;
    private final S3Properties s3Properties;

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
    public void deleteDueObjects() {
        for (TmdbS3DeleteQueueJdbcRepository.DeleteTarget target : queueRepository.findDue(100)) {
            if (!target.objectKey().startsWith(ALLOWED_PREFIX)) {
                queueRepository.markFailed(target.id(), 9, "Object key is outside the allowed prefix");
                continue;
            }
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(s3Properties.bucket())
                    .key(target.objectKey())
                    .build());
                queueRepository.markDeleted(target.id());
            } catch (RuntimeException exception) {
                log.warn("Deferred S3 delete failed key={}", target.objectKey(), exception);
                queueRepository.markFailed(target.id(), target.retryCount(), exception.getMessage());
            }
        }
    }
}
