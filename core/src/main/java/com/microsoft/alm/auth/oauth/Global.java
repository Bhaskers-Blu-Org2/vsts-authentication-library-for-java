// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.alm.auth.oauth;

import com.microsoft.alm.auth.HttpClientFactory;

public final class Global {

    private static HttpClientFactory httpClientFactory = new HttpClientFactory();
    private static String userAgent = null;

    /**
     * Creates the correct user-agent string for HTTP calls.
     *
     * @return The `user-agent` string for "auth-library".
     * Example from Windows version:
     * java-auth-library (Microsoft Windows NT 6.2.9200.0; Win32NT; x64) CLR/4.0.30319 auth-library/1.0.0
     * Example from Java version:
     * java-auth-library (Windows 8.1; 6.3; amd64) Java HotSpot(TM) 64-Bit Server VM/1.8.0_51-b16 auth-library/1.0
     * .0-SNAPSHOT
     */
    public static String getUserAgent() {
        if (userAgent == null) {
            // http://stackoverflow.com/a/6773868/
            final String version = Global.class.getPackage().getImplementationVersion();
            userAgent = String.format("java-auth-library (%1$s; %2$s; %3$s) %4$s/%5$s auth-library/%6$s",
                    System.getProperty("os.name"), // "Windows Server 2012 R2", "Mac OS X", "Linux"
                    System.getProperty("os.version"), // "6.3", "10.10.5", "3.19.0-28-generic"
                    System.getProperty("os.arch"), // "amd64", "x86_64", "amd64"
                    System.getProperty("java.vm.name"), // "Java HotSpot(TM) 64-Bit Server VM", "OpenJDK 64-Bit Server VM"
                    System.getProperty("java.runtime.version"), // "1.8.0_60-b27", "1.7.0_71-b14", "1.7.0_79-b14"
                    version);
        }
        return userAgent;
    }

    public static HttpClientFactory getHttpClientFactory() {
        return httpClientFactory;
    }

    public static void setHttpClientFactory(final HttpClientFactory _httpClientFactory) {
        httpClientFactory = _httpClientFactory;
    }
}
