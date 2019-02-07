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
package co.elastic.apm.servlet;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

import javax.annotation.Nullable;
import java.util.Arrays;

@RunWith(Parameterized.class)
public class TomcatIT extends AbstractServletContainerIntegrationTest {

    public TomcatIT(final String tomcatVersion) {
        super(new GenericContainer<>("tomcat:" + tomcatVersion)
            .withNetwork(Network.SHARED)
            .withEnv("CATALINA_OPTS", "-javaagent:/elastic-apm-agent.jar"),
            "tomcat-application",
            "/usr/local/tomcat/webapps",
            "tomcat");
    }

    @Override
    protected void enableDebugging(GenericContainer<?> servletContainer) {
        servletContainer
            .withEnv("JPDA_ADDRESS", "5005")
            .withEnv("JPDA_TRANSPORT", "dt_socket");
    }

    @Parameterized.Parameters(name = "Tomcat {0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {"7-jre7-slim"},
            {"8.5.0-jre8"},
            {"8.5-jre8-slim"},
            {"9-jre9-slim"},
            {"9-jre10-slim"},
            {"9-jre11-slim"}
        });
    }

    @Nullable
    protected String getServerLogsPath() {
        return "/usr/local/tomcat/logs/*";
    }

    @Override
    protected Iterable<Class<? extends TestApp>> getTestClasses() {
        return Arrays.asList(ServletApiTestApp.class, JsfServletContainerTestApp.class);
    }
}
