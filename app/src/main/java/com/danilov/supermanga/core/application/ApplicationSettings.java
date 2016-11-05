package com.danilov.supermanga.core.application;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import com.danilov.supermanga.activity.MainActivity;
import com.danilov.supermanga.core.util.Constants;
import com.danilov.supermanga.core.util.Decoder;

import java.io.File;

/**
 * Created by Semyon Danilov on 16.05.2014.
 */
public class ApplicationSettings {

    private static final String TAG = "ApplicationSettings";
    public static final String PACKAGE_NAME = "com.danilov.supermanga";

    private static final String DOWNLOAD_PATH_FIELD = "DPF";
    private static final String MANGA_DOWNLOAD_BASE_PATH_FIELD = "MDBPF";
    private static final String TUTORIAL_VIEWER_PASSED_FIELD = "TVPF";
    private static final String VIEWER_FULLSCREEN_FIELD = "VFF";
    private static final String FIRST_LAUNCH = "FL";
    private static final String TUTORIAL_MENU_PASSED_FIELD = "TMP";
    private static final String SHOW_VIEWER_BTNS_ALWAYS_FIELD = "SVBA";
    private static final String IN_RTL_MODE_FIELD = "IRMF";
    private static final String DECODER_FIELD = "DECF";
    private static final String MAIN_MENU_ITEM_FIELD = "MMI";

    private static ApplicationSettings instance;

    private String downloadPath;

    private boolean tutorialMenuPassed;

    private boolean firstLaunch;

    private boolean viewerFullscreen;

    private boolean isRTLMode;

    private String mainMenuItem;

    private Decoder decoder;

    private UserSettings userSettings;

    public UserSettings getUserSettings() {
        return userSettings;
    }

    public void setUserSettings(final UserSettings userSettings) {
        this.userSettings = userSettings;
    }

    public Decoder getDecoder() {
        return decoder;
    }

    public void setDecoder(final Decoder decoder) {
        this.decoder = decoder;
    }

    public boolean isRTLMode() {
        return isRTLMode;
    }

    public void setRTLMode(final boolean isRTLMode) {
        this.isRTLMode = isRTLMode;
    }

    public String getMainMenuItem() {
        return mainMenuItem;
    }

    public void setMainMenuItem(final String mainMenuItem) {
        this.mainMenuItem = mainMenuItem;
    }

    public String getDownloadPath() {
        return downloadPath;
    }

    public void setDownloadPath(final String downloadPath) {
        this.downloadPath = downloadPath;
    }

    public boolean isTutorialMenuPassed() {
        return tutorialMenuPassed;
    }

    public void setTutorialMenuPassed(final boolean tutorialMenuPassed) {
        this.tutorialMenuPassed = tutorialMenuPassed;
    }

    public boolean isFirstLaunch() {
        return firstLaunch;
    }

    public void setFirstLaunch(final boolean firstLaunch) {
        this.firstLaunch = firstLaunch;
    }

    public boolean isViewerFullscreen() {
        return viewerFullscreen;
    }

    public void setViewerFullscreen(final boolean viewerFullscreen) {
        this.viewerFullscreen = viewerFullscreen;
    }

    public static ApplicationSettings get(final Context context) {
        if (instance == null) {
            instance = new ApplicationSettings(context);
        }
        return instance;
    }

    private ApplicationSettings(final Context context) {
        load(context);
    }

