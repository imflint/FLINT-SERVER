package kr.flint.collection.event;

import java.util.List;

public record CollectionReportedEvent(
	Long reportId,
	Long reporterId,
	Long collectionId,
	List<String> reasonLabels,
	String otherDetail
) {
}
