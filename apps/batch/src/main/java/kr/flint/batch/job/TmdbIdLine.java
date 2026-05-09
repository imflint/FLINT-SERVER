package kr.flint.batch.job;

// TMDB daily export 한 줄: { "id": 123, "original_title": "...", "popularity": 1.0, "adult": false, "video": false }
public record TmdbIdLine(Long id, String originalTitle, Double popularity, Boolean adult) {
	public static TmdbIdLine fromMap(java.util.Map<String, Object> map) {
		Object idObj = map.get("id");
		if (idObj == null) {
			return null;
		}
		Long id = ((Number) idObj).longValue();
		String title = map.get("original_title") != null
			? (String) map.get("original_title")
			: (String) map.getOrDefault("original_name", null);
		Double popularity = map.get("popularity") instanceof Number n ? n.doubleValue() : null;
		Boolean adult = map.get("adult") instanceof Boolean b ? b : null;
		return new TmdbIdLine(id, title, popularity, adult);
	}
}
