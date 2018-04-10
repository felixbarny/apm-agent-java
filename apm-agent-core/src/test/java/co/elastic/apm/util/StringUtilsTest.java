package co.elastic.apm.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

class StringUtilsTest {

    @Test
    void getIndexOfNextWhitespaceChar() {
        assertSoftly(softly -> {
            softly.assertThat(StringUtils.getIndexOfNextWhitespaceChar("0", 0)).isEqualTo(1);
            softly.assertThat(StringUtils.getIndexOfNextWhitespaceChar("0 2", 0)).isEqualTo(1);
            softly.assertThat(StringUtils.getIndexOfNextWhitespaceChar("0 2", 1)).isEqualTo(1);
            softly.assertThat(StringUtils.getIndexOfNextWhitespaceChar("0 2", 2)).isEqualTo(3);
            softly.assertThat(StringUtils.getIndexOfNextWhitespaceChar("", 0)).isEqualTo(0);
            softly.assertThat(StringUtils.getIndexOfNextWhitespaceChar("", 2)).isEqualTo(-1);
        });
    }

    @Test
    void getIndexOfNextNonWhitespaceChar() {
        assertSoftly(softly -> {
            softly.assertThat(StringUtils.getIndexOfNextNonWhitespaceChar("0", 0)).isEqualTo(0);
            softly.assertThat(StringUtils.getIndexOfNextNonWhitespaceChar("0 2", 0)).isEqualTo(0);
            softly.assertThat(StringUtils.getIndexOfNextNonWhitespaceChar("0 2", 1)).isEqualTo(2);
            softly.assertThat(StringUtils.getIndexOfNextNonWhitespaceChar("0 2", 2)).isEqualTo(2);
            softly.assertThat(StringUtils.getIndexOfNextNonWhitespaceChar("", 2)).isEqualTo(-1);
        });
    }

    @Test
    void testIsWord() {
        assertSoftly(softly -> {
            softly.assertThat(StringUtils.isWord("foo", 0, 2)).isEqualTo(true);
            softly.assertThat(StringUtils.isWord("foo", 0, 1)).isEqualTo(false);
            softly.assertThat(StringUtils.isWord(" foo ", 1, 3)).isEqualTo(true);
            softly.assertThat(StringUtils.isWord("\nfoo\n", 1, 3)).isEqualTo(true);
            softly.assertThat(StringUtils.isWord("foo bar", 4, 6)).isEqualTo(true);
            softly.assertThat(StringUtils.isWord("foo\nbar", 4, 6)).isEqualTo(true);

            softly.assertThat(StringUtils.isWord("foo", -1, 1)).isEqualTo(false);
            softly.assertThat(StringUtils.isWord("foo", 0, 3)).isEqualTo(false);
            softly.assertThat(StringUtils.isWord("foo", 2, 0)).isEqualTo(false);
        });
    }

    @Test
    void getIndexOfWord() {
        assertSoftly(softly -> {
            softly.assertThat(StringUtils.getIndexOfWord("foo", "foo")).isEqualTo(0);
            softly.assertThat(StringUtils.getIndexOfWord("\nfoo", "foo")).isEqualTo(1);
            softly.assertThat(StringUtils.getIndexOfWord("foobar", "foo")).isEqualTo(-1);
            softly.assertThat(StringUtils.getIndexOfWord("foo foo", "foo")).isEqualTo(0);
            softly.assertThat(StringUtils.getIndexOfWord("fooo foo", "foo")).isEqualTo(5);
            softly.assertThat(StringUtils.getIndexOfWord("foofoo foo", "foo")).isEqualTo(7);
        });
    }
}
