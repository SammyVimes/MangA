package com.danilov.supermanga.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;

import com.danilov.supermanga.R;
import com.danilov.supermanga.core.interfaces.RefreshableActivity;
import com.danilov.supermanga.core.service.OnlineStorageProfileService;
import com.danilov.supermanga.core.service.ServiceConnectionListener;
import com.danilov.supermanga.core.util.Constants;
import com.danilov.supermanga.core.widget.RelativeTimeTextView;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.GooglePlayServicesUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * Created by Semyon on 05.04.2016.
 */
public class CloudStorageActivity extends BaseToolbarActivity implements RefreshableActivity {

    @Bind(R.id.google_sync_card)
    public View googleSyncCard;

    @Bind(R.id.yandex_sync_card)
    public View yandexSyncCard;

    @Bind(R.id.google_sync_button)
    public View googleSyncButton;

    @Bind(R.id.google_download_button)
    public View googleDownloadButton;

    @Bind(R.id.yandex_sync_button)
    public View yandexSyncButton;

    @Bind(R.id.yandex_download_button)
    public View yandexDownloadButton;

    @Bind(R.id.google_account)
    public RelativeTimeTextView googleAccountTextView;

    @Bind(R.id.yandex_account)
    public RelativeTimeTextView yandexAccountTextView;

    @Bind(R.id.progress_bar)
    public ProgressBar progressBar;

