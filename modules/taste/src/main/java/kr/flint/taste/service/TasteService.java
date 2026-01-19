package kr.flint.taste.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.taste.domain.Keyword;
import kr.flint.taste.domain.UserKeyword;
import kr.flint.taste.dto.response.KeywordSimpleRes;
import kr.flint.taste.dto.response.UserKeywordProjection;
import kr.flint.taste.exception.TasteErrorCode;
import kr.flint.taste.exception.TasteExecption;
import kr.flint.taste.repository.KeywordRepository;
import kr.flint.taste.repository.UserKeywordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class TasteService {

    private final UserKeywordRepository userKeywordRepository;
	private final KeywordRepository keywordRepository;

    public List<UserKeywordProjection> getUserKeywords(Long userId) {
        return userKeywordRepository.findUserKeywordsWithDetails(userId);
    }


	//TODO : LV 대신 색상
	@Transactional
	public void matchUserKeywords(Long userId, List<KeywordSimpleRes> gptKeywordList){
		List<String> keywordNameList = gptKeywordList.stream()
			.map(KeywordSimpleRes::name)
			.toList();

		List<Keyword> keywordList = keywordRepository.findAllByNameIn(keywordNameList);

		Map<String, Keyword> keywordMap = keywordList.stream()
			.collect(Collectors.toMap(Keyword::getName, k -> k));


		List<UserKeyword> userKeywordList = gptKeywordList.stream()
				.map(keywordRes -> {
					Keyword keyword = keywordMap.get(keywordRes.name());
					return UserKeyword.create(
						userId,
						keyword.getId(),
						keywordRes.percentage(),
						keywordRes.rank()
					);
				})
					.toList();

		userKeywordRepository.bulkUpsert(userId, userKeywordList);
	}
}
