package kr.flint.taste.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.taste.dto.response.UserKeywordProjection;
import kr.flint.taste.repository.UserKeywordRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TasteService {

    private final UserKeywordRepository userKeywordRepository;

    public List<UserKeywordProjection> getUserKeywords(Long userId) {
        return userKeywordRepository.findUserKeywordsWithDetails(userId);
    }
}
