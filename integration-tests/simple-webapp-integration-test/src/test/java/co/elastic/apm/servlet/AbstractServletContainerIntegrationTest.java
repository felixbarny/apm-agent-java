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

import co.elastic.apm.agent.MockReporter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Test;
import org.mockserver.model.ClearType;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.SocatContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static co.elastic.apm.agent.report.IntakeV2ReportingEventHandler.INTAKE_V2_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;

/**
 * When you want to execute the test in the IDE, execute {@code mvn clean package} before.
 * This creates the {@code ROOT.war} file,
 * which is bound into the docker container.
 * <p>
 * Whenever you make changes to the application,
 * you have to rerun {@code mvn clean package}.
 * </p>
 * <p>
 * To debug, add a remote debugging configuration for port 5005 and set {@link #ENABLE_DEBUGGING} to {@code true}.
 * </p>
 */
public abstract class AbstractServletContainerIntegrationTest {
    static boolean ENABLE_DEBUGGING = false;
    static final String pathToJavaagent;
    private static final Logger logger = LoggerFactory.getLogger(AbstractServletContainerIntegrationTest.class);
    static MockServerContainer mockServerContainer = new MockServerContainer()
        .withNetworkAliases("apm-server")
        .withNetwork(Network.SHARED);
    static OkHttpClient httpClient;
    static JsonSchema schema;

    static {
        mockServerContainer.start();
        mockServerContainer.getClient().when(request(INTAKE_V2_URL)).respond(HttpResponse.response().withStatusCode(200));
        mockServerContainer.getClient().when(request("/")).respond(HttpResponse.response().withStatusCode(200));
        schema = JsonSchemaFactory.getInstance().getSchema(
            AbstractServletContainerIntegrationTest.class.getResourceAsStream("/schema/transactions/payload.json"));
        final HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(logger::info);
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        httpClient = new OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .readTimeout(ENABLE_DEBUGGING ? 0 : 10, TimeUnit.SECONDS)
            .build();
        pathToJavaagent = getPathToJavaagent();
        checkFilePresent(pathToJavaagent);
    }

    private final MockReporter mockReporter = new MockReporter();
    private final GenericContainer servletContainer;
    private final int webPort;
    @Nullable
    private GenericContainer<?> debugProxy;
    private final String expectedDefaultServiceName;

    protected AbstractServletContainerIntegrationTest(GenericContainer<?> servletContainer, String expectedDefaultServiceName, String deploymentPath, String containerName) {
        this(servletContainer, 8080, expectedDefaultServiceName, deploymentPath, containerName);
    }

    protected AbstractServletContainerIntegrationTest(GenericContainer<?> servletContainer, int webPort,
                                                      String expectedDefaultServiceName, String deploymentPath, String containerName) {
        this.servletContainer = servletContainer;
        this.webPort = webPort;
        if (ENABLE_DEBUGGING) {
            enableDebugging(servletContainer);
            this.debugProxy = createDebugProxy(servletContainer, 5005);
        }
        this.expectedDefaultServiceName = expectedDefaultServiceName;
        servletContainer.withEnv("ELASTIC_APM_SERVER_URL", "http://apm-server:1080")
            .withEnv("ELASTIC_APM_IGNORE_URLS", "/status*,/favicon.ico")
            .withEnv("ELASTIC_APM_REPORT_SYNC", "true")
            .withEnv("ELASTIC_APM_LOGGING_LOG_LEVEL", "DEBUG")
            .withLogConsumer(new StandardOutLogConsumer().withPrefix(containerName))
            .withExposedPorts(webPort)
            .withFileSystemBind(pathToJavaagent, "/elastic-apm-agent.jar");
        for (TestApp testApp: getTestApps()) {
            String pathToAppFile = testApp.getAppFilePath();
            checkFilePresent(pathToAppFile);
            servletContainer.withFileSystemBind(pathToAppFile, deploymentPath + "/" + testApp.getAppFileName());
        }
        this.servletContainer.start();
    }

