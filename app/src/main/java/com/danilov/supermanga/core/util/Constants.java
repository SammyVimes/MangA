package com.danilov.supermanga.core.util;

import android.app.AlarmManager;
import android.os.Environment;

import com.danilov.supermanga.core.application.ApplicationSettings;
import com.danilov.supermanga.core.database.HistoryDAO;
import com.danilov.supermanga.core.database.MangaDAO;

import java.io.File;

/**
 * Created by Semyon Danilov on 16.05.2014.
 */
public class Constants {

    public static final boolean IS_MARKET_VERSION = false;
    public static final boolean HAS_ADS = false;

    public static final long FILE_CACHE_THRESHOLD = IoUtils.convertMbToBytes(25);
    public static final long FILE_CACHE_TRIM_AMOUNT = IoUtils.convertMbToBytes(15);

    public static final String USER_AGENT_STRING = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/45.0.2454.101 YaBrowser/15.10.2454.3865 Safari/537.36";

    public static final String MANGA_PARCEL_KEY = "MPK";
    public static final String MANGA_CHAPTERS_DIFFERENCE = "MCD";
    public static final String SELECTED_CHAPTERS_KEY = "SCK";
    public static final String REPOSITORY_KEY = "RK";
    public static final String FROM_CHAPTER_KEY = "FCK";
    public static final String FROM_PAGE_KEY = "FPK";
    public static final String FRAGMENTS_KEY = "FK";
    public static final String SHOW_ONLINE = "SO";

    public static final String LAST_UPDATE_TIME = "LAST_UPDATE_TIME";

    public static final long UPDATES_INTERVAL = AlarmManager.INTERVAL_HALF_DAY;

    public static final String RATED = "RATED";
    public static final long VIEWER_SAVE_PERIOD = 60_000;
    public static final long VIEWER_INFO_PERIOD = 10_000;

    public static class Settings {

        public static final String MANGA_DB_PATH;
        public static final String HISTORY_DB_PATH;
        static {
            File externalStorageDir = Environment.getExternalStorageDirectory();
            //{SD_PATH}/Android/data/com.danilov.manga/download
            File dbPathFile = new File(externalStorageDir, "Android" + File.separator + "data" + File.separator + ApplicationSettings.PACKAGE_NAME + File.separator + "db");
            MANGA_DB_PATH = dbPathFile + File.separator + MangaDAO.DB_NAME;
            HISTORY_DB_PATH = dbPathFile + File.separator + HistoryDAO.DB_NAME;
        }

        public static final String[][] DB_FILES = {{MangaDAO.DB_NAME + "_new", MANGA_DB_PATH},
                {HistoryDAO.DB_NAME + "_new", HISTORY_DB_PATH},
                {MangaDAO.DB_NAME + "_new" + "-journal", MANGA_DB_PATH + "-journal"},
                {HistoryDAO.DB_NAME + "_new" + "-journal", HISTORY_DB_PATH + "-journal"}};

        public static final String ONLINE_SETTINGS_FILENAME = "settings.json";
        public static final String LAST_UPDATE_PROFILE_TIME_GOOGLE = "LAST_UPDATE_PROFILE_TIME";
        public static final String LAST_UPDATE_PROFILE_TIME_YANDEX = "LAST_UPDATE_PROFILE_TIME_YANDEX";
        public static final String GOOGLE_PROFILE_NAME = "GOOGLE_PROFILE_NAME";

        public static final String USER_NAME = "USER_NAME";
        public static final String EMAIL = "EMAIL";
        public static final String TIME_READ = "TIME_READ";
        public static final String MANGA_DOWNLOAD_PATH = "MANGA_DOWNLOAD_PATH";
        public static final String MANGA_FINISHED = "MANGA_FINISHED";
        public static final String BYTES_DOWNLOADED = "BYTES_DOWNLOADED";
        public static final String ALWAYS_SHOW_VIEWER_BUTTONS = "ALWAYS_SHOW_VIEWER_BUTTONS";
        public static final String ORBOT_PROXY ="ORBOT_PROXY";
        public static final String TUTORIAL_VIEWER_PASSED = "TUTORIAL_VIEWER_PASSED";

        public static final String[] ALL_SETTINGS = {USER_NAME, EMAIL, TIME_READ, MANGA_DOWNLOAD_PATH, MANGA_FINISHED, BYTES_DOWNLOADED, ALWAYS_SHOW_VIEWER_BUTTONS, ORBOT_PROXY, TUTORIAL_VIEWER_PASSED};

    }

    public static class HentaichanConstants {

        public static final String LOGIN = "HCLOGIN";
        public static final String PASSWORD = "HCPASSWORD";
        public static final String AUTHORIZED = "HCAUTHORIZED";

    }

    public static class ImageRestrictions {

        //если превышает, то не грузим заранее
        public static final int MAX_SIDE_SIZE = 2500; //pxls

    }

}