    private ServiceHandler handler = new ServiceHandler(this);

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cloud_storage);
        ButterKnife.bind(this);
        init();
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    private void init() {
        googleSyncCard.setOnClickListener(v -> {
            if (service != null) {
                service.connect();
            }
        });
        googleSyncButton.setOnClickListener(v -> {
            if (service == null) {
                return;
            }
            startRefresh();
            service.save();
        });
        googleDownloadButton.setOnClickListener(v -> {
            if (service == null) {
                return;
            }
            startRefresh();
            service.download();
        });
        yandexSyncCard.setOnClickListener(v -> {
            if (service != null) {
                service.connectYandex();
            }
        });
        yandexSyncButton.setOnClickListener(v -> {
            if (service == null) {
                return;
            }
            startRefresh();
            service.saveYandex();
        });
        yandexDownloadButton.setOnClickListener(v -> {
            if (service == null) {
                return;
            }
            startRefresh();
            service.downloadYandex();
        });



        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        long lastUpdateTimeGoogle = sharedPreferences.getLong(Constants.Settings.LAST_UPDATE_PROFILE_TIME_GOOGLE, -1L);
        long lastUpdateTimeYandex = sharedPreferences.getLong(Constants.Settings.LAST_UPDATE_PROFILE_TIME_YANDEX, -1L);

        String yandexProfileName = sharedPreferences.getString("YA_USERNAME", null);
        if (yandexProfileName == null) {
            yandexProfileName = "Нажмите, чтобы подключить";
            yandexAccountTextView.setPrefix(yandexProfileName);
            yandexAccountTextView.setReferenceTime(RelativeTimeTextView.ONLY_PREFIX);
        } else {
            String prefix =  yandexProfileName + " (" + getString(R.string.sv_synchronized) + " ";
            yandexAccountTextView.setPrefix(prefix);
            yandexAccountTextView.setReferenceTime(lastUpdateTimeYandex);
            yandexAccountTextView.setSuffix(")");
        }

        String googleProfileName = sharedPreferences.getString(Constants.Settings.GOOGLE_PROFILE_NAME, null);
        if (googleProfileName == null) {
            googleProfileName = "Нажмите, чтобы подключить";
            googleAccountTextView.setPrefix(googleProfileName);
            googleAccountTextView.setReferenceTime(RelativeTimeTextView.ONLY_PREFIX);
        } else {
            String prefix =  googleProfileName + " (" + getString(R.string.sv_synchronized) + " ";
            googleAccountTextView.setPrefix(prefix);
            googleAccountTextView.setReferenceTime(lastUpdateTimeGoogle);
            googleAccountTextView.setSuffix(")");
        }

        if (getIntent() != null && getIntent().getData() != null) {
            onYandexLogin();
        }

        serviceConnection = OnlineStorageProfileService.bindService(this, new ServiceConnectionListener<OnlineStorageProfileService>() {
            @Override
            public void onServiceConnected(final OnlineStorageProfileService service) {
                CloudStorageActivity.this.service = service;
                service.setServiceHandler(handler);
            }

            @Override
            public void onServiceDisconnected(final OnlineStorageProfileService service) {

            }
        });
    }

    @Override
    protected void onDestroy() {
        if (serviceConnection != null && service != null) {
            unbindService(serviceConnection);
            service.removeHandler();
            service = null;
        }
        this.handler.activity = null;
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static final int GOOGLE_AUTH_REQUEST_CODE = 1;
    private static final int UNAVAILABLE_GOOGLE_AUTH_REQUEST_CODE = 2;

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case GOOGLE_AUTH_REQUEST_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    service.connect();
                }
                break;
        }
    }

    private void onYandexLogin () {
        Uri data = getIntent().getData();
        setIntent(null);
        Pattern pattern = Pattern.compile("access_token=(.*?)(&|$)");
        Matcher matcher = pattern.matcher(data.toString());
        if (matcher.find()) {
            final String token = matcher.group(1);
            if (!TextUtils.isEmpty(token)) {
                Log.d("ProfileActivity", "onLogin: token: " + token);
                saveToken(token);
            } else {
                Log.w("ProfileActivity", "onYandexLogin: empty token");
            }
        } else {
            Log.w("ProfileActivity", "onYandexLogin: token not found in return url");
        }
    }

    private void saveToken(String token) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString("YA_USERNAME", "YaUser");
        editor.putString("YA_TOKEN", token);
        editor.apply();
        handler.sendEmptyMessage(OnlineStorageProfileService.YANDEX_CONNECTED);
    }

    private ServiceConnection serviceConnection;
    private OnlineStorageProfileService service;

    @Override
    public void startRefresh() {
        progressBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void stopRefresh() {
        progressBar.setVisibility(View.GONE);
    }

    private static class ServiceHandler extends Handler {

        private CloudStorageActivity activity;

        public ServiceHandler(final CloudStorageActivity activity) {
            this.activity = activity;
        }

        @Override
        public void handleMessage(final Message msg) {
            if (activity == null) {
                return;
            }
            switch (msg.what) {
                case OnlineStorageProfileService.GOOGLE_CONNECTED:
                    activity.googleSyncButton.setVisibility(View.VISIBLE);
                    activity.googleDownloadButton.setVisibility(View.VISIBLE);
                    if (activity.service != null) {
                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext());
                        long lastUpdateTime = sharedPreferences.getLong(Constants.Settings.LAST_UPDATE_PROFILE_TIME_GOOGLE, -1L);
                        String accountName = activity.service.getGoogleConnector().getAccountName();
                        String prefix =  accountName + " (" + activity.getString(R.string.sv_synchronized) + " ";
                        activity.googleAccountTextView.setPrefix(prefix);
                        activity.googleAccountTextView.setReferenceTime(lastUpdateTime);
                        activity.googleAccountTextView.setSuffix(")");

                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString(Constants.Settings.GOOGLE_PROFILE_NAME, accountName).apply();
                    }
                    break;
                case OnlineStorageProfileService.GOOGLE_NEED_CONFIRMATION:
                    ConnectionResult connectionResult = (ConnectionResult) msg.obj;
                    if (!connectionResult.hasResolution()) {
                        // show the localized error dialog.
                        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
                        apiAvailability.getErrorDialog(activity, connectionResult.getErrorCode(), UNAVAILABLE_GOOGLE_AUTH_REQUEST_CODE).show();
                        return;
                    }

                    try {
                        connectionResult.startResolutionForResult(activity, GOOGLE_AUTH_REQUEST_CODE);
                    } catch (IntentSender.SendIntentException e) {
                        e.printStackTrace();
                    }
                    break;
                case OnlineStorageProfileService.GOOGLE_SENT_SUCCESS:
                    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext());

                    long lastUpdateTime = System.currentTimeMillis();
                    sharedPreferences.edit().putLong(Constants.Settings.LAST_UPDATE_PROFILE_TIME_GOOGLE, lastUpdateTime).apply();
                    String accountName = activity.service.getGoogleConnector().getAccountName();
                    String prefix =  accountName + " (" + activity.getString(R.string.sv_synchronized) + " ";
                    activity.googleAccountTextView.setPrefix(prefix);
                    activity.googleAccountTextView.setReferenceTime(lastUpdateTime);
                    activity.googleAccountTextView.setSuffix(")");
                    activity.stopRefresh();
                    break;
                case OnlineStorageProfileService.GOOGLE_DOWNLOADED:
                    activity.init();
                    activity.stopRefresh();
                    break;
                case OnlineStorageProfileService.YANDEX_NEED_CONFIRMATION:
                    String yandexAuthURL = "https://oauth.yandex.ru/authorize?response_type=token&client_id=" + activity.getString(R.string.yandex_client_id);
                    activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(yandexAuthURL)));
                    break;
                case OnlineStorageProfileService.YANDEX_CONNECTED:
                    activity.yandexSyncButton.setVisibility(View.VISIBLE);
                    activity.yandexDownloadButton.setVisibility(View.VISIBLE);
                    if (activity.service != null) {
                        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext());
                        lastUpdateTime = sharedPreferences.getLong(Constants.Settings.LAST_UPDATE_PROFILE_TIME_YANDEX, -1L);
                        accountName = sharedPreferences.getString("YA_USERNAME", "");
                        prefix =  accountName + " (" + activity.getString(R.string.sv_synchronized) + " ";
                        activity.yandexAccountTextView.setPrefix(prefix);
                        activity.yandexAccountTextView.setReferenceTime(lastUpdateTime);
                        activity.yandexAccountTextView.setSuffix(")");
                    }
                    break;
                case OnlineStorageProfileService.YANDEX_SENT_SUCCESS:
                    sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext());

                    lastUpdateTime = System.currentTimeMillis();
                    sharedPreferences.edit().putLong(Constants.Settings.LAST_UPDATE_PROFILE_TIME_YANDEX, lastUpdateTime).apply();
                    accountName = activity.service.getYandexConnector().getAccountName();
                    prefix =  accountName + " (" + activity.getString(R.string.sv_synchronized) + " ";
                    activity.yandexAccountTextView.setPrefix(prefix);
                    activity.yandexAccountTextView.setReferenceTime(lastUpdateTime);
                    activity.yandexAccountTextView.setSuffix(")");
                    activity.stopRefresh();
                    break;
                case OnlineStorageProfileService.YANDEX_DOWNLOADED:
                    activity.init();
                    activity.stopRefresh();
                    break;
            }
        }

    }

}
