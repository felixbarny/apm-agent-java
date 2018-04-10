package co.elastic.apm.jdbc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

class JdbcUtilsTest {

    @Test
    void getMethod() {
        assertSoftly(softly -> {
            softly.assertThat(JdbcUtils.getMethod("create table apm")).isEqualTo("CREATE");
            softly.assertThat(JdbcUtils.getMethod("INSERT INTO apm VALUES (1)")).isEqualTo("INSERT");
            softly.assertThat(JdbcUtils.getMethod("COMMIT")).isEqualTo("COMMIT");
            softly.assertThat(JdbcUtils.getMethod("SELECT FROM apm")).isEqualTo("SELECT");
            softly.assertThat(JdbcUtils.getMethod("UPDATE apm value=2")).isEqualTo("UPDATE");
            softly.assertThat(JdbcUtils.getMethod("DELETE FROM apm WHERE value=2")).isEqualTo("DELETE");
            softly.assertThat(JdbcUtils.getMethod("upsert INTO apm VALUES (1) WHERE value=1")).isEqualTo("UPSERT");
        });
    }
    @Test
    void testGetSpanName() {
        assertSoftly(softly -> {
            softly.assertThat(getSpanName("CREATE TABLE apm")).isEqualTo("CREATE TABLE apm");
            softly.assertThat(getSpanName("INSERT INTO apm VALUES (1)")).isEqualTo("INSERT INTO apm");
            softly.assertThat(getSpanName("COMMIT")).isEqualTo("COMMIT");
            softly.assertThat(getSpanName("SELECT FROM apm")).isEqualTo("SELECT FROM apm");
            softly.assertThat(getSpanName("UPDATE apm value=2")).isEqualTo("UPDATE apm");
            softly.assertThat(getSpanName("DELETE FROM apm WHERE value=2")).isEqualTo("DELETE FROM apm");
            softly.assertThat(getSpanName("UPSERT INTO apm VALUES (1) WHERE value=1")).isEqualTo("UPSERT");
            softly.assertThat(getSpanName("SELECT FirstName, LastName, \n" +
                "       OrderCount = (SELECT COUNT(O.Id) FROM [Order] O WHERE O.CustomerId = C.Id)\n" +
                "  FROM Customer C ")).isEqualTo("SELECT FROM Customer");
        });
    }

    private String getSpanName(String sql) {
        final StringBuilder spanName = new StringBuilder();
        JdbcUtils.setSpanName(sql, spanName);
        return spanName.toString();
    }
}
