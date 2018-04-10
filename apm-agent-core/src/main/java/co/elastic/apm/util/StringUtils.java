package co.elastic.apm.util;

public class StringUtils {

    private StringUtils() {
        // don't instantiate
    }


    public static int getIndexOfNextNonWhitespaceChar(String s, int offset) {
        if (!isInBounds(s, offset)) {
            return -1;
        }
        for (int i = offset; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * A whitespace character is defined as the first char matching {@link Character#isWhitespace(char)} or as the end of the string.
     *
     * @param s      the string a whitespace should be found in
     * @param offset the offset where to look for the whitespace
     * @return
     */
    public static int getIndexOfNextWhitespaceChar(String s, int offset) {
        if (!isInBounds(s, offset)) {
            return -1;
        }
        for (int i = offset; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) {
                return i;
            }
        }
        return s.length();
    }

    /**
     * Gets the index of the word in a string
     * <p>
     * A word is defined as a substring surrounded by a {@link Character#isWhitespace(char) whitespace},
     * where a string boundary also counts as a whitespace.
     * </p>
     *
     * @param s            the string a word should be found in
     * @param wordInString a substring of the string
     * @return the start index of the word in the string, or {@code -1} if there is no such word in the string
     */
    public static int getIndexOfWord(String s, String wordInString) {
        // TODO indexOfIgnoreCase
        int offset = -1;
        do {
            offset = s.indexOf(wordInString, ++offset);
        } while (offset != -1 && !isWord(s, offset, offset + wordInString.length()));
        return offset;
    }

    private static boolean isInBounds(String s, int index) {
        return index >= 0 && index <= s.length();
    }

    static boolean isWord(String s, int wordStart, int wordEnd) {
        if (!isInBounds(s, wordStart) || !isInBounds(s, wordEnd) || wordStart > wordEnd) {
            return false;
        }
        boolean precedingWhitespace;
        boolean followingWhitespace;
        precedingWhitespace = wordStart == 0 || Character.isWhitespace(s.charAt(wordStart - 1));
        followingWhitespace = wordEnd == s.length() || Character.isWhitespace(s.charAt(wordEnd));
        return precedingWhitespace && followingWhitespace;
    }
}
