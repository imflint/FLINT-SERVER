package kr.flint.shared.p6spy;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.hibernate.engine.jdbc.internal.FormatStyle;

import com.p6spy.engine.logging.Category;
import com.p6spy.engine.spy.appender.MessageFormattingStrategy;

public class CustomP6spySqlFormat implements MessageFormattingStrategy {

	public static final String SLOW_QUERY_THRESHOLD_PROPERTY = "flint.p6spy.slow-query-threshold-ms";
	public static final String STACK_ALLOW_PREFIXES_PROPERTY = "flint.p6spy.stack.allow-prefixes";
	public static final String STACK_DENY_PREFIXES_PROPERTY = "flint.p6spy.stack.deny-prefixes";
	public static final String STACK_MAX_DEPTH_PROPERTY = "flint.p6spy.stack.max-depth";

	private static final String DEFAULT_ALLOW_PREFIXES = "kr.flint";
	private static final String DEFAULT_DENY_PREFIXES = "kr.flint.shared.p6spy,kr.flint.shared.config.P6spyConfig";
	private static final int DEFAULT_STACK_MAX_DEPTH = 5;

	@Override
	public String formatMessage(
		int connectionId,
		String now,
		long elapsed,
		String category,
		String prepared,
		String sql,
		String url
	) {
		String formattedSql = formatSql(category, sql);
		if (formattedSql == null || formattedSql.isBlank()) {
			return "";
		}

		StringBuilder message = new StringBuilder()
			.append(logLabel())
			.append(" 분류=").append(category)
			.append(" 커넥션ID=").append(connectionId)
			.append(" 실행시간ms=").append(elapsed)
			.append('\n')
			.append(formattedSql);

		String stack = createStack();
		if (!stack.isBlank()) {
			message.append("\n호출 스택:").append(stack);
		}

		return message.append("\n----------------------------------------------------------------------------").toString();
	}

	private String logLabel() {
		long threshold = Long.getLong(SLOW_QUERY_THRESHOLD_PROPERTY, 0);
		return threshold > 0 ? "[느린쿼리]" : "[SQL]";
	}

	private String formatSql(String category, String sql) {
		if (sql == null || sql.isBlank()) {
			return sql;
		}

		if (Category.STATEMENT.getName().equals(category) || Category.BATCH.getName().equals(category)) {
			String tmpSql = sql.trim().toLowerCase(Locale.ROOT);
			if (tmpSql.startsWith("create") || tmpSql.startsWith("alter") || tmpSql.startsWith("comment")) {
				return FormatStyle.DDL.getFormatter().format(sql);
			}
			return FormatStyle.BASIC.getFormatter().format(sql);
		}

		return sql;
	}

	private String createStack() {
		List<String> allowPrefixes = readPrefixes(STACK_ALLOW_PREFIXES_PROPERTY, DEFAULT_ALLOW_PREFIXES);
		List<String> denyPrefixes = readPrefixes(STACK_DENY_PREFIXES_PROPERTY, DEFAULT_DENY_PREFIXES);
		int maxDepth = Integer.getInteger(STACK_MAX_DEPTH_PROPERTY, DEFAULT_STACK_MAX_DEPTH);
		if (maxDepth <= 0 || allowPrefixes.isEmpty()) {
			return "";
		}

		StringBuilder sb = new StringBuilder();
		int depth = 0;
		for (StackTraceElement element : new Throwable().getStackTrace()) {
			String trace = element.toString();
			if (!isValid(trace, allowPrefixes, denyPrefixes)) {
				continue;
			}

			sb.append("\n\t").append(++depth).append(". ").append(trace);
			if (depth >= maxDepth) {
				break;
			}
		}

		return sb.toString();
	}

	private List<String> readPrefixes(String key, String defaultValue) {
		return Arrays.stream(System.getProperty(key, defaultValue).split(","))
			.map(String::trim)
			.filter(value -> !value.isBlank())
			.toList();
	}

	private boolean isValid(String input, List<String> allowPrefixes, List<String> denyPrefixes) {
		boolean isAllowed = allowPrefixes.stream().anyMatch(input::startsWith);
		boolean isDenied = denyPrefixes.stream().anyMatch(input::startsWith);
		return isAllowed && !isDenied;
	}
}
