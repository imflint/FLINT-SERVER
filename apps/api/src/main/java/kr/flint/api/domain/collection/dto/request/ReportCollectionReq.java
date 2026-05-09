package kr.flint.api.domain.collection.dto.request;

import java.util.Set;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import kr.flint.collection.domain.ReportReason;
import kr.flint.collection.dto.ReportCollectionCommand;

@Schema(description = "컬렉션 신고 요청")
public record ReportCollectionReq(
	@ArraySchema(
		schema = @Schema(
			implementation = ReportReason.class,
			description = "신고 사유 (1개 이상, 복수 선택 가능). ABUSE=욕설·혐오 / OBSCENE=음란·선정 / SPAM=광고·스팸 / COPYRIGHT=저작권 침해 / OTHER=기타",
			example = "SPAM"
		),
		minItems = 1
	)
	@NotEmpty(message = "신고 사유는 1개 이상 선택해야 합니다")
	Set<ReportReason> reasons,

	@Schema(
		description = "기타 사유 상세 (사유에 OTHER가 포함된 경우 입력, 0~200자)",
		example = "이 컬렉션이 다른 사이트의 결제 페이지로 유도하고 있어요",
		maxLength = 200,
		nullable = true
	)
	@Nullable
	@Size(max = 200, message = "기타 사유는 최대 200자까지 입력 가능합니다")
	String otherDetail
) {
	public ReportCollectionCommand toCommand() {
		return new ReportCollectionCommand(reasons, otherDetail);
	}
}
