package kr.flint.adminauth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import kr.flint.adminauth.domain.Admin;

public interface AdminUserRepository extends JpaRepository<Admin, Long> {

    Optional<Admin> findByUsername(String username);

}
