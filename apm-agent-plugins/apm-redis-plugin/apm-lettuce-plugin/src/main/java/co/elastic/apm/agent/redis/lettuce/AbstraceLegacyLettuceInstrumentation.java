package co.elastic.apm.agent.redis.lettuce;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import io.lettuce.core.protocol.RedisCommand;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Arrays;
import java.util.Collection;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;

public abstract class AbstraceLegacyLettuceInstrumentation extends ElasticApmInstrumentation {

    /**
     * We don't support Lettuce <= 3.3, as the {@link RedisCommand#getType()} method is missing
     */
    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        // avoid instrumenting Lettuce <= 3.3 by requiring a type that has been introduced in 3.4
        return classLoaderCanLoadClass("com.lambdaworks.redis.event.EventBus");
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("redis", "lettuce");
    }
}
