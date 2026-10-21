package kr.flint.infra.gpt.service;


import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import kr.flint.infra.gpt.dto.GptKeywordDto;
import kr.flint.infra.gpt.dto.TasteKeywordLevelDto;
import kr.flint.infra.gpt.dto.TasteWorkMetaDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {
	private final ChatClient chatClient;
	private final ObjectMapper objectMapper;

	// gpt-4o-mini 가격 (USD per 1M tokens)
	private static final double INPUT_PRICE_PER_MILLION = 0.15;
	private static final double OUTPUT_PRICE_PER_MILLION = 0.60;

	/**
	 * 최종 노출은 6개지만, 서버가 레벨 분산을 적용해 6개를 고를 여유를 두기 위해 후보를 더 받는다.
	 */
	private static final int CANDIDATE_KEYWORD_COUNT = 12;

	private final static String SYSTEM_PROMPT_TEMPLATE = """
You are a taste-profile extractor for a personalization system.

You will receive a list of works bookmarked by a user.
Each work contains metadata such as genres, keywords, title, and overview.

Important concepts:
- Taste keywords MUST be derived from the user's saved works.
- You must detect recurring and repeated taste patterns across all works.
- Dominance is based on consistency, but diversity of dimensions is also important.

Your task:
1) Analyze ALL bookmarked works together at the user level.
2) Identify recurring taste patterns across the works.
3) Select EXACTLY %d taste keywords ONLY from the predefined keyword list below.
4) Rank them from most dominant (rank 1) to least dominant (rank %d).
5) Assign an integer percentage to EACH keyword representing its relative weight.

Ranking is the only signal the server keeps from you, so order matters more than
the exact percentages: the server picks the final 6 keywords from your ranked list.

⚠️ Diversity constraint (MANDATORY):
- The top 6 of your ranked list MUST cover at least 3 DIFFERENT levels.
- Do not fill the top ranks with keywords from a single level.
- Overuse of Genre (LV1) keywords is discouraged unless strongly unavoidable.
- If multiple keywords are similarly dominant, prefer the one that introduces a NEW level.

How to determine ranking and percentage:
- Ranking is based on overall dominance and consistency across works.
- Percentages represent relative dominance within the selected keywords.
- Percentages must be normalized so that the total equals exactly 100.

Genre inference rule:
- If a work does NOT contain explicit genre metadata,
  you MUST infer its genre ONLY by analyzing the work's description (overview).
- Genre inference is allowed ONLY to map to the predefined genre list.

Predefined taste keywords (ONLY selectable options):

--------------------------------------------------
%s
--------------------------------------------------

Hard rules (ABSOLUTE):
1) You MUST choose only from the predefined keywords above.
2) The "keyword" value MUST be copied character-for-character from the list.
   No synonyms, no paraphrasing, no translation, no added or removed spaces.
3) Select EXACTLY %d keywords, all distinct.
4) Assign rank from 1 (most dominant) to %d (least dominant), each rank used once.
5) Assign an integer percentage to EACH keyword.
6) The sum of all percentages MUST be exactly 100.
7) Output valid JSON only. No markdown, no comments.
8) Output keywords sorted by rank in ascending order (rank 1 first).

Fallback rule (MANDATORY):
- You MUST always output EXACTLY %d keywords.
- If fewer are strongly supported by evidence, you MUST still select additional
  weaker but plausible keywords from the predefined list.
- Weaker keywords MUST be ranked lower and assigned smaller percentages.

Output format (JSON ONLY):

{
  "tasteKeywords": [
    {
      "rank": number,
      "keyword": string,
      "percent": number
    }
  ]
}""";

	public GptKeywordDto callGptForTaste(
		List<TasteWorkMetaDto> tasteWorkMetaDtoList,
		List<TasteKeywordLevelDto> keywordCatalog
	) {
		try {
			String worksJson = objectMapper.writeValueAsString(tasteWorkMetaDtoList);

			String userPrompt = """
			Classify the user's bookmarked works into taste keywords.

			Input (JSON array of works):
			%s
			""".formatted(worksJson);

			ChatResponse response = chatClient.prompt()
				.system(buildSystemPrompt(keywordCatalog))
				.user(userPrompt)
				.call()
				.chatResponse();

			logTokenUsage(response);

			String content = response.getResult().getOutput().getText();
			GptKeywordDto result = objectMapper.readValue(content, GptKeywordDto.class);
			logUnknownKeywords(result, keywordCatalog);
			return result;
		} catch (Exception e) {
			throw new RuntimeException("Error calling gpt for taste keywords", e);
		}
	}

	private String buildSystemPrompt(List<TasteKeywordLevelDto> keywordCatalog) {
		return SYSTEM_PROMPT_TEMPLATE.formatted(
			CANDIDATE_KEYWORD_COUNT,
			CANDIDATE_KEYWORD_COUNT,
			renderKeywordCatalog(keywordCatalog),
			CANDIDATE_KEYWORD_COUNT,
			CANDIDATE_KEYWORD_COUNT,
			CANDIDATE_KEYWORD_COUNT
		);
	}

	private String renderKeywordCatalog(List<TasteKeywordLevelDto> keywordCatalog) {
		if (keywordCatalog == null || keywordCatalog.isEmpty()) {
			throw new IllegalArgumentException("취향 키워드 후보 목록이 비어 있습니다.");
		}
		return keywordCatalog.stream()
			.map(level -> "%s. %s :%n- %s".formatted(
				level.level(),
				level.label(),
				String.join(", ", level.keywords())
			))
			.collect(Collectors.joining(System.lineSeparator() + System.lineSeparator()));
	}

	// 프롬프트를 벗어난 응답은 서버가 별칭으로 복구하므로 버리지 않고 기록만 한다
	private void logUnknownKeywords(GptKeywordDto result, List<TasteKeywordLevelDto> keywordCatalog) {
		if (result == null || result.tasteKeywords() == null) {
			return;
		}
		Set<String> allowed = keywordCatalog.stream()
			.flatMap(level -> level.keywords().stream())
			.collect(Collectors.toSet());
		List<String> unknown = result.tasteKeywords().stream()
			.map(GptKeywordDto.TasteKeywords::keyword)
			.filter(keyword -> !allowed.contains(keyword))
			.toList();
		if (!unknown.isEmpty()) {
			log.warn("[GPT] 후보 목록을 벗어난 키워드 응답: {}", unknown);
		}
	}

	private void logTokenUsage(ChatResponse response) {
		Usage usage = response.getMetadata().getUsage();
		int inputTokens = usage.getPromptTokens();
		int outputTokens = usage.getCompletionTokens();
		int totalTokens = usage.getTotalTokens();

		double inputCost = (inputTokens / 1_000_000.0) * INPUT_PRICE_PER_MILLION;
		double outputCost = (outputTokens / 1_000_000.0) * OUTPUT_PRICE_PER_MILLION;
		double totalCost = inputCost + outputCost;

		log.info("[GPT 사용량] tokens: input={}, output={}, total={} | cost: ${} (input=${}, output=${})",
			inputTokens,
			outputTokens,
			totalTokens,
			String.format("%.6f", totalCost),
			String.format("%.6f", inputCost),
			String.format("%.6f", outputCost));
	}

}
