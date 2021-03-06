package com.danilov.supermanga.core.http;

import android.content.res.Resources;
import android.support.annotation.Nullable;
import android.util.Log;

import com.danilov.supermanga.core.util.IoUtils;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.InputStream;
import java.util.HashMap;

/**
 * Created by Semyon Danilov on 16.05.2014.
 */
public class HttpStreamReader {
    public static final String TAG = "HttpStreamReader";

    private final DefaultHttpClient mHttpClient;
    private final Resources mResources;
    private final HashMap<String, String> mIfModifiedMap = new HashMap<>();

    public HttpStreamReader(DefaultHttpClient httpClient, Resources resources) {
        this.mHttpClient = httpClient;
        this.mResources = resources;
    }

    public HttpStreamModel fromUri(String uri) throws HttpRequestException {
        return this.fromUri(uri, null, null);
    }

    public HttpStreamModel fromUri(String uri, @Nullable final RequestPreprocessor preprocessor, Header[] customHeaders) throws HttpRequestException {
        return this.fromUri(uri, preprocessor, customHeaders, null, null);
    }

    public HttpStreamModel fromUri(String uri, @Nullable final RequestPreprocessor preprocessor, Header[] customHeaders, IProgressChangeListener listener, ICancelled task) throws HttpRequestException {
        HttpGet request = null;
        HttpResponse response = null;
        InputStream stream = null;
        boolean wasNotModified = false;

        try {
            request = this.createRequest(uri, customHeaders);
            response = this.getResponse(request, preprocessor);

            StatusLine status = response.getStatusLine();
            if (status.getStatusCode() == 304) {
                wasNotModified = true;
            } else if (status.getStatusCode() != 200) {
                throw new HttpRequestException(status.getStatusCode() + " - " + status.getReasonPhrase());
            } else {
                stream = this.fromResponse(response, listener, task);
            }

        } catch (HttpRequestException e) {
            ExtendedHttpClient.releaseRequestResponse(request, response);
            throw e;
        }

        HttpStreamModel result = new HttpStreamModel();
        result.stream = stream;
        result.request = request;
        result.response = response;
        result.notModifiedResult = wasNotModified;

        return result;
    }

    public InputStream fromResponse(HttpResponse response) throws HttpRequestException {
        return this.fromResponse(response, null, null);
    }

    public InputStream fromResponse(HttpResponse response, IProgressChangeListener listener, ICancelled task) throws HttpRequestException {
        try {
            HttpEntity entity = response.getEntity();

            return IoUtils.modifyInputStream(entity.getContent(), entity.getContentLength(), listener, task);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            ExtendedHttpClient.releaseResponse(response);
            throw new HttpRequestException(e.getMessage() != null ? e.getMessage() : "Failed to read response");
        }
    }

    public void removeIfModifiedForUri(String uri) {
        this.mIfModifiedMap.remove(uri);
    }

    private HttpGet createRequest(String uri, Header[] customHeaders) throws HttpRequestException {
        HttpGet request = null;
        try {
            request = new HttpGet(uri);

            if (this.mIfModifiedMap.containsKey(uri)) {
                request.setHeader("If-Modified-Since", this.mIfModifiedMap.get(uri));
            }

            if (customHeaders != null) {
                request.setHeaders(customHeaders);
            }

            return request;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            ExtendedHttpClient.releaseRequest(request);
            throw new HttpRequestException(e.getMessage() != null ? e.getMessage() : "Failed to create request");
        }
    }

    private HttpResponse getResponse(HttpGet request, @Nullable final RequestPreprocessor preprocessor) throws HttpRequestException {
        HttpResponse response = null;
        Exception responseException = null;

        // try several times if exception, break the loop after a successful read
        for (int i = 0; i < 3; i++) {
            try {
                //this.mHttpClient.getCookieStore().addCookie(new BasicClientCookie("key", Math.random() + ""));
                if (preprocessor != null) {
                    preprocessor.process(mHttpClient);
                }
                response = this.mHttpClient.execute(request);

                if (response.getStatusLine().getStatusCode() == 200) {
                    // save the last modified date
                    Header header = response.getFirstHeader("Last-Modified");
                    if (header != null) {
                        this.mIfModifiedMap.put(request.getURI().toString(), header.getValue());
                    }
                }

                responseException = null;
                break;
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg == null) {
                    msg = e.toString();
                }
                Log.e(TAG, msg);
                responseException = e;

                if ("recvfrom failed: ECONNRESET (Connection reset by peer)".equals(e.getMessage())) {
                    // a stupid error, I have no idea how to solve it so I just try again
                    continue;
                } else {
                    break;
                }
            }
        }

        if (responseException != null) {
            ExtendedHttpClient.releaseRequestResponse(request, response);
            if (responseException.getMessage() != null) {
                throw new HttpRequestException(responseException.getMessage());
            } else {
                throw new HttpRequestException("");
            }
        }

        return response;
    }
}
