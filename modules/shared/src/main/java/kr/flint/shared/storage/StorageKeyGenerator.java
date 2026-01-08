package kr.flint.shared.storage;

import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@NoArgsConstructor
public final class StorageKeyGenerator {

    public static String generate(StoragePath storagePath, String extension) {
        LocalDate now = LocalDate.now();
        return String.format("%s/%02d%02d%02d/%s.%s",
                storagePath.getPath(),
                now.getYear() % 100,
                now.getMonthValue(),
                now.getDayOfMonth(),
                UUID.randomUUID(),
                extension.toLowerCase()
        );
    }

    public static String getContentType(String extension) {
        return switch (extension.toLowerCase()) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }
}
