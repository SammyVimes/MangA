package com.danilov.supermanga.core.service;

import android.support.annotation.Nullable;
import android.util.Log;

import com.annimon.stream.Stream;
import com.danilov.supermanga.core.application.ApplicationSettings;
import com.danilov.supermanga.core.application.MangaApplication;
import com.danilov.supermanga.core.http.RequestPreprocessor;
import com.danilov.supermanga.core.interfaces.Pool;

import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Semyon Danilov on 26.05.2014. Using new mode - 07.06
 */
public class DownloadManager {

    private final String TAG = "DownloadManager";

    private ApplicationSettings.UserSettings userSettings = ApplicationSettings.get(MangaApplication.getContext()).getUserSettings();

    protected DownloadManagerThread thread = new DownloadManagerThread();

    private static final int MAX_BUFFER_SIZE = 1024;

    private DownloadPool pool = new DownloadPool();

    private DownloadProgressListener listener;

    private ArrayDeque<Download> downloads = new ArrayDeque<>();

    //thread
    final Lock lock = new ReentrantLock();

    final Condition isWake = lock.newCondition();
    //!thread

    public DownloadManager() {
        thread.start();
    }

    /**
     * Should only use to dump current downloads
     * @return
     */
    public List<Download> getDownloads() {
        lock.lock();
        try {
            return new ArrayList<>(downloads);
        } finally {
            lock.unlock();
        }
    }

    public void setDownloads(final List<Download> downloads) {
        lock.lock();
        try {
            this.downloads.addAll(downloads);
        } finally {
            lock.unlock();
        }
    }

    //holy shiiiet
    public Lock getLock() {
        return lock;
    }

    //executing only one download at a time
    //on complete start another download
    public Download startDownload(final String uri, final String filePath, @Nullable final RequestPreprocessor requestPreprocessor) {
        return startDownload(uri, filePath, requestPreprocessor, null);
    }

    public Download startDownload(final String uri, final String filePath, @Nullable final RequestPreprocessor requestPreprocessor, final Object tag) {
        lock.lock();
        Download download = null;
        try {
            download = pool.obtain();
            download.setUri(uri);
            download.setTag(tag);
            download.setFilePath(filePath);
            download.setPreprocessor(requestPreprocessor);
            downloads.add(download);
            isWake.signalAll();
        } finally {
            lock.unlock();
        }
        return download;
    }

    public Download startImportantDownload(final String uri, final String filePath, @Nullable final RequestPreprocessor requestPreprocessor, final Object tag) {
        lock.lock();
        Download download = null;
        try {
            download = pool.obtain();
            download.setUri(uri);
            download.setTag(tag);
            download.setFilePath(filePath);
            download.setPreprocessor(requestPreprocessor);
            downloads.addFirst(download);
            isWake.signalAll();
        } finally {
            lock.unlock();
        }
        return download;
    }


    public Download obtain() {
        return pool.obtain();
    }

