package co.elastic.apm.agent.redis.lettuce;

import co.elastic.apm.agent.TestClassWithDependencyRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;

@RunWith(Parameterized.class)
public class Lettuce5VersionsIT {

    private final TestClassWithDependencyRunner runner;

    public Lettuce5VersionsIT(List<String> dependencies) throws Exception {
        System.setProperty("io.lettuce.core.kqueue", "false");
        runner = new TestClassWithDependencyRunner(dependencies, Lettuce5InstrumentationTest.class);
    }

    @Parameterized.Parameters(name= "{0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][] {
            { List.of("io.lettuce:lettuce-core:5.2.1.RELEASE", "io.netty:netty-all:4.1.43.Final") },
            { List.of("io.lettuce:lettuce-core:5.1.8.RELEASE", "io.netty:netty-all:4.1.38.Final") },
            { List.of("io.lettuce:lettuce-core:5.0.5.RELEASE", "io.netty:netty-all:4.1.28.Final") },
        });
    }

    @Test
    public void testLettuce() {
        runner.run();
    }
}
