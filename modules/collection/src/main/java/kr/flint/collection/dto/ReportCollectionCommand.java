package kr.flint.collection.dto;

import java.util.Set;

import kr.flint.collection.domain.ReportReason;

public record ReportCollectionCommand(
	Set<ReportReason> reasons,
	String otherDetail
) {
}