    private static String getPathToJavaagent() {
        File agentBuildDir = new File("../../elastic-apm-agent/target/");
        FileFilter fileFilter = new WildcardFileFilter("elastic-apm-agent-*.jar");
        for (File file : agentBuildDir.listFiles(fileFilter)) {
            if (!file.getAbsolutePath().endsWith("javadoc.jar") && !file.getAbsolutePath().endsWith("sources.jar")) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }

    private static void checkFilePresent(String pathToWar) {
        final File warFile = new File(pathToWar);
        logger.info("Check file {}", warFile.getAbsolutePath());
        assertThat(warFile).exists();
        assertThat(warFile).isFile();
        assertThat(warFile.length()).isGreaterThan(0);
    }

    protected void enableDebugging(GenericContainer<?> servletContainer) {
    }

    // makes sure the debugging port is always 5005
    // if the port is not available, the test can still run
    @Nullable
    private GenericContainer<?> createDebugProxy(GenericContainer<?> servletContainer, final int debugPort) {
        try {
            final SocatContainer socatContainer = new SocatContainer() {{
                addFixedExposedPort(debugPort, debugPort);
            }}
                .withNetwork(Network.SHARED)
                .withTarget(debugPort, servletContainer.getNetworkAliases().get(0));
            socatContainer.start();
            return socatContainer;
        } catch (Exception e) {
            logger.warn("Starting debug proxy failed");
            return null;
        }
    }

    @After
    public final void stopServer() {
        servletContainer.getDockerClient()
            .stopContainerCmd(servletContainer.getContainerId())
            .exec();
        servletContainer.stop();
        if (debugProxy != null) {
            debugProxy.stop();
        }
    }

    protected Iterable<TestApp> getTestApps() {
        List<TestApp> testApps = new ArrayList<>();
        for (Class<? extends TestApp> testClass : getTestClasses()) {
            try {
                testApps.add(testClass.getConstructor().newInstance());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return testApps;
    }

    protected Iterable<Class<? extends TestApp>> getTestClasses() {
        return Collections.emptyList();
    }

    /**
     * NOTE: This test class should contain a single test method, otherwise multiple instances may coexist and cause port clash due to the
     * debug proxy
     */
    @Test
    public void testAllScenarios() throws Exception {
        for (TestApp testApp : getTestApps()) {
            waitFor(testApp.getStatusEndpoint());
            mockServerContainer.getClient().clear(HttpRequest.request(), ClearType.LOG);
            testApp.test(this);
        }
    }


    JsonNode assertTransactionReported(String pathToTest, int expectedResponseCode) {
        final List<JsonNode> reportedTransactions = getAllReported(500, this::getReportedTransactions, 1);
        JsonNode transaction = reportedTransactions.iterator().next();
        assertThat(transaction.get("context").get("request").get("url").get("pathname").textValue()).isEqualTo(pathToTest);
        assertThat(transaction.get("context").get("response").get("status_code").intValue()).isEqualTo(expectedResponseCode);
        return transaction;
    }

    void executeAndValidateRequest(String pathToTest, String expectedContent, Integer expectedResponseCode) throws IOException, InterruptedException {
        Response response = executeRequest(pathToTest);
        if (expectedResponseCode != null) {
            assertThat(response.code()).withFailMessage(response.toString() + getServerLogs()).isEqualTo(expectedResponseCode);
        }
        final ResponseBody responseBody = response.body();
        assertThat(responseBody).isNotNull();
        if (expectedContent != null) {
            assertThat(responseBody.string()).contains(expectedContent);
        }
    }

    Response executeRequest(String pathToTest) throws IOException {
        return httpClient.newCall(new Request.Builder()
                .get()
                .url(getBaseUrl() + pathToTest)
                .build())
                .execute();
    }

    @Nonnull
    List<JsonNode> getAllReported(int timeoutMs, Supplier<List<JsonNode>> supplier, int expected) {
        long start = System.currentTimeMillis();
        List<JsonNode> reportedTransactions;
        do {
            reportedTransactions = supplier.get();
        } while (reportedTransactions.size() != expected && System.currentTimeMillis() - start < timeoutMs);
        assertThat(reportedTransactions).hasSize(expected);
        return reportedTransactions;
    }

    @Nonnull
    List<JsonNode> assertSpansTransactionId(int timeoutMs, Supplier<List<JsonNode>> supplier, String transactionId) {
        long start = System.currentTimeMillis();
        List<JsonNode> reportedSpans;
        do {
            reportedSpans = supplier.get();
        } while (reportedSpans.size() == 0 && System.currentTimeMillis() - start < timeoutMs);
        assertThat(reportedSpans.size()).isGreaterThanOrEqualTo(1);
        for (JsonNode span : reportedSpans) {
            assertThat(span.get("transaction_id").textValue()).isEqualTo(transactionId);
        }
        return reportedSpans;
    }

    @Nonnull
    List<JsonNode>  assertErrorContent(int timeoutMs, Supplier<List<JsonNode>> supplier, String transactionId, String errorMessage) {
        long start = System.currentTimeMillis();
        List<JsonNode> reportedErrors;
        do {
            reportedErrors = supplier.get();
        } while (reportedErrors.size() == 0 && System.currentTimeMillis() - start < timeoutMs);
        assertThat(reportedErrors.size()).isEqualTo(1);
        for (JsonNode error : reportedErrors) {
            assertThat(error.get("transaction_id").textValue()).isEqualTo(transactionId);
            assertThat(error.get("exception").get("message").textValue()).contains(errorMessage);
            assertThat(error.get("exception").get("stacktrace").size()).isGreaterThanOrEqualTo(1);
        }
        return reportedErrors;
    }

    @Nonnull
    private String getServerLogs() throws IOException, InterruptedException {
        final String serverLogsPath = getServerLogsPath();
        if (serverLogsPath != null) {
            return "\nlogs:\n" +
                servletContainer.execInContainer("bash", "-c", "cat " + serverLogsPath).getStdout();
        } else {
            return "";
        }
    }

    @Nullable
    protected String getServerLogsPath() {
        return null;
    }

    @NotNull
    protected List<String> getPathsToTest() {
        return Arrays.asList("/index.jsp", "/servlet", "/async-dispatch-servlet", "/async-start-servlet");
    }

    @NotNull
    protected List<String> getPathsToTestErrors() {
        return Arrays.asList("/index.jsp", "/servlet", "/async-dispatch-servlet", "/async-start-servlet");
    }

    protected boolean isExpectedStacktrace(String path) {
        return !path.equals("/async-start-servlet");
    }

    private String getBaseUrl() {
        return "http://" + servletContainer.getContainerIpAddress() + ":" + servletContainer.getMappedPort(webPort);
    }

    List<JsonNode> getReportedTransactions() {
        final List<JsonNode> transactions = getEvents("transaction");
        transactions.forEach(mockReporter::verifyTransactionSchema);
        return transactions;
    }

    List<JsonNode> getReportedSpans() {
        final List<JsonNode> transactions = getEvents("span");
        transactions.forEach(mockReporter::verifySpanSchema);
        return transactions;
    }

    List<JsonNode> getReportedErrors() {
        final List<JsonNode> transactions = getEvents("error");
        transactions.forEach(mockReporter::verifyErrorSchema);
        return transactions;
    }

    private List<JsonNode> getEvents(String eventType) {
        try {
            final List<JsonNode> transactions = new ArrayList<>();
            final ObjectMapper objectMapper = new ObjectMapper();
            for (HttpRequest httpRequest : mockServerContainer.getClient().retrieveRecordedRequests(request(INTAKE_V2_URL))) {
                for (String ndJsonLine : httpRequest.getBodyAsString().split("\n")) {
                    final JsonNode ndJson = objectMapper.readTree(ndJsonLine);
                    if (ndJson.get(eventType) != null) {
                        transactions.add(ndJson.get(eventType));
                    }
                }
            }
            return transactions;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void validateMetadata() {
        try {
            final ObjectMapper objectMapper = new ObjectMapper();
            final JsonNode payload;
            payload = objectMapper
                .readTree(mockServerContainer.getClient()
                    .retrieveRecordedRequests(request(INTAKE_V2_URL))[0].getBodyAsString().split("\n")[0]);
            JsonNode metadata = payload.get("metadata");
            assertThat(metadata.get("service").get("name").textValue()).isEqualTo(expectedDefaultServiceName);
            JsonNode container = metadata.get("system").get("container");
            assertThat(container).isNotNull();
            assertThat(container.get("id").textValue()).isEqualTo(servletContainer.getContainerId());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void addSpans(List<JsonNode> spans, JsonNode payload) {
        final JsonNode jsonSpans = payload.get("spans");
        if (jsonSpans != null) {
            for (JsonNode jsonSpan : jsonSpans) {
                mockReporter.verifyTransactionSchema(jsonSpan);
                spans.add(jsonSpan);
            }
        }
    }

    public void waitFor(String path) {
        this.servletContainer.waitingFor(Wait.forHttp(path)
            .forPort(webPort)
            .forStatusCode(200)
            .withStartupTimeout(Duration.ofMinutes(ENABLE_DEBUGGING ? 1_000 : 5)));
    }
}