    public void cancelDownload(final Download download) {
        lock.lock();
        try {
            download.setStatus(DownloadStatus.CANCELLED);
            wakeUp();
            Log.e(TAG, "Cancelling download");
        } catch (Exception e) {
            Log.d(TAG, "Exception occurred in cancelDownload(), error: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    public void cancelDownloadsWithTag(final Object tag) {
        lock.lock();
        try {
            Stream.of(downloads).filter(download -> tag.equals(download.getTag())).forEach((Download download) -> download.setStatus(DownloadStatus.CANCELLED));
            wakeUp();
            Log.e(TAG, "Cancelling download");
        } catch (Exception e) {
            Log.d(TAG, "Exception occurred in cancelDownload(), error: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    public void cancelAllDownloads() {
        lock.lock();
        try {
            for (Download download : downloads) {
                download.setStatus(DownloadStatus.CANCELLED);
            }
        } catch (Exception e) {
            Log.d(TAG, "Exception occurred in cancelAllDownloads(), error: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    public void restartError() {
        lock.lock();
        try {
            Download d = downloads.peek();
            if (d.getStatus() == DownloadStatus.ERROR) {
                d.setStatus(DownloadStatus.DOWNLOADING);
                wakeUp(); //!
            }
        } finally {
            lock.unlock();
        }
    }


    public void restore() {
        lock.lock();
        try {
            isWake.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void wakeUp() {
        lock.lock();
        try {
            isWake.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void skipDownload() {
        lock.lock();
        try {
            Download d = downloads.peek();
            if (d != null) {
                d.skip();
                wakeUp(); //!
            }
        } finally {
            lock.unlock();
        }
    }

    public void pauseDownload() {
        lock.lock();
        try {
            Download d = downloads.peek();
            if (d != null) {
                d.pause();
            }
        } finally {
            lock.unlock();
        }
    }

    public void resumeDownload() {
        lock.lock();
        try {
            Download d = downloads.peek();
            if (d.getStatus() == DownloadStatus.PAUSED) {
                d.setStatus(DownloadStatus.DOWNLOADING);
                wakeUp(); //!
            }
        } finally {
            lock.unlock();
        }
    }

    public class Download implements Runnable {

        private Object tag = 0;

        private String uri;
        private String filePath;
        private int size = -1;
        private int downloaded = 0;
        private DownloadStatus status = DownloadStatus.DOWNLOADING;
        private Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", 8118));
        private ApplicationSettings.UserSettings userSettings = ApplicationSettings.get(MangaApplication.getContext()).getUserSettings();

        @Nullable
        private RequestPreprocessor preprocessor;

        public Download() {
        }

        public void recycle() {
            clear();
            DownloadManager.this.recycle(this);
        }

        public String getUri() {
            return uri;
        }

        public void setUri(final String uri) {
            this.uri = uri;
        }

        public String getFilePath() {
            return filePath;
        }

        //TODO: block if download is not in pool
        public void setFilePath(final String filePath) {
            this.filePath = filePath;
        }

        public void setPreprocessor(@Nullable final RequestPreprocessor requestPreprocessor) {
            this.preprocessor = requestPreprocessor;
        }

        private void clear() {
            this.uri = null;
            this.filePath = null;
            this.size = -1;
            this.tag = null;
            this.downloaded = 0;
            this.status = DownloadStatus.DOWNLOADING;
            this.preprocessor = null;
        }

        @Override
        public void run() {
            lock.lock();
            try {
                DownloadStatus s = getStatus();
                if (s == DownloadStatus.SKIPPED) {
                    lock.lock();
                    try {
                        downloads.remove(this);
                    } finally {
                        lock.unlock();
                    }
                    stateChanged();
                    return;
                }
                if (s == DownloadStatus.PAUSED) {
                    stateChanged();
                }
                if (s != DownloadStatus.DOWNLOADING) {
                    return;
                }
            } finally {
                lock.unlock();
            }
            RandomAccessFile file = null;
            InputStream stream = null;
            try {
                // Open connection to URL.
                URL url = new URL(uri);
                HttpURLConnection connection;

                if (preprocessor != null) {
                    connection = preprocessor.process(url);
                } else {
                    if(userSettings.isOrbotProxy())
                        connection = (HttpURLConnection) url.openConnection(proxy);
                    else
                        connection = (HttpURLConnection) url.openConnection();
                }

                // Specify what portion of file to download.
                connection.setRequestProperty("Range",
                        "bytes=" + downloaded + "-");

                // Connect to server.
                connection.connect();

                // Make sure response code is in the 200 range.
                if (connection.getResponseCode() / 100 != 2) {
                    error("Can't download, response code is "
                            + connection.getResponseCode()
                            + " message is " + connection.getResponseMessage());
                }

                // Check for valid content length.
                int contentLength = connection.getContentLength();
                if (contentLength < 1) {
                    error("Not valid content length");
                }
                /* Set the size for this download if it
                hasn't been already set. */
                if (size == -1) {
                    synchronized (this) {
                        size = contentLength;
                    }
                }
                if (getStatus() == DownloadStatus.DOWNLOADING) {
                    stateChanged();
                }

                // Open file and seek to the end of it.
                file = new RandomAccessFile(filePath, "rw");
                file.seek(downloaded);

                stream = connection.getInputStream();
                byte[] buffer;
                if (size - downloaded > MAX_BUFFER_SIZE) {
                    buffer = new byte[MAX_BUFFER_SIZE];
                } else {
                    buffer = new byte[size - downloaded];
                }
                while (getStatus() == DownloadStatus.DOWNLOADING) {
                    /* Size buffer according to how much of the
                    file is left to download. */
                    if (size - downloaded < MAX_BUFFER_SIZE) {
                        buffer = new byte[size - downloaded];
                    }
                    // Read from server into buffer.
                    int read = stream.read(buffer);
                    if (read == -1) {
                        break;
                    }
                    // Write buffer to file.
                    file.write(buffer, 0, read);
                    downloaded += read;
                    if (listener != null) {
                        listener.onProgress(this, downloaded);
                    }
                }
                /* Change status to complete if this point was
                reached because downloading has finished. */
                if (getStatus() == DownloadStatus.DOWNLOADING) {
                    status = DownloadStatus.COMPLETE;
                    lock.lock();
                    try {
                        downloads.remove(this);
                    } finally {
                        lock.unlock();
                    }
                    // Close file.
                    try {
                        file.close();
                    } catch (Exception e) {
                        Log.e(TAG, "Error while closing file: " + e.getMessage());
                    }
                    stateChanged();
                }
                if (getStatus() == DownloadStatus.SKIPPED) {
                    lock.lock();
                    try {
                        downloads.remove(this);
                    } finally {
                        lock.unlock();
                    }
                    stateChanged();
                }
                if (getStatus() == DownloadStatus.PAUSED) {
                    stateChanged();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error while downloading: " + e.getClass().getName() + " - " +  e.getMessage());
                error(e.getMessage());
            } finally {
                // Close file.
                if (file != null) {
                    try {
                        file.close();
                    } catch (Exception e) {
                        Log.e(TAG, "Error while closing file: " + e.getMessage());
                    }
                }

                // Close connection to server.
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (Exception e) {
                        Log.e(TAG, "Error while closing stream: " + e.getMessage());
                    }
                }
            }
        }

        private String errorMessage = null;

        private void error(final String errorMessage) {
            setStatus(DownloadStatus.ERROR);
            this.errorMessage = errorMessage;
            stateChanged();
        }

        private void stateChanged() {
            switch (status) {
                case DOWNLOADING:
                    if (listener != null) {
                        listener.onResume(this);
                    }
                    break;
                case PAUSED:
                    if (listener != null) {
                        listener.onPause(this);
                    }
                    break;
                case COMPLETE:
                    if (listener != null) {
                        listener.onComplete(this);
                        userSettings.appendDownloadedSize(size);
                        recycle();
                    }
                    break;
                case CANCELLED:
                    if (listener != null) {
                        listener.onCancel(this);
                    }
                    break;
                case ERROR:
                    if (listener != null) {
                        listener.onError(this, errorMessage);
                    }
                    break;
                case SKIPPED:
                    if (listener != null) {
                        listener.onComplete(this);
                    }
                    break;
            }
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public int getDownloaded() {
            return downloaded;
        }

        public float getProgress() {
            return ((float) downloaded / size) * 100;
        }

        public synchronized DownloadStatus getStatus() {
            return status;
        }

        protected synchronized void setStatus(final DownloadStatus status) {
            this.status = status;
        }

        public synchronized void skip() {
            status = DownloadStatus.SKIPPED;
        }

        public synchronized void pause() {
            status = DownloadStatus.PAUSED;
        }

        public synchronized int getSize() {
            return size;
        }

        public Object getTag() {
            return tag;
        }

        public void setTag(final Object tag) {
            this.tag = tag;
        }

        public void setSize(final int size) {
            this.size = size;
        }

        public void setDownloaded(final int downloaded) {
            this.downloaded = downloaded;
        }

        public void setErrorMessage(final String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }

    public class DownloadPool implements Pool<Download> {

        private int poolMaxSize = 15;

        private Queue<Download> pool = new ArrayDeque<>(poolMaxSize);

        @Override
        public Download obtain() {
            Download download;
            if (!pool.isEmpty()) {
                download = pool.poll();
            } else {
                download = new Download();
            }
            download.clear();
            return download;
        }

        @Override
        public void retrieve(final Download object) {
            if (pool.size() < poolMaxSize) {
                pool.add(object);
            }
        }

    }

    private void recycle(final Download download) {
        pool.retrieve(download);
    }

    public interface DownloadProgressListener {

        public void onProgress(final Download download, final int progress);

        public void onPause(final Download download);

        public void onResume(final Download download);

        public void onComplete(final Download download);

        public void onCancel(final Download download);

        public void onError(final Download download, final String errorMsg);

    }

    public void setListener(final DownloadProgressListener listener) {
        this.listener = listener;
    }

    private class DownloadManagerThread extends Thread {

        private boolean isWorking;

        public DownloadManagerThread() {
            isWorking = true;
        }

        @Override
        public void run() {
            while (isWorking()) {
                Download download = null;
                lock.lock();
                try {
                    while (downloads.size() < 1) {
                        try {
                            isWake.await();
                        } catch (InterruptedException e) {
                            Log.d(TAG, "Thread is awake, checking state now");
                        }
                    }
                    download = downloads.peek();
                    while (download.getStatus() == DownloadStatus.PAUSED || download.getStatus() == DownloadStatus.ERROR) {
                        //doing nothing while download is paused or has error
                        try {
                            isWake.await();
                        } catch (InterruptedException e) {
                            Log.d(TAG, "Thread is awake, checking state now");
                        }
                    }
                    if (download.getStatus() == DownloadStatus.CANCELLED) {
                        Log.e(TAG, "Download cancelled");
                        download = downloads.remove();
                        download.recycle();
                        continue;
                    }
                } finally {
                    lock.unlock();
                }
                download.run();
                if (download.getStatus() == DownloadStatus.CANCELLED) {
                    Log.e(TAG, "Download cancelled");
                }
            }
        }

        public synchronized boolean isWorking() {
            return isWorking;
        }

        public synchronized void stopWorking() {
            this.isWorking = false;
        }

    }

    public enum DownloadStatus {
        DOWNLOADING,
        PAUSED,
        COMPLETE,
        CANCELLED,
        ERROR,
        SKIPPED
    }

}
