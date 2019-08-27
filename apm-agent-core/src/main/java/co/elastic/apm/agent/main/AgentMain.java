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
package co.elastic.apm.agent.main;

import co.elastic.apm.agent.bci.OsgiBootDelegationEnabler;
import net.bytebuddy.dynamic.loading.ClassInjector;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.instrument.Instrumentation;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * This class is loaded by the system classloader,
 * and adds the rest of the agent to the bootstrap class loader search.
 * <p>
 * This is required to instrument Java core classes like {@link Runnable} and to enable boot delegation in OSGi environments.
 * See {@link OsgiBootDelegationEnabler}.
 * </p>
 * <p>
 * Note that this relies on the fact that the system classloader is a parent-first classloader and first asks the bootstrap classloader
 * to resolve a class.
 * </p>
 */
public class AgentMain {

    /**
     * Allows the installation of this agent via the {@code javaagent} command line argument.
     *
     * @param agentArguments  The agent arguments.
     * @param instrumentation The instrumentation instance.
     */
    public static void premain(String agentArguments, Instrumentation instrumentation) {
        init(agentArguments, instrumentation);
    }

    /**
     * Allows the installation of this agent via the Attach API.
     *
     * @param agentArguments  The agent arguments.
     * @param instrumentation The instrumentation instance.
     */
    @SuppressWarnings("unused")
    public static void agentmain(String agentArguments, Instrumentation instrumentation) {
        init(agentArguments, instrumentation);
    }

    public synchronized static void init(String agentArguments, Instrumentation instrumentation) {
        if (Boolean.getBoolean("ElasticApm.attached")) {
            // agent is already attached; don't attach twice
            // don't fail as this is a valid case
            // for example, Spring Boot restarts the application in dev mode
            return;
        }
        try {
            FileSystems.getDefault();
            File bootstrapJar = getBootstrapJar();
            try {
                injectJar(bootstrapJar, ClassInjector.UsingUnsafe.ofBootLoader());
            } finally {
                if (!bootstrapJar.delete()) {
                    bootstrapJar.deleteOnExit();
                }
            }
            final File agentJarFile = getAgentJarFile();
            try (JarFile jarFile = new JarFile(agentJarFile)) {
                instrumentation.appendToBootstrapClassLoaderSearch(jarFile);
            }
            // invoking via reflection to make sure the class is not loaded by the system classloader,
            // but only from the bootstrap classloader
            Class.forName("co.elastic.apm.agent.bci.ElasticApmAgent", true, null)
                .getMethod("initialize", String.class, Instrumentation.class, File.class)
                .invoke(null, agentArguments, instrumentation, agentJarFile);
            System.setProperty("ElasticApm.attached", Boolean.TRUE.toString());
        } catch (Exception e) {
            System.err.println("Failed to start agent");
            e.printStackTrace();
        }
    }

    private static void injectJar(File bootstrapJar, ClassInjector classInjector) throws IOException, ClassNotFoundException {
        Map<String, byte[]> types = new HashMap<>();
        try (JarFile jarFile = new JarFile(bootstrapJar)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry jarEntry = entries.nextElement();
                if (jarEntry.getName().endsWith(".class")) {
                    try (InputStream inputStream = jarFile.getInputStream(jarEntry)) {
                        types.put(toClassName(jarEntry.getName()), getBytes(inputStream));
                    }
                }
            }
        }

        classInjector.injectRaw(types);
        // verify that the types have been successfully injected into the bootstrap CL
        for (String injectedType : types.keySet()) {
            Class.forName(injectedType, false, null);
        }
    }

    private static String toClassName(String resourceName) {
        return resourceName.replace('/', '.').replace(".class", "");
    }

    private static byte[] getBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        for (int n; (n = is.read(data, 0, data.length)) != -1; ) {
            buffer.write(data, 0, n);
        }
        return buffer.toByteArray();
    }

    private static File getBootstrapJar() {
        try (InputStream agentJar = AgentMain.class.getResourceAsStream("/apm-agent-bootstrap.jar")) {
            if (agentJar == null) {
                throw new IllegalStateException("Agent jar not found");
            }
            // don't delete on exit, because this the attaching application may terminate before the target application
            File tempAgentJar = File.createTempFile("apm-agent-bootstrap", ".jar");
            try (OutputStream out = new FileOutputStream(tempAgentJar)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = agentJar.read(buffer)) != -1) {
                    out.write(buffer, 0, length);
                }
            }
            return tempAgentJar;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static File getAgentJarFile() throws URISyntaxException {
        final File agentJar = new File(AgentMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (!agentJar.getName().endsWith(".jar")) {
            throw new IllegalStateException("Agent is not a jar file: " + agentJar);
        }
        return agentJar.getAbsoluteFile();
    }
}
