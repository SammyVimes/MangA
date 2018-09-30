package com.danilov.supermanga.core.repository.special;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.danilov.supermanga.core.http.ExtendedHttpClient;
import com.danilov.supermanga.core.repository.RepositoryEngine;
import com.danilov.supermanga.core.repository.RepositoryException;
import com.danilov.supermanga.core.util.IoUtils;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Semyon on 19.12.2015.
 */
public abstract class CloudFlareBypassEngine implements RepositoryEngine {

    private CookieStore cookieStore = null;

    public Map<String, String> getCFCookies() {
        return null;
    }

    public CookieStore getCookieStore() {
        return cookieStore;
    }

    private final static Pattern OPERATION_PATTERN = Pattern.compile("setTimeout\\(function\\(\\)\\{\\s+(var (?:\\w,)+f.+?\\r?\\n[\\s\\S]+?a\\.value =.+?)\\r?\\n");
    private final static Pattern PASS_PATTERN = Pattern.compile("name=\"pass\" value=\"(.+?)\"");
    private final static Pattern CHALLENGE_PATTERN = Pattern.compile("name=\"jschl_vc\" value=\"(\\w+)\"");

    @Nullable
    private HttpResponse parseCFResponse(final HttpResponse response, final DefaultHttpClient httpClient, final HttpContext context, final String uri) throws IOException {
        byte[] result = IoUtils.convertStreamToBytes(response.getEntity().getContent());
        String responseString = IoUtils.convertBytesToString(result);


        // инициализируем Rhino
        Context rhino = Context.enter();
        try {
            String domain = getDomain();

            // CF ожидает ответа после некоторой задержки
            Thread.sleep(5000);

            // вытаскиваем арифметику
            Matcher operationSearch = OPERATION_PATTERN.matcher(responseString);
            Matcher challengeSearch = CHALLENGE_PATTERN.matcher(responseString);
            Matcher passSearch = PASS_PATTERN.matcher(responseString);
            if(!operationSearch.find() || !passSearch.find() || !challengeSearch.find()) {
                return null;
            }

            String rawOperation = operationSearch.group(1); // операция
            String challengePass = passSearch.group(1); // ключ
            String challenge = challengeSearch.group(1); // хэш

            // Oooops...
            if (rawOperation == null || challenge == null || challengePass == null) {
                throw new Exception("Failed resolving Cloudflare challenge");
            }

            // вырезаем присвоение переменной
            String js = rawOperation
                    .replaceAll("a\\.value = (.+ \\+ t\\.length).+", "$1")
                    .replaceAll("\\s{3,}[a-z](?: = |\\.).+", "")
                    .replaceAll("t.length", String.valueOf(domain.length()) )
                    .replace("\n", "");

            rhino.setOptimizationLevel(-1); // без этой строки rhino не запустится под Android
            Scriptable scope = rhino.initStandardObjects(); // инициализируем пространство исполнения

            // either do or die trying
            double res = ((Double) rhino.evaluateString(scope, js, "CloudFlare JS Challenge", 1, null)).doubleValue();
            //String answer = String.valueOf(res); // ответ на javascript challenge
            String answer = String.format(Locale.ENGLISH, "%1$,.10f", res);

            final List<NameValuePair> params = new ArrayList<>(3);
            params.add(new BasicNameValuePair("jschl_vc", challenge));
            params.add(new BasicNameValuePair("pass", challengePass));
            params.add(new BasicNameValuePair("jschl_answer", answer));

            String url = "http://" + domain + "/cdn-cgi/l/chk_jschl?" + URLEncodedUtils.format(params, "windows-1251");

            HttpGet httpGet = new HttpGet();
            httpGet.setURI(URI.create(url));
            httpGet.setHeader("Referer", "http://" + domain + "/"); // url страницы, с которой было произведено перенаправление
            httpGet.setHeader("Accept", "text/html,application/xhtml+xml,application/xml");
            httpGet.setHeader("Accept-Language", "ru");

            HttpResponse httpResponse = context != null ? httpClient.execute(httpGet, context) : httpClient.execute(httpGet);
            int status = httpResponse.getStatusLine().getStatusCode();
            if(status == HttpStatus.SC_OK) { // в ответе придёт страница, указанная в Referer
                cookieStore = httpClient.getCookieStore();
                return httpResponse;
            }
        } catch (Exception e) {
            return null;
        } finally {
            Context.exit(); // выключаем Rhino
        }
        return null;
    }

    public void emptyRequest() throws IOException, RepositoryException {
        queryRepository("", Collections.emptyList());
    }

    private Lock lock = new ReentrantLock();
    private volatile boolean recursiveEnter = false;

    public HttpResponse loadPage(final HttpUriRequest request, final HttpContext context) throws IOException, RepositoryException {
        DefaultHttpClient httpClient = new ExtendedHttpClient();
        boolean lockLocked = false;
        try {
            if (cookieStore == null) {
                lock.lock();
                lockLocked = true;
            }
            if (cookieStore != null) {
                httpClient.setCookieStore(cookieStore);
            } else {
                if (!recursiveEnter) {
                    recursiveEnter = true;
                    emptyRequest();
                    if (cookieStore != null) {
                        httpClient.setCookieStore(cookieStore);
                    }
                }
            }
            HttpResponse response = context != null ? httpClient.execute(request, context) : httpClient.execute(request);
            if (response.getStatusLine().getStatusCode() >= 400) {
                HttpResponse cfResponse = response;
                response = parseCFResponse(cfResponse, httpClient, context, request.getURI().toString());
                return response;
            }
            return response;
        } finally {
            if (lockLocked) {
                lock.unlock();
            }
        }
    }

    public HttpResponse loadPage(final HttpUriRequest request) throws IOException, RepositoryException {
        return loadPage(request, null);
    }

    @NonNull
    public abstract String getDomain();

    /**
     * Пустой запрос, чтобы получить куки CloudFlare
     * @return url
     */
    @NonNull
    public abstract String getEmptyRequestURL();

}