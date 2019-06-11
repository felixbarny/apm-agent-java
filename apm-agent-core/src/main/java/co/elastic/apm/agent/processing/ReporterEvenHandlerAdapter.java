package co.elastic.apm.agent.processing;

import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.report.Reporter;

import java.util.concurrent.Future;

public class ReporterEvenHandlerAdapter implements Reporter {

    private final EventProcessor eventProcessor;

    public ReporterEvenHandlerAdapter(EventProcessor eventProcessor) {
        this.eventProcessor = eventProcessor;
    }

    @Override
    public void report(Transaction transaction) {
        eventProcessor.report(transaction);
    }

    @Override
    public void report(Span span) {
        eventProcessor.report(span);
    }

    @Override
    public long getDropped() {
        return 0;
    }

    @Override
    public long getReported() {
        return 0;
    }

    @Override
    public Future<Void> flush() {
        return null;
    }

    @Override
    public void close() {
    }

    @Override
    public void report(ErrorCapture error) {
        eventProcessor.report(error);
    }

    @Override
    public void reportMetrics(MetricRegistry metricRegistry) {

    }
}
