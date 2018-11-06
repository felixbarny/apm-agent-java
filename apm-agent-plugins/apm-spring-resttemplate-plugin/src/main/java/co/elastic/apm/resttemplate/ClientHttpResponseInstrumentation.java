package co.elastic.apm.resttemplate;

import co.elastic.apm.bci.ElasticApmInstrumentation;
import co.elastic.apm.impl.transaction.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class ClientHttpResponseInstrumentation extends ElasticApmInstrumentation {

    @Advice.OnMethodExit
    public static void onClose() {
        if (tracer != null) {
            final Span currentSpan = tracer.currentSpan();
            if (currentSpan != null) {
                currentSpan.deactivate().end();
            }
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return nameStartsWith("org.springframework")
            .and(not(isInterface()))
            // only traverse the object hierarchy if the class declares the method to instrument at all
            .and(declaresMethod(getMethodMatcher()))
            .and(hasSuperType(named("org.springframework.http.client.ClientHttpResponse")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("close").and(takesArguments(0));
    }


    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "spring-resttemplate");
    }
}
