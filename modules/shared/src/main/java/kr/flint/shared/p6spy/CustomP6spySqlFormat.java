package kr.flint.shared.p6spy;

import com.p6spy.engine.logging.Category;
import com.p6spy.engine.spy.appender.MessageFormattingStrategy;
import org.hibernate.engine.jdbc.internal.FormatStyle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

@Component
public class CustomP6spySqlFormat implements MessageFormattingStrategy {

    @Value("${p6spy.filter.allow:}")
    private List<String> allowFilter = Collections.emptyList();

    @Value("${p6spy.filter.deny:}")
    private List<String> denyFilter = Collections.emptyList();

    @Override
    public String formatMessage(int connectionId, String now, long elapsed, String category, String prepared,
                                String sql, String url) {
        sql = formatSql(category, sql);
        if (sql == null || sql.trim().isEmpty()) {
            return "";
        }
        return sql + createStack(connectionId, elapsed);
    }

    private String formatSql(String category, String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return sql;
        }

        if (Category.STATEMENT.getName().equals(category)) {
            String tmpSql = sql.trim().toLowerCase(Locale.ROOT);
            if (tmpSql.startsWith("create") || tmpSql.startsWith("alter") || tmpSql.startsWith("comment")) {
                sql = FormatStyle.DDL.getFormatter().format(sql);
            } else {
                sql = FormatStyle.BASIC.getFormatter().format(sql);
            }
        }

        return sql;
    }

    private String createStack(int connectionId, long elapsed) {
        Queue<String> callStack = new LinkedList<>();
        StackTraceElement[] stackTrace = new Throwable().getStackTrace();

        for (StackTraceElement element : stackTrace) {
            String trace = element.toString();
            if (isValid(trace)) {
                callStack.offer(trace);
            }
        }

        StringBuilder sb = new StringBuilder();
        int order = 1;
        while (!callStack.isEmpty()) {
            sb.append("\n\t\t").append(order++).append(". ").append(callStack.poll());
        }

        return "\n\n\tConnection ID: " + connectionId
                + " | Execution Time: " + elapsed + " ms\n"
                + "\tCall Stack:" + sb + "\n"
                + "\n----------------------------------------------------------------------------";
    }

    private boolean isValid(String input) {
        if (allowFilter.isEmpty()) {
            return false;
        }
        boolean isAllowed = allowFilter.stream().anyMatch(input::startsWith);
        boolean isDenied = denyFilter.stream().anyMatch(input::startsWith);
        return isAllowed && !isDenied;
    }
}
