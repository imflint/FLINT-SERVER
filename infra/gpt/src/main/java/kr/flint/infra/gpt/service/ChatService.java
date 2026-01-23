package kr.flint.infra.gpt.service;


import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.flint.infra.gpt.dto.GptKeywordDto;
import kr.flint.infra.gpt.dto.TasteWorkMetaDto;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatService {
	private final ChatClient chatClient;

	ObjectMapper objectMapper = new ObjectMapper();

	private final static String SYSTEM_PROMPT = """
You are a taste-profile extractor for a personalization system.

You will receive a list of works bookmarked by a user.
Each work contains metadata such as genres, keywords, title, and overview.

Important concepts:
- Taste keywords MUST be derived from the user's saved works.
- You must detect recurring and repeated taste patterns across all works.
- Keywords that appear more consistently or strongly across multiple works
  should be considered more dominant.

Your task:
1) Analyze ALL bookmarked works together at the user level.
2) Identify recurring taste patterns across the works.
3) Select EXACTLY 6 taste keywords ONLY from the predefined keyword list below.
4) These 6 keywords represent the user's overall taste profile.
5) Rank the 6 keywords from most dominant (rank 1) to least dominant (rank 6).
6) Assign a percentage to EACH keyword that represents its relative weight
   within the user's overall taste profile.

Diversity constraint (MANDATORY):
- Avoid selecting too many keywords from the same level (LV).
- When multiple keywords have similar dominance,
  prefer selecting keywords from DIFFERENT levels (LV1–LV5).
- Over-concentration in a single level should be avoided
  unless strongly justified by the works.

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
LV1. Genre :
- 모험, 코미디, 다큐멘터리, 드라마, 가족, 역사, 액션, 범죄, 호러, 미스터리, SF, 스릴러, 전쟁, 애니메이션, 판타지, 음악, 로맨스, 어린이

LV2. Mood / Emotion :
- 슬픈, 불안한, 어두운, 긴장되는, 잔혹한, 잔잔한, 설레는, 몽환적인, 유쾌한

LV3. Theme / Narrative Focus :
- 노년의 삶, 실화 기반, 사회문제, 정체성, 권력, 오피스, 종교, 복수, 추리, 성장, 브로맨스, 우정, 청춘, 생존

LV4. Setting / Era :
- 사극, 디스토피아, 근미래, 근대, 아포칼립스

LV5. Cultural Context :
- 한국, 북미, 유럽, 인도, 일본, 영국

--------------------------------------------------

Hard rules (ABSOLUTE):
1) You MUST choose only from the predefined keywords above.
2) Exact match only. No synonyms, paraphrasing, or translation.
3) Select EXACTLY 6 keywords.
4) Assign rank from 1 (most dominant) to 6 (least dominant).
5) Assign an integer percentage to EACH keyword.
6) The sum of all percentages MUST be exactly 100.
7) Output valid JSON only. No markdown, no comments.
8) Output keywords sorted by rank in ascending order (rank 1 first).

Fallback rule (MANDATORY):
- You MUST always output EXACTLY 6 keywords.
- If fewer than 6 keywords are strongly supported by evidence,
  you MUST still select additional weaker but plausible keywords
  from the predefined list.
- Weaker keywords MUST be ranked lower and assigned smaller percentages.
- Never output fewer or more than 6 keywords under any circumstances.

Output format (JSON ONLY):

{
  "tasteKeywords": [
    {
      "rank": number,
      "level": "LV1" | "LV2" | "LV3" | "LV4" | "LV5",
      "category": string,
      "keyword": string,
      "percent": number
    }
  ]
}""";

	public GptKeywordDto callGptForTaste(List<TasteWorkMetaDto> tasteWorkMetaDtoList) {
		try {
			String worksJson = objectMapper.writeValueAsString(tasteWorkMetaDtoList);

			String userPrompt = """
			Classify the user's bookmarked works into taste keywords.

			Input (JSON array of works):
			%s
			""".formatted(worksJson);

			return chatClient.prompt()
				.system(SYSTEM_PROMPT)
				.user(userPrompt)
				.call()
				.entity(GptKeywordDto.class);
		} catch (Exception e) {
			throw new RuntimeException("Error calling gpt for taste keywords", e);
		}
	}

}
