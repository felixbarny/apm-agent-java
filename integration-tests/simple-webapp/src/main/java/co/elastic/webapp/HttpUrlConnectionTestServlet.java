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
package co.elastic.webapp;

import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class HttpUrlConnectionTestServlet extends HttpServlet {

    private final CloseableHttpAsyncClient client;

    public HttpUrlConnectionTestServlet() {
        client = HttpAsyncClients.createDefault();
        client.start();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        try {

        final CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();

        RequestConfig requestConfig = RequestConfig.custom()
            .setCircularRedirectsAllowed(true)
            .build();
        HttpClientContext httpClientContext = HttpClientContext.create();
        httpClientContext.setRequestConfig(requestConfig);
        final URL url = new URL("http",  req.getLocalAddr(), req.getLocalPort(),req.getServletContext().getContextPath() + "/hello-world.jsp");

        client.execute(new HttpGet(url.toURI()), httpClientContext, new FutureCallback<HttpResponse>() {
            @Override
            public void completed(HttpResponse result) {
                responseFuture.complete(result);
            }

            @Override
            public void failed(Exception ex) {
                responseFuture.completeExceptionally(ex);
            }

            @Override
            public void cancelled() {
                responseFuture.cancel(true);
            }
        });

            HttpResponse httpResponse = responseFuture.get();
            resp.setStatus(httpResponse.getStatusLine().getStatusCode());
            resp.setContentType(httpResponse.getFirstHeader("Content-Type").getValue());
            final ServletOutputStream outputStream = resp.getOutputStream();
            httpResponse.getEntity().writeTo(outputStream);
            outputStream.close();

        } catch (Exception e) {
            throw new ServletException(e);
        }


        /*final URL url = new URL("http",  req.getLocalAddr(), req.getLocalPort(),req.getServletContext().getContextPath() + "/hello-world.jsp");
        final HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.connect();
        final InputStream inputStream = urlConnection.getInputStream();
        resp.setStatus(urlConnection.getResponseCode());
        resp.setContentType(urlConnection.getHeaderField("Content-Type"));
        final byte[] buffer = new byte[1024];
        final ServletOutputStream outputStream = resp.getOutputStream();
        for (int limit = inputStream.read(buffer); limit != -1; limit = inputStream.read(buffer)) {
            outputStream.write(buffer, 0, limit);
        }
        inputStream.close();
        urlConnection.disconnect();
        outputStream.close();*/
    }

    @Override
    public void destroy() {
        try {
            client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
