package kr.flint.collection.domain;

public enum ReportReason {
	ABUSE("욕설·혐오 표현이 포함된 콘텐츠"),
	OBSCENE("음란하거나 선정적인 콘텐츠"),
	SPAM("광고·홍보 또는 스팸성 콘텐츠"),
	COPYRIGHT("저작권을 침해한 콘텐츠"),
	OTHER("기타");

	private final String label;

	ReportReason(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
