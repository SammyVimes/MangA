package com.danilov.supermanga.core.module;

import android.support.annotation.NonNull;

import com.android.httpimage.BitmapCache;
import com.android.httpimage.BitmapMemoryCache;
import com.android.httpimage.FileSystemPersistence;
import com.android.httpimage.HttpImageManager;
import com.danilov.supermanga.core.application.MangaApplication;
import com.danilov.supermanga.core.cache.CacheDirectoryManager;
import com.danilov.supermanga.core.http.ExtendedHttpClient;
import com.danilov.supermanga.core.http.HttpBitmapReader;
import com.danilov.supermanga.core.http.HttpBytesReader;
import com.danilov.supermanga.core.http.HttpStreamReader;
import com.danilov.supermanga.core.service.LocalImageManager;

import org.apache.http.client.HttpClient;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * Created by Semyon on 18.03.2016.
 */
@Module
public class NetworkModule {

    @Provides
    @Singleton
    public HttpStreamReader provideHttpStreamReader(@NonNull final MangaApplication application) {
        return new HttpStreamReader(new ExtendedHttpClient(), application.getResources());
    }

    @Provides
    @Singleton
    public HttpBytesReader provideHttpBytesReader(@NonNull final MangaApplication application,
                                                  @NonNull final HttpStreamReader streamReader) {
        return new HttpBytesReader(streamReader, application.getResources());
    }

    @Provides
    @Singleton
    public HttpBitmapReader provideHttpBitmapReader(@NonNull final HttpBytesReader httpBytesReader) {
        return new HttpBitmapReader(httpBytesReader);
    }

    @Provides
    @Singleton
    public HttpImageManager provideHttpImageManager(@NonNull final HttpBitmapReader httpBitmapReader,
                                                    @NonNull final MangaApplication application,
                                                    @NonNull final BitmapCache bitmapCache,
                                                    @NonNull final CacheDirectoryManager cacheDirectoryManager) {
        FileSystemPersistence fsp = new FileSystemPersistence(cacheDirectoryManager);
        BitmapMemoryCache bmc = new BitmapMemoryCache(0.4f);
        return new HttpImageManager(bitmapCache, fsp, application.getResources(), httpBitmapReader);
    }

    @Provides
    @Singleton
    public LocalImageManager provideLocalImageManager(@NonNull final BitmapCache bitmapCache,
                                                      @NonNull final MangaApplication application) {
        return new LocalImageManager(bitmapCache, application.getResources());
    }

    @Provides
    public HttpClient provideHttpClient() {
        return new ExtendedHttpClient();
    }

}
