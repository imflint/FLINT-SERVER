package kr.flint.api.global.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

public class MySqlFullTextFunctionContributor implements FunctionContributor {
	private static final String FUNCTION_NAME = "match_against_boolean";
	private static final String FUNCTION_PATTERN = "match (?1) against (?2 in boolean mode) > 0";

	@Override
	public void contributeFunctions(FunctionContributions functionContributions) {
		var booleanType = functionContributions.getTypeConfiguration()
			.getBasicTypeRegistry()
			.resolve(StandardBasicTypes.BOOLEAN);
		functionContributions.getFunctionRegistry()
			.registerPattern(FUNCTION_NAME, FUNCTION_PATTERN, booleanType);
	}
}
