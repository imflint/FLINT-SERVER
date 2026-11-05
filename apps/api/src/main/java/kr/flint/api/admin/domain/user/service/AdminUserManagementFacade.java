package kr.flint.api.admin.domain.user.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.api.admin.common.AdminAuthorizationService;
import kr.flint.api.admin.domain.user.dto.request.AdminUserModerationReq;
import kr.flint.api.admin.domain.user.dto.request.AdminUserProfileImageReq;
import kr.flint.api.admin.domain.user.dto.response.AdminUserDetailRes;
import kr.flint.api.admin.domain.user.dto.response.AdminUserModerationHistoryRes;
import kr.flint.api.admin.domain.user.dto.response.AdminUserSummaryRes;
import kr.flint.api.admin.domain.user.repository.AdminUserQueryRepository;
import kr.flint.api.admin.domain.user.repository.AdminUserQueryRepository.UserRow;
import kr.flint.api.global.storage.service.StorageFacade;
import kr.flint.infra.storage.cloudfront.CloudFrontUrlProvider;
import kr.flint.infra.storage.enums.StoragePathType;
import kr.flint.moderation.domain.UserModerationAction;
import kr.flint.moderation.service.UserModerationHistoryService;
import kr.flint.shared.dto.PaginationResponse;
import kr.flint.shared.exception.ErrorCode;
import kr.flint.shared.exception.GeneralException;
import kr.flint.shared.storage.FileExtension;
import kr.flint.shared.storage.StorageUploadUrl;
import kr.flint.user.domain.User;
import kr.flint.user.domain.UserRole;
import kr.flint.user.domain.UserStatus;
import kr.flint.user.exception.UserErrorCode;
import kr.flint.user.exception.UserException;
import kr.flint.user.service.UserService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserManagementFacade {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final AdminAuthorizationService adminAuthorizationService;
    private final AdminUserQueryRepository adminUserQueryRepository;
    private final AdminUserModerationApplicationService moderationApplicationService;
    private final UserModerationHistoryService userModerationHistoryService;
    private final CloudFrontUrlProvider cloudFrontUrlProvider;
    private final UserService userService;
    private final StorageFacade storageFacade;

    public PaginationResponse<AdminUserSummaryRes> getUsers(
        Long adminId,
        String keyword,
        UserStatus status,
        LocalDate createdFrom,
        LocalDate createdTo,
        Integer page,
        Integer size
    ) {
        adminAuthorizationService.validateAdmin(adminId);
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        LocalDateTime createdFromDateTime = createdFrom != null ? createdFrom.atStartOfDay() : null;
        LocalDateTime createdToExclusive = createdTo != null ? createdTo.plusDays(1).atStartOfDay() : null;

        var pageIds = adminUserQueryRepository.findUserIds(
            keyword,
            status,
            createdFromDateTime,
            createdToExclusive,
            safePage,
            safeSize
        );
        long totalElements = adminUserQueryRepository.countUsers(
            keyword,
            status,
            createdFromDateTime,
            createdToExclusive
        );
        Map<Long, UserRow> rowsById = adminUserQueryRepository.findUserRows(pageIds)
            .stream()
            .collect(Collectors.toMap(UserRow::userId, Function.identity()));
        var data = pageIds.stream()
            .map(rowsById::get)
            .filter(row -> row != null)
            .map(this::toSummary)
            .toList();

        return PaginationResponse.ofOffset(data, safePage, safeSize, totalElements);
    }

    public AdminUserDetailRes getUser(Long adminId, Long userId) {
        adminAuthorizationService.validateAdmin(adminId);
        return toDetail(getUserRow(userId));
    }

    public StorageUploadUrl getProfileImageUploadUrl(Long adminId, Long userId, FileExtension extension) {
        adminAuthorizationService.validateAdmin(adminId);
        validateEditableFliner(userService.getById(userId));
        return storageFacade.getUploadUrl(StoragePathType.USER_PROFILE, extension);
    }

    @Transactional
    public AdminUserDetailRes updateProfileImage(Long adminId, Long userId, AdminUserProfileImageReq request) {
        adminAuthorizationService.validateAdmin(adminId);
        validateEditableFliner(userService.getByIdForUpdate(userId));
        userService.updateProfileImage(userId, request.profileImage());
        return toDetail(getUserRow(userId));
    }

    @Transactional
    public AdminUserDetailRes moderateUser(Long adminId, Long userId, AdminUserModerationReq request) {
        adminAuthorizationService.validateAdmin(adminId);
        validateDirectModerationRequest(request);
        getUserRow(userId);

        moderationApplicationService.apply(userId, request.action(), request.expiresAt());
        userModerationHistoryService.record(
            userId,
            adminId,
            request.action(),
            request.expiresAt(),
            normalizeNullableText(request.adminMemo())
        );
        return toDetail(getUserRow(userId));
    }

    private void validateEditableFliner(User user) {
        if (user.getUserRole() != UserRole.FLINER || user.getStatus() != UserStatus.ACTIVE) {
            throw new GeneralException(ErrorCode.INVALID_INPUT, "활성 플리너의 사진만 변경할 수 있습니다.");
        }
    }

    private AdminUserSummaryRes toSummary(UserRow row) {
        return new AdminUserSummaryRes(
            row.userId(),
            row.nickname(),
            resolveNullableImage(row.profileImage()),
            row.userRole(),
            row.status(),
            normalizeWarningCount(row.warningCount()),
            isActiveWindow(row.uploadRestrictedAt(), row.uploadRestrictedUntil(), LocalDateTime.now()),
            row.uploadRestrictedUntil(),
            isActiveWindow(row.suspendedAt(), row.suspendedUntil(), LocalDateTime.now()),
            row.suspendedUntil(),
            row.createdAt()
        );
    }

    private AdminUserDetailRes toDetail(UserRow row) {
        LocalDateTime now = LocalDateTime.now();
        var recentModerations = userModerationHistoryService.getRecentHistories(row.userId())
            .stream()
            .map(AdminUserModerationHistoryRes::from)
            .toList();
        return new AdminUserDetailRes(
            row.userId(),
            row.nickname(),
            resolveNullableImage(row.profileImage()),
            row.userRole(),
            row.status(),
            normalizeWarningCount(row.warningCount()),
            isActiveWindow(row.uploadRestrictedAt(), row.uploadRestrictedUntil(), now),
            row.uploadRestrictedAt(),
            row.uploadRestrictedUntil(),
            isActiveWindow(row.suspendedAt(), row.suspendedUntil(), now),
            row.suspendedAt(),
            row.suspendedUntil(),
            row.deletedAt(),
            row.createdAt(),
            row.updatedAt(),
            recentModerations
        );
    }

    private UserRow getUserRow(Long userId) {
        UserRow row = adminUserQueryRepository.findUserRow(userId);
        if (row == null) {
            throw new UserException(UserErrorCode.USER_NOT_FOUND);
        }
        return row;
    }

    private void validateDirectModerationRequest(AdminUserModerationReq request) {
        if (request.action() == UserModerationAction.KEEP) {
            throw new GeneralException(ErrorCode.INVALID_INPUT, "회원관리에서는 유지 조치를 선택할 수 없습니다.");
        }
        if ((request.action() == UserModerationAction.RESTRICT_UPLOAD || request.action() == UserModerationAction.SUSPEND)
            && request.expiresAt() == null) {
            throw new GeneralException(ErrorCode.INVALID_INPUT, "조치 종료 시각을 선택해주세요.");
        }
    }

    private String resolveNullableImage(String imageUrl) {
        return imageUrl == null ? null : cloudFrontUrlProvider.resolveUrl(imageUrl);
    }

    private boolean isActiveWindow(LocalDateTime startsAt, LocalDateTime endsAt, LocalDateTime now) {
        return startsAt != null && !startsAt.isAfter(now) && (endsAt == null || endsAt.isAfter(now));
    }

    private int normalizeWarningCount(Integer warningCount) {
        return warningCount != null ? warningCount : 0;
    }

    private String normalizeNullableText(String value) {
        String normalized = value != null ? value.trim() : null;
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 1) {
            return 1;
        }
        return page;
    }
}
