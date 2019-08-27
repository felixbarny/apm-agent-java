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
package co.elastic.apm.agent.dispatcher;

import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.dynamic.loading.InjectionClassLoader;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * <p>
 * This {@link java.lang.ClassLoader} is capable of loading classes from multiple parents. This class loader
 * implicitly defines the bootstrap class loader to be its direct parent as it is required for all class loaders.
 * This can be useful when creating a type that inherits a super type and interfaces that are defined by different,
 * non-compatible class loaders.
 * </p>
 * <p>
 * <b>Note</b>: Instances of this class loader can have the same class loader as its parent multiple times,
 * either directly or indirectly by multiple parents sharing a common parent class loader. By definition,
 * this implies that the bootstrap class loader is {@code #(direct parents) + 1} times a parent of this class loader.
 * For the {@link java.lang.ClassLoader#getResources(java.lang.String)} method, this means that this class loader
 * might return the same url multiple times by representing the same class loader multiple times.
 * </p>
 * <p>
 * <b>Important</b>: This class loader does not support the location of packages from its multiple parents. This breaks
 * package equality when loading classes by either loading them directly via this class loader (e.g. by subclassing) or
 * by loading classes with child class loaders of this class loader.
 * </p>
 */
public class MultipleParentChildFirstClassLoader extends InjectionClassLoader {

    /**
     * The parents of this class loader in their application order.
     */
    private final List<? extends ClassLoader> parents;

    /**
     * Creates a new class loader with multiple parents.
     *
     * @param parents The parents of this class loader in their application order. This list must not contain {@code null},
     *                i.e. the bootstrap class loader which is an implicit parent of any class loader.
     */
    public MultipleParentChildFirstClassLoader(List<? extends ClassLoader> parents) {
        this(ClassLoadingStrategy.BOOTSTRAP_LOADER, parents);
    }

    /**
     * Creates a new class loader with multiple parents.
     *
     * @param parent  An explicit parent in compliance with the class loader API. This explicit parent should only be set if
     *                the current platform does not allow creating a class loader that extends the bootstrap loader.
     * @param parents The parents of this class loader in their application order. This list must not contain {@code null},
     *                i.e. the bootstrap class loader which is an implicit parent of any class loader.
     */
    public MultipleParentChildFirstClassLoader(ClassLoader parent, List<? extends ClassLoader> parents) {
        this(parent, parents, true);
    }

    /**
     * Creates a new class loader with multiple parents.
     *
     * @param parent  An explicit parent in compliance with the class loader API. This explicit parent should only be set if
     *                the current platform does not allow creating a class loader that extends the bootstrap loader.
     * @param parents The parents of this class loader in their application order. This list must not contain {@code null},
     *                i.e. the bootstrap class loader which is an implicit parent of any class loader.
     * @param sealed  {@code true} if the class loader is sealed for injection of additional classes.
     */
    public MultipleParentChildFirstClassLoader(ClassLoader parent, List<? extends ClassLoader> parents, boolean sealed) {
        super(parent, sealed);
        this.parents = parents;
    }

    /**
     * {@inheritDoc}
     */
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        try {
            return super.loadClass(name, resolve);
        } catch (ClassNotFoundException e) {
            for (ClassLoader parent : parents) {
                try {
                    Class<?> type = parent.loadClass(name);
                    if (resolve) {
                        resolveClass(type);
                    }
                    return type;
                } catch (ClassNotFoundException ignored) {
                    /* try next class loader */
                }
            }
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     */
    public URL getResource(String name) {
        URL resource = super.getResource(name);
        if (resource != null) {
            return resource;
        }
        for (ClassLoader parent : parents) {
            URL url = parent.getResource(name);
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public Enumeration<URL> getResources(String name) throws IOException {
        List<Enumeration<URL>> enumerations = new ArrayList<Enumeration<URL>>(parents.size() + 1);
        enumerations.add(super.getResources(name));
        for (ClassLoader parent : parents) {
            enumerations.add(parent.getResources(name));
        }
        return new CompoundEnumeration(enumerations);
    }

    @Override
    protected Map<String, Class<?>> doDefineClasses(Map<String, byte[]> typeDefinitions) {
        Map<String, Class<?>> types = new HashMap<String, Class<?>>();
        for (Map.Entry<String, byte[]> entry : typeDefinitions.entrySet()) {
            types.put(entry.getKey(), defineClass(entry.getKey(), entry.getValue(), 0, entry.getValue().length));
        }
        return types;
    }

    /**
     * A compound URL enumeration.
     */
    protected static class CompoundEnumeration implements Enumeration<URL> {

        /**
         * Indicates the first index of a list.
         */
        private static final int FIRST = 0;

        /**
         * The remaining lists of enumerations.
         */
        private final List<Enumeration<URL>> enumerations;

        /**
         * The currently represented enumeration or {@code null} if no such enumeration is currently selected.
         */
        @Nullable
        private Enumeration<URL> currentEnumeration;

        /**
         * Creates a compound enumeration.
         *
         * @param enumerations The enumerations to represent.
         */
        protected CompoundEnumeration(List<Enumeration<URL>> enumerations) {
            this.enumerations = enumerations;
        }

        /**
         * {@inheritDoc}
         */
        public boolean hasMoreElements() {
            if (currentEnumeration != null && currentEnumeration.hasMoreElements()) {
                return true;
            } else if (!enumerations.isEmpty()) {
                currentEnumeration = enumerations.remove(FIRST);
                return hasMoreElements();
            } else {
                return false;
            }
        }

        /**
         * {@inheritDoc}
         */
        public URL nextElement() {
            if (hasMoreElements()) {
                return currentEnumeration.nextElement();
            } else {
                throw new NoSuchElementException();
            }
        }
    }
}

