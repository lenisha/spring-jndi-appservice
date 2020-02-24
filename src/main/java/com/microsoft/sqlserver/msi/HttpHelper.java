package com.microsoft.sqlserver.msi;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;



class HttpHelper {



    static String executeHttpGet(final String url,
                                 final Map<String, String> headers,
                                 final SSLSocketFactory sslSocketFactory) throws Exception {

        final HttpURLConnection conn = HttpHelper.openConnection(url, sslSocketFactory);
        return executeGetRequest( headers, conn);
    }

    static HttpURLConnection openConnection(final URL finalURL,
                                             final SSLSocketFactory sslSocketFactory) throws IOException {
        HttpURLConnection connection =  (HttpURLConnection)finalURL.openConnection();

        if (sslSocketFactory != null && finalURL.getHost().startsWith("https")) {
            ((HttpsURLConnection) connection).setSSLSocketFactory(sslSocketFactory);
        }

        return connection;
    }

    static HttpURLConnection openConnection(final String url,
                                             final SSLSocketFactory sslSocketFactory) throws IOException {
        return openConnection(new URL(url),  sslSocketFactory);
    }



    static HttpURLConnection configureAdditionalHeaders(final HttpURLConnection conn,
                                                         final Map<String, String> headers) throws IOException {
        if (headers != null) {
            for (final Map.Entry<String, String> entry : headers.entrySet()) {
                conn.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
        return conn;
    }

    private static String executeGetRequest(Map<String, String> headers,
                                            HttpURLConnection conn) throws IOException {
        configureAdditionalHeaders(conn, headers);
        return getResponse(headers, conn);
    }

    private static String getResponse( Map<String, String> headers,
                                       HttpURLConnection conn) throws IOException {
        String response = readResponseFromConnection(conn);
        return response;
    }

    static String inputStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    static String readResponseFromConnection(final HttpURLConnection conn) throws IOException {
        InputStream is = null;
        try {
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                String msg = "Server returned HTTP response code: " + conn.getResponseCode() + " for URL : " +
                        conn.getURL();
                is = conn.getErrorStream();
                if (is != null) {
                    msg = msg + ", Error details : " + inputStreamToString(is);
                }
                throw new IOException(msg);
            }

            is = conn.getInputStream();
            return inputStreamToString(is);
        }
        finally {
            if(is != null){
                is.close();
            }
        }
    }

}