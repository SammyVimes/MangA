package com.danilov.supermanga.core.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Semyon on 30.09.2018.
 */
public class RequestUtils {

    public static HttpPost from(@NonNull final String uri, @Nullable final String... params) throws UnsupportedEncodingException {
        HttpPost request = new HttpPost(uri);
        if (params != null) {
            if ((params.length % 2) != 0) {
                throw new RuntimeException("Incorrect number of params");
            }
            List<NameValuePair> nameValuePairs = new ArrayList<>(params.length / 2);
            for (int i = 0; i < params.length; ) {
                String name = params[i];
                String value = params[i + 1];
                nameValuePairs.add(new BasicNameValuePair(name, value));
                i += 2;
            }
            request.setEntity(new UrlEncodedFormEntity(nameValuePairs));
        }
        return request;
    }

}
