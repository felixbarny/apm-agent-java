package co.elastic.apm.jdbc;

import co.elastic.apm.matcher.WildcardMatcher;
import co.elastic.apm.util.StringUtils;

import javax.annotation.Nullable;

class JdbcUtils {
    private static final WildcardMatcher CREATE_MATCHER = WildcardMatcher.valueOf("(?i)create*");
    private static final WildcardMatcher INSERT_MATCHER = WildcardMatcher.valueOf("(?i)insert*");
    private static final WildcardMatcher COMMIT_MATCHER = WildcardMatcher.valueOf("(?i)commit*");
    private static final WildcardMatcher SELECT_MATCHER = WildcardMatcher.valueOf("(?i)select*");
    private static final WildcardMatcher UPDATE_MATCHER = WildcardMatcher.valueOf("(?i)update*");
    private static final WildcardMatcher DELETE_MATCHER = WildcardMatcher.valueOf("(?i)delete*");
    private static final String CREATE = "CREATE";
    private static final String INSERT = "INSERT";
    private static final String COMMIT = "COMMIT";
    private static final String SELECT = "SELECT";
    private static final String UPDATE = "UPDATE";
    private static final String DELETE = "DELETE";

    static void setSpanName(String sql, StringBuilder spanName) {
        final String method = getMethod(sql);
        if (method == null) {
            return;
        }
        spanName.append(method);
        final String keyword;
        switch (method) {
            case CREATE:
                keyword = "TABLE";
                break;
            case INSERT:
                keyword = "INTO";
                break;
            case SELECT:
                keyword = "FROM";
                break;
            case UPDATE:
                keyword = UPDATE;
                break;
            case DELETE:
                keyword = "FROM";
                break;
            default:
                keyword = null;
        }
        if (keyword != null) {
            appendKeyword(spanName, keyword);
            appendTableName(sql, keyword, spanName);
        }
    }

    private static void appendKeyword(StringBuilder spanName, String keyword) {
        spanName.ensureCapacity(spanName.length() + keyword.length() + 2);
        spanName.append(' ');
        spanName.append(keyword);
        spanName.append(' ');
    }

    private static void appendTableName(String sql, String keyword, StringBuilder spanName) {
        final int indexOfTableName = getIndexOfTableName(sql, keyword);
        final int indexOfTableNameEnd = StringUtils.getIndexOfNextWhitespaceChar(sql, indexOfTableName);
        if (indexOfTableName != -1 && indexOfTableNameEnd != -1) {
            for (int i = indexOfTableName; i < indexOfTableNameEnd; i++) {
                spanName.append(sql.charAt(i));
            }
        }
    }

    private static int getIndexOfTableName(String sql, String keyword) {
        final int indexOfKeyword = StringUtils.getIndexOfWord(sql, keyword);
        if (indexOfKeyword != -1) {
            return StringUtils.getIndexOfNextNonWhitespaceChar(sql, indexOfKeyword + keyword.length());
        }
        return -1;
    }

    @Nullable
    static String getMethod(String sql) {
        if (sql == null) {
            return null;
        }
        // don't allocate objects for the common case
        if (CREATE_MATCHER.matches(sql)) {
            return CREATE;
        } else if (INSERT_MATCHER.matches(sql)) {
            return INSERT;
        } else if (COMMIT_MATCHER.matches(sql)) {
            return COMMIT;
        } else if (SELECT_MATCHER.matches(sql)) {
            return SELECT;
        } else if (UPDATE_MATCHER.matches(sql)) {
            return UPDATE;
        } else if (DELETE_MATCHER.matches(sql)) {
            return DELETE;
        }
        sql = sql.trim();
        final int indexOfWhitespace = sql.indexOf(' ');
        if (indexOfWhitespace > 0) {
            return sql.substring(0, indexOfWhitespace).toUpperCase();
        } else {
            // for example COMMIT
            return sql.toUpperCase();
        }
    }

    static String getDbVendor(String url) {
        // jdbc:h2:mem:test
        //     ^
        int indexOfJdbc = url.indexOf("jdbc:") + 5;
        if (indexOfJdbc != -1) {
            // h2:mem:test
            String urlWithoutJdbc = url.substring(indexOfJdbc);
            int indexOfColonAfterVendor = urlWithoutJdbc.indexOf(":");
            if (indexOfColonAfterVendor != -1) {
                // h2
                return urlWithoutJdbc.substring(0, indexOfColonAfterVendor);
            }
        }
        return "unknown";
    }
}
