package com.danilov.supermanga.core.service;

import android.app.Notification;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.SyncStateContract;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.NotificationCompat;

import com.danilov.supermanga.R;
import com.danilov.supermanga.core.application.ApplicationSettings;
import com.danilov.supermanga.core.onlinestorage.GoogleDriveConnector;
import com.danilov.supermanga.core.onlinestorage.OnlineStorageConnector;
import com.danilov.supermanga.core.onlinestorage.YandexDiskConnector;
import com.danilov.supermanga.core.util.Constants;
import com.danilov.supermanga.core.util.Logger;
import com.google.android.gms.common.ConnectionResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Map;

/**
 * Created by Semyon on 30.08.2015.
 */
public class OnlineStorageProfileService extends Service {

    private static final Logger LOGGER = new Logger(OnlineStorageProfileService.class);

    public static final int GOOGLE_CONNECTED = 0;
    public static final int GOOGLE_NEED_CONFIRMATION = 1;
    public static final int GOOGLE_SENT_SUCCESS = 2;
    public static final int GOOGLE_DOWNLOADED = 3;

    public static final int YANDEX_CONNECTED = 4;
    public static final int YANDEX_NEED_CONFIRMATION = 5;
    public static final int YANDEX_SENT_SUCCESS = 6;
    public static final int YANDEX_DOWNLOADED = 7;

    private Handler handler = null;

    private OnlineStorageConnector googleConnector = null;

