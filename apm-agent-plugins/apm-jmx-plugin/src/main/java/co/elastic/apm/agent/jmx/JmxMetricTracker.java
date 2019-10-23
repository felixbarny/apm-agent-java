/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.jmx;

import co.elastic.apm.agent.context.AbstractLifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.metrics.DoubleSupplier;
import co.elastic.apm.agent.metrics.Labels;
import co.elastic.apm.agent.metrics.MetricRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.configuration.ConfigurationOption;

import javax.annotation.Nullable;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.MBeanServerDelegate;
import javax.management.MBeanServerNotification;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.relation.MBeanServerNotificationFilter;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class JmxMetricTracker extends AbstractLifecycleListener {

    private static final String JMX_PREFIX = "jvm.jmx.";
    private final Logger logger;
    private final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
    private final JmxConfiguration jmxConfiguration;
    private final MetricRegistry metricRegistry;
    private NotificationListener listener;

    public JmxMetricTracker(ElasticApmTracer tracer) {
        this(tracer, LoggerFactory.getLogger(JmxMetricTracker.class));
    }

    JmxMetricTracker(ElasticApmTracer tracer, Logger logger) {
        super(tracer);
        this.logger = logger;
        jmxConfiguration = tracer.getConfig(JmxConfiguration.class);
        metricRegistry = tracer.getMetricRegistry();

        registerMBeanNotificationListener();

        jmxConfiguration.getCaptureJmxMetrics().addChangeListener(new ConfigurationOption.ChangeListener<List<JmxMetric>>() {
            @Override
            public void onChange(ConfigurationOption<?> configurationOption, List<JmxMetric> oldValue, List<JmxMetric> newValue) {
                List<JmxMetricRegistration> oldRegistrations = compileJmxMetricRegistrations(oldValue);
                List<JmxMetricRegistration> newRegistrations = compileJmxMetricRegistrations(newValue);

                for (JmxMetricRegistration addedRegistration : removeAll(oldRegistrations, newRegistrations)) {
                    addedRegistration.register(server, metricRegistry);
                }
                for (JmxMetricRegistration deletedRegistration : removeAll(newRegistrations, oldRegistrations)) {
                    deletedRegistration.unregister(metricRegistry);
                }

            }
        });
        register(jmxConfiguration.getCaptureJmxMetrics().get());
    }

    private void registerMBeanNotificationListener() {
        MBeanServerNotificationFilter filter = new MBeanServerNotificationFilter();
        filter.enableAllObjectNames();
        filter.enableType(MBeanServerNotification.REGISTRATION_NOTIFICATION);
        listener = new NotificationListener() {
            @Override
            public void handleNotification(Notification notification, Object handback) {
                if (notification instanceof MBeanServerNotification) {
                    ObjectName mBeanName = ((MBeanServerNotification) notification).getMBeanName();
                    for (JmxMetric jmxMetric : jmxConfiguration.getCaptureJmxMetrics().get()) {
                        if (jmxMetric.getObjectName().apply(mBeanName)) {
                            logger.debug("MBean added at runtime: {}", jmxMetric.getObjectName());
                            register(Collections.singletonList(jmxMetric));
                        }
                    }
                }
            }
        };
        try {
            server.addNotificationListener(MBeanServerDelegate.DELEGATE_NAME, listener, filter, null);
        } catch (InstanceNotFoundException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private static <T> List<T> removeAll(List<T> removeFromThis, List<T> toRemove) {
        List<T> result = new ArrayList<T>(toRemove);
        result.removeAll(removeFromThis);
        return result;
    }

    private void register(List<JmxMetric> jmxMetrics) {
        for (JmxMetricRegistration registration : compileJmxMetricRegistrations(jmxMetrics)) {
            registration.register(server, metricRegistry);
        }
    }

    /**
     * A single {@link JmxMetric} can yield multiple {@link JmxMetricRegistration}s if the {@link JmxMetric} contains multiple {@link JmxMetric#attributes}
     */
    private List<JmxMetricRegistration> compileJmxMetricRegistrations(List<JmxMetric> jmxMetrics) {
        List<JmxMetricRegistration> registrations = new ArrayList<>();
        for (JmxMetric jmxMetric : jmxMetrics) {
            try {
                addJmxMetricRegistration(jmxMetric, registrations);
            } catch (Exception e) {
                logger.error("Failed to register JMX metric {}", jmxMetric.toString(), e);
            }
        }
        return registrations;
    }

    private void addJmxMetricRegistration(final JmxMetric jmxMetric, List<JmxMetricRegistration> registrations) throws JMException {
        Set<ObjectInstance> mbeans = server.queryMBeans(jmxMetric.getObjectName(), null);
        logger.debug("Found mbeans for object name {}", jmxMetric.getObjectName());
        for (ObjectInstance mbean : mbeans) {
            for (JmxMetric.Attribute attribute : jmxMetric.getAttributes()) {
                final ObjectName objectName = mbean.getObjectName();
                final Object value;
                try {
                    value = server.getAttribute(objectName, attribute.getJmxAttributeName());
                    if (value instanceof Number) {
                        logger.debug("Found number attribute {}={}", attribute.getJmxAttributeName(), value);
                        registrations.add(new JmxMetricRegistration(JMX_PREFIX + attribute.getMetricName(),
                            Labels.Mutable.of(objectName.getKeyPropertyList()),
                            attribute.getJmxAttributeName(),
                            null,
                            objectName));
                    } else if (value instanceof CompositeData) {
                        final CompositeData compositeValue = (CompositeData) value;
                        for (final String key : compositeValue.getCompositeType().keySet()) {
                            if (compositeValue.get(key) instanceof Number) {
                                logger.debug("Found composite number attribute {}.{}={}", attribute.getJmxAttributeName(), key, value);
                                registrations.add(new JmxMetricRegistration(JMX_PREFIX + attribute.getMetricName() + "." + key,
                                    Labels.Mutable.of(objectName.getKeyPropertyList()),
                                    attribute.getJmxAttributeName(),
                                    key,
                                    objectName));
                            } else {
                                logger.warn("Can't create metric '{}' because composite value '{}' is not a number: '{}'", jmxMetric, key, value);
                            }
                        }
                    } else {
                        logger.warn("Can't create metric '{}' because attribute '{}' is not a number: '{}'", jmxMetric, attribute.getJmxAttributeName(), value);
                    }
                } catch (AttributeNotFoundException e) {
                    logger.warn("Can't create metric '{}' because attribute '{}' could not be found", jmxMetric, attribute.getJmxAttributeName());
                }
            }
        }
    }

    static class JmxMetricRegistration {
        private static final Logger logger = LoggerFactory.getLogger(JmxMetricRegistration.class);
        private final String metricName;
        private final Labels.Immutable labels;
        private final String jmxAttribute;
        @Nullable
        private final String compositeDataKey;
        private final ObjectName objectName;

        private JmxMetricRegistration(String metricName, Labels labels, String jmxAttribute, @Nullable String compositeDataKey, ObjectName objectName) {
            this.metricName = metricName;
            this.labels = labels.immutableCopy();
            this.jmxAttribute = jmxAttribute;
            this.compositeDataKey = compositeDataKey;
            this.objectName = objectName;
        }


        void register(final MBeanServer server, final MetricRegistry metricRegistry) {
            logger.debug("Registering JMX metric {} {}.{} as metric_name: {} labels: {}", objectName, jmxAttribute, compositeDataKey, metricName, labels);
            metricRegistry.add(metricName, labels, new DoubleSupplier() {
                @Override
                public double get() {
                    try {
                        if (compositeDataKey == null) {
                            return ((Number) server.getAttribute(objectName, jmxAttribute)).doubleValue();
                        } else {
                            return ((Number) ((CompositeData) server.getAttribute(objectName, jmxAttribute)).get(compositeDataKey)).doubleValue();
                        }
                    } catch (Exception e) {
                        return Double.NaN;
                    }
                }
            });
        }

        void unregister(MetricRegistry metricRegistry) {
            logger.debug("Unregistering JMX metric {} {}.{} metric_name: {} labels: {}", objectName, jmxAttribute, compositeDataKey, metricName, labels);
            metricRegistry.removeGauge(metricName, labels);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            JmxMetricRegistration that = (JmxMetricRegistration) o;
            return metricName.equals(that.metricName) &&
                labels.equals(that.labels);
        }

        @Override
        public int hashCode() {
            return Objects.hash(metricName, labels);
        }
    }

    @Override
    public void stop() throws Exception {
        server.removeNotificationListener(MBeanServerDelegate.DELEGATE_NAME, listener);
    }
}