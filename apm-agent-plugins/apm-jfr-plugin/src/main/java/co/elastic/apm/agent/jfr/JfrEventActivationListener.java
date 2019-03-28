package co.elastic.apm.agent.jfr;

import co.elastic.apm.agent.impl.ActivationListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import co.elastic.apm.agent.objectpool.Recyclable;
import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.StackTrace;

public class JfrEventActivationListener implements ActivationListener {

    private static ThreadLocal<ActiveTransactionEvent> eventStack = new ThreadLocal<>() {
        @Override
        protected ActiveTransactionEvent initialValue() {
            return new ActiveTransactionEvent();
        }
    };
    private ElasticApmTracer tracer;

    @Override
    public void init(ElasticApmTracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public void beforeActivate(TraceContextHolder<?> context) {
        final ActiveTransactionEvent event = eventStack.get();
        if (event.isEnabled() && context.isSampled() && tracer.getActive() == null) {
            event.transactionId = context.getTraceContext().getTransactionId().readLong(0);
            event.begin();
        }
    }

    @Override
    public void afterDeactivate() {
        final ActiveTransactionEvent event = eventStack.get();
        if (event.transactionId != 0 && tracer.getActive() == null) {
            event.end();
            event.commit();
            event.resetState();
        }
    }

    @Category("Tracing")
    @Label("Activation")
    @Description("Marks the time a particular transaction has been active on a thread")
    @StackTrace(false)
    public static class ActiveTransactionEvent extends Event implements Recyclable {
        @Label("TransactionID")
        long transactionId;

        @Override
        public void resetState() {
            transactionId = 0;
        }
    }

}
