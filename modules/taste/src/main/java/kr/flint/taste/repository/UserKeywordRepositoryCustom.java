package kr.flint.taste.repository;

import java.util.List;

import kr.flint.taste.domain.UserKeyword;

public interface UserKeywordRepositoryCustom {

    void bulkUpsert(Long userId, List<UserKeyword> userKeywords);
}
