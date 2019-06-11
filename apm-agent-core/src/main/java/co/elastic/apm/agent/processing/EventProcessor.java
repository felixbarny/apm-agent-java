package co.elastic.apm.agent.processing;

import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.metrics.MetricRegistry;

import java.util.concurrent.FutureTask;

public interface EventProcessor {

//    interface Event {
//        enum Type {
//            TRANSACTION, SPAN, ERROR, METRIC, FLUSH, SHUTDOWN
//        }
//
//        Type getType();
//    }
//
//    void onEvent(Event e);

    void report(Transaction transaction);

    void report(Span span);

    void report(ErrorCapture errorCapture);

    void onClose();

    void reportMetrics(MetricRegistry event);

    void flush(FutureTask<Void> event);
}
