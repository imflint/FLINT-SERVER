package kr.flint.api.domain.collection.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class AddContentReqTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	@DisplayName("customImageUrls 요청 필드는 customImages로 역직렬화된다")
	void customImageUrlsAlias() throws Exception {
		String json = """
			{
			  "contentId": 1,
			  "isSpoiler": false,
			  "reason": "좋아서 추천해요",
			  "customImageUrls": ["custom-a.jpg", "custom-b.jpg"]
			}
			""";

		AddContentReq request = objectMapper.readValue(json, AddContentReq.class);

		assertThat(request.customImages()).containsExactly("custom-a.jpg", "custom-b.jpg");
	}
}
