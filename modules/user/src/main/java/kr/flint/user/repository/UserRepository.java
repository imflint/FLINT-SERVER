package kr.flint.user.repository;

import kr.flint.user.domain.User;
import kr.flint.user.dto.response.UserSimpleRes;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByNickname(String nickname);

    boolean existsByNickname(String nickname);

    @Query("""
        select new kr.flint.user.dto.response.UserSimpleRes(u.id, u.nickname, u.profileImage, str(u.userRole))
        from User u
        where u.id in :ids
    """)
        List<UserSimpleRes> findByIds(@Param("ids") List<Long> ids);

        @Query("""
            select count(u)
            from User u
            where u.status = kr.flint.user.domain.UserStatus.ACTIVE
                and (
                    u.suspendedAt is null
                    or (u.suspendedUntil is not null and u.suspendedUntil <= :now)
                )
        """)
        long countActiveUsers(@Param("now") LocalDateTime now);
    }
