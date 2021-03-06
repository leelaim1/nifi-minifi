/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.nifi.minifi.bootstrap.configuration.notifiers.util;

import com.squareup.okhttp.Headers;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.minifi.bootstrap.configuration.notifiers.RestChangeNotifier;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public abstract class TestRestChangeNotifierCommon {

    private static String testString = "This is a test string.";

    public static OkHttpClient client;
    public static RestChangeNotifier restChangeNotifier;
    public static final MediaType MEDIA_TYPE_MARKDOWN  = MediaType.parse("text/x-markdown; charset=utf-8");
    public static String url;
    public static MockChangeListener mockChangeListener = new MockChangeListener();

    @Test
    public void testGet() throws Exception {
        assertEquals(1, restChangeNotifier.getChangeListeners().size());

        Request request = new Request.Builder()
                .url(url)
                .build();

        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

        Headers responseHeaders = response.headers();
        for (int i = 0; i < responseHeaders.size(); i++) {
            System.out.println(responseHeaders.name(i) + ": " + responseHeaders.value(i));
        }

        assertEquals(RestChangeNotifier.GET_TEXT, response.body().string());
    }

    @Test
    public void testFileUpload() throws Exception {
        assertEquals(1, restChangeNotifier.getChangeListeners().size());
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(MEDIA_TYPE_MARKDOWN, testString))
                .addHeader("charset","UTF-8")
                .build();

        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

        Headers responseHeaders = response.headers();
        for (int i = 0; i < responseHeaders.size(); i++) {
            System.out.println(responseHeaders.name(i) + ": " + responseHeaders.value(i));
        }

        assertEquals("The result of notifying listeners:\nMockChangeListener successfully handled the configuration change\n", response.body().string());

        assertEquals(testString, StringUtils.trim(mockChangeListener.getConfFile()));
    }
}
