package kr.flint.api.domain.content.dto;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class GetContentDetailResTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	@DisplayName("OTT 목록은 ottList 필드로 직렬화")
	void serializeOttList() throws Exception {
		GetContentDetailRes response = new GetContentDetailRes(
			1L,
			"작품",
			"poster.jpg",
			2026,
			10,
			List.of(new GetContentDetailRes.GetOttSimpleRes("Netflix", "netflix.svg"))
		);

		JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

		assertThat(json.has("ottList")).isTrue();
		assertThat(json.has("getOttSimpleList")).isFalse();
		assertThat(json.get("ottList").get(0).get("ottName").asText()).isEqualTo("Netflix");
	}

	@Test
	@DisplayName("프로필 콘텐츠 목록의 OTT 목록도 ottList 필드로 직렬화")
	void serializeProfileContentOttList() throws Exception {
		GetContentListRes.Content response = new GetContentListRes.Content(
			1L,
			"작품",
			"poster.jpg",
			2026,
			10,
			true,
			List.of(new GetContentDetailRes.GetOttSimpleRes("Netflix", "netflix.svg"))
		);

		JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

		assertThat(json.has("ottList")).isTrue();
		assertThat(json.has("getOttSimpleList")).isFalse();
		assertThat(json.get("ottList").get(0).get("ottName").asText()).isEqualTo("Netflix");
	}
}
