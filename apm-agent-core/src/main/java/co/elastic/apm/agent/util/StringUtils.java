package co.elastic.apm.agent.util;

import javax.annotation.Nullable;

public final class StringUtils {

    private StringUtils() {
    }

    public static boolean equals(@Nullable StringBuilder s1, @Nullable StringBuilder s2) {
        if (s1 == null || s2 == null) {
            return s1 == s2;
        }

        int length = s1.length();
        if (s2.length() != length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (s1.charAt(i) != s2.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
