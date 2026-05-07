package kr.flint.api.domain.home.dto.projection;

public record CollectionContentImageDto(
	Long collectionId,
	String customImage,
	String poster
) {
	public String image() {
		return customImage != null && !customImage.isBlank() ? customImage : poster;
	}
}
