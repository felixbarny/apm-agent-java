package co.elastic.apm.agent.processing;

import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.report.IntakeV2ReportingEventHandler;
import co.elastic.apm.agent.report.ReportingEvent;
import co.elastic.apm.agent.report.ReportingEventHandler;

import java.util.concurrent.FutureTask;

/**
 * Bridges the {@link EventProcessor} interface to the existing {@link IntakeV2ReportingEventHandler}
 */
public class ApmServerReporterProcessor implements EventProcessor {

    private final ReportingEventHandler reporter;
    private final ReportingEvent event = new ReportingEvent();

    public ApmServerReporterProcessor(ReportingEventHandler reporter) {
        this.reporter = reporter;
    }

    @Override
    public void report(Transaction transaction) {
        event.setTransaction(transaction);
        reporter.onEvent(event, -1, true);
    }

    @Override
    public void report(Span span) {
        event.setSpan(span);
        reporter.onEvent(event, -1, true);
    }

    @Override
    public void report(ErrorCapture errorCapture) {
        event.setError(errorCapture);
        reporter.onEvent(event, -1, true);
    }

    @Override
    public void onClose() {
        event.shutdownEvent();
        reporter.onEvent(event, -1, true);
    }

    @Override
    public void reportMetrics(MetricRegistry metricRegistry) {
        event.reportMetrics(metricRegistry);
        reporter.onEvent(event, -1, true);
    }

    @Override
    public void flush(FutureTask<Void> flush) {
        event.setFlushEvent();
        reporter.onEvent(event, -1, true);
        flush.run();
    }
}
