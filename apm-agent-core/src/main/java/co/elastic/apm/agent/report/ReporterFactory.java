/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * #L%
 */
package co.elastic.apm.agent.report;

import co.elastic.apm.agent.impl.MetaData;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.report.processor.Processor;
import co.elastic.apm.agent.report.processor.ProcessorEventHandler;
import co.elastic.apm.agent.report.queue.ByteRingBuffer;
import co.elastic.apm.agent.report.queue.ByteRingBufferProcessor;
import co.elastic.apm.agent.report.queue.DrainableQueue;
import co.elastic.apm.agent.report.queue.DrainableQueueProcessor;
import co.elastic.apm.agent.report.queue.MutableRunnableThread;
import co.elastic.apm.agent.report.queue.ProcessorLifecycleCallback;
import co.elastic.apm.agent.report.queue.SpscOffHeapByteBuffer;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import co.elastic.apm.agent.util.DependencyInjectingServiceLoader;
import co.elastic.apm.agent.util.ThreadUtils;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpscArrayQueue;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

public class ReporterFactory {

    private static final boolean USE_NEW_JCTOOLS_REPORTER = false;

    public Reporter createReporter(ConfigurationRegistry configurationRegistry, ApmServerClient apmServerClient, MetaData metaData) {
        if (!USE_NEW_JCTOOLS_REPORTER) {
            return createDisruptorReporter(configurationRegistry, apmServerClient, metaData);
        } else {
            return createJCToolsReporter(configurationRegistry, apmServerClient, metaData);
        }
    }

    public Reporter createDisruptorReporter(ConfigurationRegistry configurationRegistry, ApmServerClient apmServerClient, MetaData metaData) {
        final ReporterConfiguration reporterConfiguration = configurationRegistry.getConfig(ReporterConfiguration.class);
        final ReportingEventHandler reportingEventHandler = getReportingEventHandler(configurationRegistry,
            reporterConfiguration, metaData, apmServerClient);
        return new ApmServerReporter(true, reporterConfiguration, reportingEventHandler);
    }

    @Nonnull
    private ReportingEventHandler getReportingEventHandler(ConfigurationRegistry configurationRegistry,
                                                           ReporterConfiguration reporterConfiguration, MetaData metaData, ApmServerClient apmServerClient) {

        final DslJsonSerializer payloadSerializer = new DslJsonSerializer(configurationRegistry.getConfig(StacktraceConfiguration.class), apmServerClient);
        final ProcessorEventHandler processorEventHandler = ProcessorEventHandler.loadProcessors(configurationRegistry);
        return new IntakeV2ReportingEventHandler(reporterConfiguration, processorEventHandler, payloadSerializer, metaData, apmServerClient);
    }

    public JCToolsReporter createJCToolsReporter(ConfigurationRegistry configurationRegistry, ApmServerClient apmServerClient, MetaData metaData) {
        final ReporterConfiguration reporterConfiguration = configurationRegistry.getConfig(ReporterConfiguration.class);
        final DslJsonSerializer payloadSerializer = new DslJsonSerializer(configurationRegistry.getConfig(StacktraceConfiguration.class), apmServerClient);
        ApmServerReportingEventConsumer apmSender = new ApmServerReportingEventConsumer(reporterConfiguration, payloadSerializer, metaData, apmServerClient);
        ByteRingBufferProcessor eventSender = new ByteRingBufferProcessor(new MessagePassingQueue.Supplier<ByteRingBuffer>() {
            @Override
            public ByteRingBuffer get() {
                return new SpscOffHeapByteBuffer(4 * 1024 * 1024);
            }
        },
        new MutableRunnableThread(ThreadUtils.addElasticApmThreadPrefix("event-sender")),
            apmSender,
            apmSender,
            TimeUnit.MILLISECONDS.toNanos(100),
            100,
            1000);
        return new JCToolsReporter(new DrainableQueueProcessor<>(
            new MessagePassingQueue.Supplier<DrainableQueue<Object, Object>>() {
                @Override
                public DrainableQueue<Object, Object> get() {
                    return DrainableQueue.MessagePassingQueueAdapter.of(new MpscArrayQueue<>(reporterConfiguration.getMaxQueueSize()));
                }
            },
            new MutableRunnableThread(ThreadUtils.addElasticApmThreadPrefix("event-serializer")),
            new SerializingApmEventConsumer(payloadSerializer, eventSender, DependencyInjectingServiceLoader.load(Processor.class, configurationRegistry)),
            ProcessorLifecycleCallback.Noop.INSTANCE,
            TimeUnit.MILLISECONDS.toNanos(100),
            100,
            1000), eventSender);
    }

}
