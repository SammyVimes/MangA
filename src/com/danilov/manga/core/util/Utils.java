package com.danilov.manga.core.util;

import android.content.Context;
import android.widget.Toast;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * Created by Semyon Danilov on 18.05.2014.
 */
public class Utils {

    private static final String TAG = "Utils";

    public static Document toDocument(final String content) {
        return Jsoup.parse(content);
    }

    public static JSONObject toJSON(final String content) throws JSONException {
        return new JSONObject(content);
    }

    public static void showToast(final Context context, final String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

    public static String stringResource(final Context context, final int id) {
        return context.getResources().getString(id);
    }

    public static String errorMessage(final Context context, final String error, final int errorMessageId) {
        return stringResource(context, errorMessageId) + ": " + error;
    }

}