    private void load(final Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE);
        this.downloadPath = sharedPreferences.getString(DOWNLOAD_PATH_FIELD, "");
        this.tutorialMenuPassed = sharedPreferences.getBoolean(TUTORIAL_MENU_PASSED_FIELD, false);
        this.firstLaunch = sharedPreferences.getBoolean(FIRST_LAUNCH, true);
        this.viewerFullscreen = sharedPreferences.getBoolean(VIEWER_FULLSCREEN_FIELD, false);
        this.isRTLMode = sharedPreferences.getBoolean(IN_RTL_MODE_FIELD, false);
        this.mainMenuItem = sharedPreferences.getString(MAIN_MENU_ITEM_FIELD, MainActivity.MainMenuItem.SEARCH.toString());
        this.decoder = Decoder.valueOf(sharedPreferences.getString(DECODER_FIELD, Decoder.Rapid.toString()));
        userSettings = new UserSettings();
        userSettings.init(context);
    }

    public void invalidate(final Context context) {
        load(context);
    }

    public void update(final Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(DOWNLOAD_PATH_FIELD, downloadPath);
        editor.putBoolean(TUTORIAL_MENU_PASSED_FIELD, tutorialMenuPassed);
        editor.putBoolean(VIEWER_FULLSCREEN_FIELD, viewerFullscreen);
        editor.putBoolean(FIRST_LAUNCH, firstLaunch);
        editor.putBoolean(IN_RTL_MODE_FIELD, isRTLMode);
        editor.putString(MAIN_MENU_ITEM_FIELD, mainMenuItem);
        editor.putString(DECODER_FIELD, decoder.toString());
        editor.apply();
        userSettings.save();
    }

    public static class UserSettings {

        private String userName = null;
        private String email = "";
        private long timeRead = 0;
        private String downloadPath = "";
        private int mangasComplete = 0;
        private long bytesDownloaded = 0;
        private boolean alwaysShowButtons = false;
        private boolean orbotProxy = false;
        private boolean tutorialViewerPassed = false;

        public void init(final Context context) {
            SharedPreferences sp = context.getSharedPreferences(TAG, Context.MODE_PRIVATE);
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            userName = sharedPreferences.getString(Constants.Settings.USER_NAME, "");
            email = sharedPreferences.getString(Constants.Settings.EMAIL, "");
            timeRead = sharedPreferences.getLong(Constants.Settings.TIME_READ, 0L);
            downloadPath = sharedPreferences.getString(Constants.Settings.MANGA_DOWNLOAD_PATH, "");

            if ("".equals(downloadPath)) {
                downloadPath = sp.getString(MANGA_DOWNLOAD_BASE_PATH_FIELD, "");
            }
            if ("".equals(downloadPath)) {
                loadMangaBasePath();
            }

            mangasComplete = sharedPreferences.getInt(Constants.Settings.MANGA_FINISHED, 0);
            bytesDownloaded = sharedPreferences.getLong(Constants.Settings.BYTES_DOWNLOADED, 0L);
            alwaysShowButtons = sharedPreferences.getBoolean(Constants.Settings.ALWAYS_SHOW_VIEWER_BUTTONS, false);
            orbotProxy =sharedPreferences.getBoolean(Constants.Settings.ORBOT_PROXY,false);
            tutorialViewerPassed = sharedPreferences.getBoolean(Constants.Settings.TUTORIAL_VIEWER_PASSED, false);
        }

        private void loadMangaBasePath() {
            //TODO: checking state
            File externalStorageDir = Environment.getExternalStorageDirectory();
            //{SD_PATH}/Android/data/{PACKAGE_NAME}/download
            File extStorageAppCachePath = new File(externalStorageDir, "Android" + File.separator + "data" + File.separator + PACKAGE_NAME + File.separator + "download");
            downloadPath = extStorageAppCachePath.getPath();
            File path = new File(downloadPath);
            if (!path.mkdirs() && !path.exists()) {
                Log.d(TAG, "Failure on creation of " + path.toString() + " path");
            }
        }

        private void save() {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MangaApplication.getContext());
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(Constants.Settings.USER_NAME, userName);
            editor.putString(Constants.Settings.EMAIL, email);
            editor.putString(Constants.Settings.MANGA_DOWNLOAD_PATH, downloadPath);

            editor.putLong(Constants.Settings.TIME_READ, timeRead);
            editor.putInt(Constants.Settings.MANGA_FINISHED, mangasComplete);
            editor.putLong(Constants.Settings.BYTES_DOWNLOADED, bytesDownloaded);
            editor.putBoolean(Constants.Settings.ALWAYS_SHOW_VIEWER_BUTTONS, alwaysShowButtons);
            editor.putBoolean(Constants.Settings.ORBOT_PROXY,orbotProxy);
            editor.putBoolean(Constants.Settings.TUTORIAL_VIEWER_PASSED, tutorialViewerPassed);
            editor.apply();
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(final String userName) {
            this.userName = userName;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(final String email) {
            this.email = email;
        }

        public long getTimeRead() {
            return timeRead;
        }

        public void setTimeRead(final long timeRead) {
            this.timeRead = timeRead;
        }

        public String getDownloadPath() {
            return downloadPath;
        }

        public void setDownloadPath(final String downloadPath) {
            this.downloadPath = downloadPath;
        }

        public int getMangasComplete() {
            return mangasComplete;
        }

        public void setMangasComplete(final int mangasComplete) {
            this.mangasComplete = mangasComplete;
        }

        public long getBytesDownloaded() {
            return bytesDownloaded;
        }

        public void setBytesDownloaded(final long bytesDownloaded) {
            this.bytesDownloaded = bytesDownloaded;
        }

        public boolean isAlwaysShowButtons() {
            return alwaysShowButtons;
        }

        public void setAlwaysShowButtons(final boolean alwaysShowButtons) {
            this.alwaysShowButtons = alwaysShowButtons;
        }
        public boolean isOrbotProxy() {return orbotProxy;}
        public void setOrbotProxy(final boolean orbotProxy){
            this.orbotProxy = orbotProxy;
        }
        public boolean isTutorialViewerPassed() {
            return tutorialViewerPassed;
        }

        public void setTutorialViewerPassed(final boolean tutorialViewerPassed) {
            this.tutorialViewerPassed = tutorialViewerPassed;
        }

        int hold = 0;
        private static final int MBYTE = 1024 * 1024;

        public void appendDownloadedSize(final long appended) {
            hold += appended;
            if (hold > MBYTE) {
                bytesDownloaded += hold;
                hold = 0;
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MangaApplication.getContext());
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putLong(Constants.Settings.BYTES_DOWNLOADED, bytesDownloaded).apply();
            }
        }

    }

}
