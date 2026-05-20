package kr.flint.user.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import io.hypersistence.tsid.TSID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import kr.flint.user.repository.DailyVisitorActivityRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DailyVisitorActivityService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final DailyVisitorActivityRepository dailyVisitorActivityRepository;

    @Transactional
    public void recordVisit(String visitorKeyHash, Long userId) {
        LocalDateTime now = LocalDateTime.now(SEOUL_ZONE);
        recordVisit(visitorKeyHash, userId, now.toLocalDate(), now);
    }

    @Transactional
    public void recordVisit(String visitorKeyHash, Long userId, LocalDate activityDate, LocalDateTime seenAt) {
        if (!StringUtils.hasText(visitorKeyHash)) {
            return;
        }

        dailyVisitorActivityRepository.insertFirstVisit(
            TSID.Factory.getTsid().toLong(),
            visitorKeyHash,
            userId,
            activityDate,
            seenAt
        );
    }

    public Optional<LocalDate> findFirstActivityDate() {
        return Optional.ofNullable(dailyVisitorActivityRepository.findFirstActivityDate());
    }
}
