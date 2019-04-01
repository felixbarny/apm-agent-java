/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.jfr;

import co.elastic.apm.agent.configuration.converter.TimeDuration;
import co.elastic.apm.agent.configuration.converter.TimeDurationValueConverter;
import co.elastic.apm.agent.configuration.validation.RangeValidator;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;

public class ProfilerConfiguration extends ConfigurationOptionProvider {

    private static final String PROFILER_CATEGORY = "Profiler";

    private final ConfigurationOption<TimeDuration> profilerDelay = TimeDurationValueConverter.durationOption("s")
        .key("profiling_delay")
        .configurationCategory(PROFILER_CATEGORY)
        .buildWithDefault(TimeDuration.of("53s"));
    private final ConfigurationOption<TimeDuration> profilingDuration = TimeDurationValueConverter.durationOption("s")
        .key("profiling_duration")
        .configurationCategory(PROFILER_CATEGORY)
        .buildWithDefault(TimeDuration.of("8s"));
    private final ConfigurationOption<TimeDuration> profilingSamplingInterval = TimeDurationValueConverter.durationOption("ms")
        .key("profiling_sampling_interval")
        .configurationCategory(PROFILER_CATEGORY)
        .addValidator(RangeValidator.min(TimeDuration.of("10ms")))
        .buildWithDefault(TimeDuration.of("20ms"));
    private final ConfigurationOption<TimeDuration> profilingMinTransactionActivation = TimeDurationValueConverter.durationOption("ms")
        .key("profiling_min_transaction_activation")
        .configurationCategory(PROFILER_CATEGORY)
        .buildWithDefault(TimeDuration.of("10ms"));

    public TimeDuration getProfilingDelay() {
        return profilerDelay.get();
    }

    public TimeDuration getProfilingDuration() {
        return profilingDuration.get();
    }

    public TimeDuration getProfilingSamplingInterval() {
        return profilingSamplingInterval.get();
    }

    public TimeDuration getProfilingMinTransactionActivation() {
        return profilingMinTransactionActivation.get();
    }
}