    private OnlineStorageConnector yandexConnector = null;

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        return START_STICKY;
    }

    public static ServiceConnection bindService(final Context context, final ServiceConnectionListener<OnlineStorageProfileService> listener) {
        context.startService(new Intent(context, OnlineStorageProfileService.class));
        OnlineStorageServiceConnection connection = new OnlineStorageServiceConnection(listener);
        context.bindService(new Intent(context, OnlineStorageProfileService.class), connection, BIND_AUTO_CREATE);
        return connection;
    }

    public synchronized void setServiceHandler(final Handler handler) {
        this.handler = handler;
    }

    public synchronized void removeHandler() {
        this.handler = null;
    }

    public void connect() {
        googleConnector = new GoogleDriveConnector(googleConnectorListener);
        googleConnector.init();
    }

    public void connectYandex() {
        yandexConnector = new YandexDiskConnector(yandexConnectorListener);
        yandexConnector.init();
    }

    private void notifyHandler(final int action, final Object object) {
        if (handler != null) {
            if (object == null) {
                handler.sendEmptyMessage(action);
            } else {
                Message message = handler.obtainMessage();
                message.what = action;
                message.obj = object;
                handler.sendMessage(message);
            }
        }
    }

    public OnlineStorageConnector getGoogleConnector() {
        return googleConnector;
    }

    public OnlineStorageConnector getYandexConnector() {
        return yandexConnector;
    }

    public void sendDataViaGoogle() {
    }

    public void save() {
        showPendingUploadNotification(true);
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        final long lastSyncTime = sharedPreferences.getLong(Constants.Settings.LAST_UPDATE_PROFILE_TIME_GOOGLE, -1);
        googleConnector.getExistingFile(Constants.Settings.ONLINE_SETTINGS_FILENAME, new OnlineStorageConnector.CommandCallback<OnlineStorageConnector.OnlineFile>() {
            @Override
            public void onCommandSuccess(final OnlineStorageConnector.OnlineFile onlineFile) {
                if (onlineFile != null) {
                    //заливаем на диск
                    replaceCurrent(onlineFile);
                } else {
                    //диск пустой, заливаем файл
                    createNew();
                }
            }

            @Override
            public void onCommandError(final String message) {

            }
        });
    }

    public void saveYandex() {
        showPendingUploadNotification(false);
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        final long lastSyncTime = sharedPreferences.getLong(Constants.Settings.LAST_UPDATE_PROFILE_TIME_YANDEX, -1);
        yandexConnector.getExistingFile(Constants.Settings.ONLINE_SETTINGS_FILENAME, new OnlineStorageConnector.CommandCallback<OnlineStorageConnector.OnlineFile>() {
            @Override
            public void onCommandSuccess(final OnlineStorageConnector.OnlineFile onlineFile) {
                if (onlineFile != null) {
                    //заливаем на диск
                    replaceCurrentYandex(onlineFile);
                } else {
                    //диск пустой, заливаем файл
                    createNewYandex();
                }
            }

            @Override
            public void onCommandError(final String message) {

            }
        });
    }

    public void download() {
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        final long lastSyncTime = sharedPreferences.getLong(Constants.Settings.LAST_UPDATE_PROFILE_TIME_GOOGLE, -1);
        googleConnector.getExistingFile(Constants.Settings.ONLINE_SETTINGS_FILENAME, new OnlineStorageConnector.CommandCallback<OnlineStorageConnector.OnlineFile>() {
            @Override
            public void onCommandSuccess(final OnlineStorageConnector.OnlineFile onlineFile) {
                if (onlineFile != null) {
                    download(onlineFile);
                } else {
                    //alert-no-data
                }
            }

            @Override
            public void onCommandError(final String message) {

            }
        });
    }

    public void downloadYandex() {
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        final long lastSyncTime = sharedPreferences.getLong(Constants.Settings.LAST_UPDATE_PROFILE_TIME_YANDEX, -1);
        yandexConnector.getExistingFile(Constants.Settings.ONLINE_SETTINGS_FILENAME, new OnlineStorageConnector.CommandCallback<OnlineStorageConnector.OnlineFile>() {
            @Override
            public void onCommandSuccess(final OnlineStorageConnector.OnlineFile onlineFile) {
                if (onlineFile != null) {
                    downloadYandex(onlineFile);
                } else {
                    //alert-no-data
                }
            }

            @Override
            public void onCommandError(final String message) {

            }
        });
    }

    private class SyncUpCallback implements OnlineStorageConnector.CommandCallback<Boolean> {

        private OnlineStorageConnector connector;

        public SyncUpCallback(final OnlineStorageConnector connector) {
            this.connector = connector;
        }

        @Override
        public void onCommandSuccess(final Boolean object) {
            final OnlineStorageConnector.CommandCallback<Boolean> fileSendCallback = new OnlineStorageConnector.CommandCallback<Boolean>() {
                final int filesToSend = Constants.Settings.DB_FILES.length;
                int sent = 0;
                int callbacks = 0;
                @Override
                public synchronized void onCommandSuccess(final Boolean object) {
                    if (object) {
                        sent++;
                    }
                    callbacks++;
                    check();
                }

                @Override
                public synchronized void onCommandError(final String message) {
                    callbacks++;
                    check();
                }

                private void check() {
                    if (callbacks == filesToSend) {
                        if (sent != filesToSend) {

                        } else {
                            notifyHandler(connector.getSentSuccessCode(), null);
                            closePendingUploadNotification(connector.getSentSuccessCode() == GOOGLE_SENT_SUCCESS);
                        }
                    }
                }

            };

            for (int i = 0; i < Constants.Settings.DB_FILES.length; i++) {
                final String fileName = Constants.Settings.DB_FILES[i][0];
                final String localFilePath = Constants.Settings.DB_FILES[i][1];
                connector.getExistingFile(fileName, new OnlineStorageConnector.CommandCallback<OnlineStorageConnector.OnlineFile>() {
                    @Override
                    public void onCommandSuccess(final OnlineStorageConnector.OnlineFile onlineFile) {
                        //TODO: remove and uncomment
                        if (onlineFile != null) {
                            onlineFile.rewriteWith(new File(localFilePath), fileSendCallback);
                        } else {
                            connector.createFile(fileName, new File(localFilePath), OnlineStorageConnector.MimeType.SQLITE, fileSendCallback);
                        }
                    }

                    @Override
                    public void onCommandError(final String message) {

                    }
                });
            }
        }

        @Override
        public void onCommandError(final String message) {

        }

    }

    public void replaceCurrent(final OnlineStorageConnector.OnlineFile onlineFile) {
        onlineFile.rewriteWith(getSettingsJsonString(), new SyncUpCallback(googleConnector));
    }

    public void createNew() {
        googleConnector.createFile(Constants.Settings.ONLINE_SETTINGS_FILENAME, getSettingsJsonString(),
                OnlineStorageConnector.MimeType.TEXT_PLAIN, new SyncUpCallback(googleConnector));
    }

    public void replaceCurrentYandex(final OnlineStorageConnector.OnlineFile onlineFile) {
        onlineFile.rewriteWith(getSettingsJsonString(), new SyncUpCallback(yandexConnector));
    }

    public void createNewYandex() {
        yandexConnector.createFile(Constants.Settings.ONLINE_SETTINGS_FILENAME, getSettingsJsonString(),
                OnlineStorageConnector.MimeType.TEXT_PLAIN, new SyncUpCallback(yandexConnector));
    }

    private class DownloadCallback implements OnlineStorageConnector.CommandCallback<String> {

        private OnlineStorageConnector connector;

        public DownloadCallback(final OnlineStorageConnector connector) {
            this.connector = connector;
        }

        @Override
        public void onCommandSuccess(final String object) {


            //TODO: нужно предложить пользователю два варианта - скачать файл или залить новый
            //notifyHandler(GOOGLE_FRESH_INIT_HAS_FILES_ON_DISK, onlineFile);

            final OnlineStorageConnector.CommandCallback<Boolean> fileDownloadCallback = new OnlineStorageConnector.CommandCallback<Boolean>() {
                final int filesToSend = Constants.Settings.DB_FILES.length;
                int sent = 0;
                int callbacks = 0;

                @Override
                public synchronized void onCommandSuccess(final Boolean object) {
                    if (object) {
                        sent++;
                    }
                    callbacks++;
                    check();
                }

                @Override
                public synchronized void onCommandError(final String message) {
                    callbacks++;
                    check();
                }

                private void check() {
                    if (callbacks == filesToSend) {
                        if (sent != filesToSend) {
                        } else {
                            notifyHandler(connector.getDownloadedCode(), null);
                        }
                    }
                }

            };

            saveJson(object);

            for (int i = 0; i < Constants.Settings.DB_FILES.length; i++) {
                final String fileName = Constants.Settings.DB_FILES[i][0];
                final String localFilePath = Constants.Settings.DB_FILES[i][1];
                connector.getExistingFile(fileName, new OnlineStorageConnector.CommandCallback<OnlineStorageConnector.OnlineFile>() {
                    @Override
                    public void onCommandSuccess(final OnlineStorageConnector.OnlineFile onlineFile) {
                        if (onlineFile != null) {
                            onlineFile.download(localFilePath, fileDownloadCallback);
                        } else {
                            fileDownloadCallback.onCommandSuccess(true);
                        }
                    }

                    @Override
                    public void onCommandError(final String message) {

                    }
                });
            }
        }

        @Override
        public void onCommandError(final String message) {

        }
    }

    public void download(final OnlineStorageConnector.OnlineFile onlineFile) {
        onlineFile.download(new DownloadCallback(googleConnector));
    }

    public void downloadYandex(final OnlineStorageConnector.OnlineFile onlineFile) {
        onlineFile.download(new DownloadCallback(yandexConnector));
    }

    public String getSettingsJsonString() {
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        final JSONObject settingsObject = new JSONObject();
        Map<String, ?> all = sharedPreferences.getAll();
        for (String fieldName : Constants.Settings.ALL_SETTINGS) {
            Object o = all.get(fieldName);
            try {
                settingsObject.put(fieldName, o);
            } catch (JSONException e) {
                //e.printStackTrace();
            }
        }
        return settingsObject.toString();
    }

    private void saveJson(final String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);

            String userName = jsonObject.optString(Constants.Settings.USER_NAME, "");
            String email = jsonObject.optString(Constants.Settings.EMAIL, "");
            String mangaDownloadPath = jsonObject.optString(Constants.Settings.MANGA_DOWNLOAD_PATH, "");
            Long timeRead = jsonObject.optLong(Constants.Settings.TIME_READ, 0L);
            Long bytesDownloaded = jsonObject.optLong(Constants.Settings.BYTES_DOWNLOADED, 0L);
            Integer mangaFinished = jsonObject.optInt(Constants.Settings.MANGA_FINISHED, 0);
            boolean showViewerButtons = jsonObject.optBoolean(Constants.Settings.ALWAYS_SHOW_VIEWER_BUTTONS, false);
            boolean orbotProxy = jsonObject.optBoolean(Constants.Settings.ORBOT_PROXY,false);
            boolean tutorialViewerPassed = jsonObject.optBoolean(Constants.Settings.TUTORIAL_VIEWER_PASSED, false);

            ApplicationSettings applicationSettings = ApplicationSettings.get(getApplicationContext());
            ApplicationSettings.UserSettings userSettings = applicationSettings.getUserSettings();

            userSettings.setUserName(userName);
            userSettings.setEmail(email);
            userSettings.setDownloadPath(mangaDownloadPath);

            userSettings.setTimeRead(timeRead);
            userSettings.setBytesDownloaded(bytesDownloaded);
            userSettings.setMangasComplete(mangaFinished);

            userSettings.setAlwaysShowButtons(showViewerButtons);
            userSettings.setOrbotProxy(orbotProxy);
            userSettings.setTutorialViewerPassed(tutorialViewerPassed);
            applicationSettings.update(getApplicationContext());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private OnlineStorageConnector.StorageConnectorListener googleConnectorListener = new OnlineStorageConnector.StorageConnectorListener() {


        @Override
        public void onStorageConnected(final OnlineStorageConnector connector) {
            notifyHandler(GOOGLE_CONNECTED, null);
        }

        @Override
        public void onStorageDisconnected(final OnlineStorageConnector connector) {

        }

        @Override
        public void onConnectionFailed(final OnlineStorageConnector connector, final Object object) {
            ConnectionResult connectionResult = (ConnectionResult) object;
            notifyHandler(GOOGLE_NEED_CONFIRMATION, connectionResult);
        }

    };

    private OnlineStorageConnector.StorageConnectorListener yandexConnectorListener = new OnlineStorageConnector.StorageConnectorListener() {

        @Override
        public void onStorageConnected(final OnlineStorageConnector connector) {
            notifyHandler(YANDEX_CONNECTED, null);
        }

        @Override
        public void onStorageDisconnected(final OnlineStorageConnector connector) {

        }

        @Override
        public void onConnectionFailed(final OnlineStorageConnector connector, final Object object) {
            notifyHandler(YANDEX_NEED_CONFIRMATION, null);
        }
    };

    @Override
    public IBinder onBind(final Intent intent) {
        return new ServiceBinder();
    }

    public static class OnlineStorageServiceConnection implements ServiceConnection {

        private ServiceConnectionListener<OnlineStorageProfileService> listener;
        private OnlineStorageProfileService service;


        public OnlineStorageServiceConnection(final ServiceConnectionListener<OnlineStorageProfileService> listener) {
            this.listener = listener;
        }

        @Override
        public void onServiceConnected(final ComponentName componentName, final IBinder iBinder) {
            LOGGER.d("Service connected");
            if (listener != null) {
                ServiceBinder binder = (ServiceBinder) iBinder;
                service = binder.getService();
                listener.onServiceConnected(service);
            }
        }

        @Override
        public void onServiceDisconnected(final ComponentName componentName) {
            LOGGER.d("Service disconnected");
            if (listener != null) {
                listener.onServiceDisconnected(service);
            }
        }

    }

    private class ServiceBinder extends Binder {

        public OnlineStorageProfileService getService() {
            return OnlineStorageProfileService.this;
        }

    }


    private void showPendingUploadNotification(final boolean isGoogle) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setAutoCancel(true);
        builder.setSmallIcon(R.drawable.ic_action_cloud);

        builder.setLargeIcon(BitmapFactory.decodeResource(this.getResources(), R.drawable.dark_ic_saved));

        final String text = "Загрузка данных в " + (isGoogle ? "Google Drive" : "Яндекс.Диск");

        builder.setContentTitle("Super Manga Reader");
        builder.setContentText(text);
        Notification notification = builder.build();
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(isGoogle ? 1337 : 228, notification);
    }

    private void closePendingUploadNotification(final boolean isGoogle) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.cancel(isGoogle ? 1337 : 228);


        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setAutoCancel(true);
        builder.setSmallIcon(R.drawable.ic_action_cloud);

        builder.setLargeIcon(BitmapFactory.decodeResource(this.getResources(), R.drawable.dark_ic_saved));

        final String text = "Загружено в " + (isGoogle ? "Google Drive" : "Яндекс.Диск");
        builder.setTicker(text);
        builder.setContentTitle("Ура!");
        builder.setContentText(text);
        Notification notification = builder.build();
        notificationManager.notify(isGoogle ? 1338 : 229, notification);
    }

}
