package kr.flint.api.domain.user.dto.response;

public record CollectionContentImageProjectionImpl(
	Long collectionId,
	String poster
) implements CollectionContentImageProjection {

	@Override
	public Long getCollectionId() { return collectionId; }

	@Override
	public String getPoster() { return poster; }
}
