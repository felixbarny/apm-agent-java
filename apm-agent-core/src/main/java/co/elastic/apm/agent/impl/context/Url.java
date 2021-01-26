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
package co.elastic.apm.agent.impl.context;

import co.elastic.apm.agent.objectpool.Recyclable;

import javax.annotation.Nullable;
import java.net.URL;


/**
 * A complete Url, with scheme, host and path.
 */
public class Url implements Recyclable {

    /**
     * The full, possibly agent-assembled URL of the request, e.g https://example.com:443/search?q=elasticsearch#top.
     */
    private final StringBuilder full = new StringBuilder();
    /**
     * The protocol of the request, e.g. 'https:'.
     */
    @Nullable
    private String protocol;
    /**
     * The hostname of the request, e.g. 'example.com'.
     */
    @Nullable
    private String hostname;
    /**
     * The port of the request, e.g. '443'
     */
    private final StringBuilder port = new StringBuilder();
    /**
     * The path of the request, e.g. '/search'
     */
    @Nullable
    private String pathname;
    /**
     * The search describes the query string of the request. It is expected to have values delimited by ampersands.
     */
    @Nullable
    private String search;

    /**
     * The protocol of the request, e.g. 'https:'.
     */
    @Nullable
    public String getProtocol() {
        return protocol;
    }

    /**
     * The protocol of the request, e.g. 'https:'.
     */
    public Url withProtocol(@Nullable String protocol) {
        this.protocol = protocol;
        return this;
    }

    /**
     * The full, possibly agent-assembled URL of the request, e.g https://example.com:443/search?q=elasticsearch#top.
     */
    public StringBuilder getFull() {
        return full;
    }

    public Url appendToFull(CharSequence charSequence) {
        full.append(charSequence);
        return this;
    }

    /**
     * The hostname of the request, e.g. 'example.com'.
     */
    @Nullable
    public String getHostname() {
        return hostname;
    }

    /**
     * The hostname of the request, e.g. 'example.com'.
     */
    public Url withHostname(@Nullable String hostname) {
        this.hostname = hostname;
        return this;
    }

    /**
     * The port of the request, e.g. '443'
     */
    public StringBuilder getPort() {
        return port;
    }

    public int getPortAsInt() {
        if (port.length() > 0) {
            try {
                return Integer.parseInt(port.toString());
            } catch (NumberFormatException ignore) {
            }
        }
        return -1;
    }

    /**
     * The port of the request, e.g. '443'
     */
    public Url withPort(int port) {
        this.port.append(port);
        return this;
    }

    /**
     * The path of the request, e.g. '/search'
     */
    @Nullable
    public String getPathname() {
        return pathname;
    }

    /**
     * The path of the request, e.g. '/search'
     */
    public Url withPathname(@Nullable String pathname) {
        this.pathname = pathname;
        return this;
    }

    /**
     * The search describes the query string of the request. It is expected to have values delimited by ampersands.
     */
    @Nullable
    public String getSearch() {
        return search;
    }

    /**
     * The search describes the query string of the request. It is expected to have values delimited by ampersands.
     */
    public Url withSearch(@Nullable String search) {
        this.search = search;
        return this;
    }

    public void fillFromFullUrl(URL url) {
        if (protocol == null) protocol = url.getProtocol();
        if (hostname == null) hostname = url.getHost();
        if (pathname == null) pathname = url.getPath();
        if (search == null) search = url.getQuery();
        int port = url.getPort();
        port = port > 0 ? port : url.getDefaultPort();
        if (port > 0) {
            withPort(port);
        }
    }

    public void fillFullUrl() {
        if (full.length() > 0 || hostname == null) {
            return;
        }
        full.append(protocol != null ? protocol : "http")
            .append("://")
            .append(hostname)
            .append(port.length() > 0 ? ":" : "")
            .append(port.length() > 0 ? port : "")
            .append(pathname != null ? pathname : "")
            .append(search != null ? "?" : "")
            .append(search != null ? search : "");
    }

    @Override
    public void resetState() {
        protocol = null;
        full.setLength(0);
        hostname = null;
        port.setLength(0);
        pathname = null;
        search = null;
    }

    public void copyFrom(Url other) {
        this.protocol = other.protocol;
        this.full.setLength(0);
        this.full.append(other.full);
        this.hostname = other.hostname;
        this.port.setLength(0);
        this.port.append(other.port);
        this.pathname = other.pathname;
        this.search = other.search;
    }

    public boolean hasContent() {
        return protocol != null ||
            full.length() > 0 ||
            hostname != null ||
            port.length() > 0 ||
            pathname != null ||
            search != null;
    }
}
