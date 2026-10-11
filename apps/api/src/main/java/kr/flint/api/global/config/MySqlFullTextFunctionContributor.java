package kr.flint.api.global.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

public class MySqlFullTextFunctionContributor implements FunctionContributor {
	private static final String FUNCTION_NAME = "match_against_boolean";
	private static final String FUNCTION_PATTERN = "match (?1) against (?2 in boolean mode) > 0";
	private static final String SCORE_FUNCTION_NAME = "match_against_score";
	private static final String SCORE_FUNCTION_PATTERN = "match (?1) against (?2 in natural language mode)";

	@Override
	public void contributeFunctions(FunctionContributions functionContributions) {
		var booleanType = functionContributions.getTypeConfiguration()
			.getBasicTypeRegistry()
			.resolve(StandardBasicTypes.BOOLEAN);
		functionContributions.getFunctionRegistry()
			.registerPattern(FUNCTION_NAME, FUNCTION_PATTERN, booleanType);
		functionContributions.getFunctionRegistry().registerPattern("match_against_words_boolean",
			"match (?1, ?2) against (?3 in boolean mode) > 0", booleanType);
		functionContributions.getFunctionRegistry().registerPattern("title_contains_binary",
			"?1 like ?2 collate utf8mb4_0900_bin", booleanType);

		var doubleType = functionContributions.getTypeConfiguration()
			.getBasicTypeRegistry()
			.resolve(StandardBasicTypes.DOUBLE);
		functionContributions.getFunctionRegistry()
			.registerPattern(SCORE_FUNCTION_NAME, SCORE_FUNCTION_PATTERN, doubleType);
	}
}
