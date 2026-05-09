package kr.flint.collection.domain;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import kr.flint.shared.domain.BaseTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "collection_reports")
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CollectionReport extends BaseTime {

	@Column(nullable = false)
	private Long reporterId;

	@Column(nullable = false)
	private Long collectionId;

	@ElementCollection(targetClass = ReportReason.class, fetch = FetchType.EAGER)
	@CollectionTable(
		name = "collection_report_reasons",
		joinColumns = @JoinColumn(name = "report_id", nullable = false)
	)
	@Enumerated(EnumType.STRING)
	@Column(name = "reason", nullable = false, length = 32)
	private Set<ReportReason> reasons;

	// 사유에 OTHER가 포함될 때 작성한 자유 입력 (0~200자, nullable). 길이 검증은 호출 측에서 보장.
	@Column(name = "other_detail", length = 200)
	private String otherDetail;

	public static CollectionReport create(
		Long reporterId,
		Long collectionId,
		Set<ReportReason> reasons,
		String otherDetail
	) {
		Set<ReportReason> safeReasons = (reasons == null || reasons.isEmpty())
			? EnumSet.noneOf(ReportReason.class)
			: new HashSet<>(reasons);
		return CollectionReport.builder()
			.reporterId(reporterId)
			.collectionId(collectionId)
			.reasons(safeReasons)
			.otherDetail(otherDetail)
			.build();
	}
}
