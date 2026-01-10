package kr.flint.shared.storage;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StorageKeyGenerator {

    public static String generate(StoragePath storagePath, FileExtension fileExtension) {
        LocalDate now = LocalDate.now();
        return String.format("%s/%02d%02d%02d/%s.%s",
                storagePath.getPath(),
                now.getYear() % 100,
                now.getMonthValue(),
                now.getDayOfMonth(),
                UUID.randomUUID(),
                fileExtension.getExtension()
        );
    }
}
