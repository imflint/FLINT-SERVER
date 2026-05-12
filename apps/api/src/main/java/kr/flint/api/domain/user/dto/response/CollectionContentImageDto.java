package kr.flint.api.domain.user.dto.response;


public record CollectionContentImageDto(
	Long collectionId,
	String customImage,
	String poster
) {
	public String image() {
		return customImage != null && !customImage.isBlank() ? customImage : poster;
	}
}
