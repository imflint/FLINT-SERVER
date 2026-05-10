package kr.flint.content.domain;

public enum MediaType {
	MOVIE,
	TV;

	public static MediaType fromTmdb(String tmdbMediaType) {
		if (tmdbMediaType == null) {
			return MOVIE;
		}
		return switch (tmdbMediaType.toLowerCase()) {
			case "tv" -> TV;
			default -> MOVIE;
		};
	}
}
