package kr.flint.taste.repository;

import java.util.List;

public interface CollectionKeywordRepositoryCustom {

    void deleteByCollectionIdAndKeywordIdIn(Long collectionId, List<Long> keywordIds);

    void deleteByCollectionId(Long collectionId);
}
