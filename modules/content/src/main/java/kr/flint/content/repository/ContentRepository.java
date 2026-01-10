package kr.flint.content.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import kr.flint.content.domain.Content;

public interface ContentRepository extends JpaRepository<Content, Long> {
}
