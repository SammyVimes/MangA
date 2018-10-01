package com.danilov.supermanga.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.danilov.supermanga.R;
import com.danilov.supermanga.activity.BaseToolbarActivity;
import com.danilov.supermanga.core.adapter.BaseAdapter;
import com.danilov.supermanga.core.model.Manga;
import com.danilov.supermanga.core.repository.RepositoryEngine;
import com.danilov.supermanga.core.service.DownloadsDumpService;
import com.danilov.supermanga.core.service.MangaDownloadService;
import com.danilov.supermanga.core.service.ServiceConnectionListener;
import com.danilov.supermanga.core.util.Constants;
import com.danilov.supermanga.core.util.Pair;
import com.danilov.supermanga.core.util.SafeHandler;
import com.danilov.supermanga.core.util.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Semyon on 15.11.2014.
 */
public class DownloadManagerFragment extends BaseFragmentNative {

    private static final String TAG = "DownloadManagerFragment";

    private MangaDownloadService service;

    private ProgressBar chaptersProgressBar;
    private ProgressBar imageProgressBar;

    private TextView title;
    private TextView chaptersProgress;
    private TextView imagesProgress;

    private ServiceConnection serviceConnection;

    private Intent serviceIntent;

    private ImageButton resumeButton;
    private ImageButton restartButton;
    private ImageButton pauseButton;
    private ImageButton skipButton;
    private ImageButton removeButton;

    private View cardWrapper;
    private TextView noActiveDownloadSign;

    private Button restoreButton;

    private ListView listView;

    private BaseToolbarActivity activity;

    private Context context;

    private MangaDownloadService.MangaDownloadRequest currentRequest;

    public static DownloadManagerFragment newInstance() {
        return new DownloadManagerFragment();
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.manga_downloadmanager_fragment, container, false);
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        activity = (BaseToolbarActivity) getActivity();
        context = activity;
        cardWrapper = findViewById(R.id.card_wrapper);
        title = findViewById(R.id.title);
        noActiveDownloadSign = findViewById(R.id.no_downloads);
        chaptersProgressBar = findViewById(R.id.chaptersProgressBar);
        imageProgressBar = findViewById(R.id.imageProgressBar);
        chaptersProgress = findViewById(R.id.chaptersProgress);
        imagesProgress = findViewById(R.id.imageProgress);
        listView = findViewById(R.id.download_queue);

        handler = new ServiceMessagesHandler();

        resumeButton = findViewById(R.id.resume_btn);
        pauseButton = findViewById(R.id.pause_btn);
        restartButton = findViewById(R.id.restart_btn);
        skipButton = findViewById(R.id.skip_btn);
        removeButton = findViewById(R.id.remove);

        removeButton.setOnClickListener(view1 -> {
            if (currentRequest != null && service != null) {
                service.deleteCurRequest(currentRequest);
            }
        });

        restartButton.setOnClickListener(v -> {
            showPauseBtn();
            service.restartDownload();
        });
        pauseButton.setOnClickListener(view1 -> service.pause());
        resumeButton.setOnClickListener(view1 -> service.resume());
        skipButton.setOnClickListener(v -> service.skipImage());
        restoreButton = findViewById(R.id.restore);
        restoreButton.setOnClickListener(view1 -> {
            if (service != null) {
                //TODO: show breadcrumb
                DownloadsDumpService downloadsDumpService = new DownloadsDumpService();
                downloadsDumpService.unDump(service);
            }
        });
        showPauseBtn();
    }

    @Override
    public void onResume() {
        super.onResume();
        noActiveDownloadSign.setVisibility(View.VISIBLE);
        cardWrapper.setVisibility(View.GONE);
        serviceConnection = new MangaDownloadService.MDownloadServiceConnection(new DownloadServiceConnectionListener());
        serviceIntent = new Intent(activity, MangaDownloadService.class);
        activity.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        Log.d(TAG, "Connecting to service");
    }

    private class ServiceMessagesHandler extends SafeHandler {

        @Override
        public void handleMessage(final Message message) {
            int what = message.what;
            switch (what) {
                case MangaDownloadService.PROGRESS:
                    onProgress(message);
                    break;
                case MangaDownloadService.PAUSE:
                    onPause(message);
                    break;
                case MangaDownloadService.RESUME:
                    onResume(message);
                    break;
                case MangaDownloadService.REQUEST_COMPLETE:
                    onRequestComplete(message);
                    break;
                case MangaDownloadService.CHAPTER_COMPLETE:
                    onChapterComplete(message);
                    break;
                case MangaDownloadService.CANCEL:
                    onCancel(message);
                    break;
                case MangaDownloadService.ERROR:
                    onError(message);
                    break;
                case MangaDownloadService.STATUS:
                    onStatus(message);
                    break;
            }
        }
    }

    private void onProgress(final Message message) {
        int progress = message.arg1;
        int max = message.arg2;
        imageProgressBar.setMax(max);
        imageProgressBar.setProgress(progress);
    }

    private void onPause(final Message message) {
        showResumeBtn();
    }

    private void onResume(final Message message) {
        showPauseBtn();
        int max = message.arg1;
        imageProgressBar.setMax(max);
        imageProgressBar.setProgress(0);
        Pair pair = (Pair) message.obj;
        int currentImage = (Integer) pair.first + 1; //what image is processed now starting with 1 not zero
        String progressText = currentImage + "/" + pair.second;
        imagesProgress.setText(progressText);
        pair.retrieve();
    }

    private void onRequestComplete(final Message message) {
        imageProgressBar.setMax(1);
        imageProgressBar.setProgress(1);
        chaptersProgressBar.setMax(1);
        chaptersProgressBar.setProgress(1);
        cardWrapper.setVisibility(View.GONE);
        if (listView.getCount() <= 0) {
            noActiveDownloadSign.setVisibility(View.VISIBLE);
        }
    }

    private void onChapterComplete(final Message message) {
        int currentChapter = message.arg1;
        int quantity = message.arg2;
        MangaDownloadService.MangaDownloadRequest request = (MangaDownloadService.MangaDownloadRequest) message.obj;
        chaptersProgressBar.setProgress(currentChapter);
        String progressText = ++currentChapter + "/" + quantity;
        chaptersProgress.setText(progressText);
    }

    private void onCancel(final Message message) {

    }

    private void onError(final Message message) {
        showRestartBtn();
        Pair p = (Pair) message.obj;
        Utils.showToast(context, String.valueOf(p.second));
    }

    private void onStatus(final Message message) {
        cardWrapper.setVisibility(View.VISIBLE);
        noActiveDownloadSign.setVisibility(View.GONE);
        Pair p = (Pair) message.obj;
        MangaDownloadService.MangaDownloadRequest request = (MangaDownloadService.MangaDownloadRequest) p.first;
        currentRequest = request;
        List<MangaDownloadService.MangaDownloadRequest> requests = (List<MangaDownloadService.MangaDownloadRequest>) p.second;

        RequestsAdapter adapter = new RequestsAdapter(context, android.R.layout.simple_list_item_1, requests);
        listView.setAdapter(adapter);

        int currImage = message.arg1 + 1; //what image is processed now starting with 1 not zero
        int currImageQuantity = message.arg2;
        String progressText = currImage + "/" + currImageQuantity;
        imagesProgress.setText(progressText);
        int currentChapter = request.getCurrentChapterInList();
        int quantity = request.quantity;
        title.setText(request.manga.getTitle());

        if (request.isHasError()) {
            showRestartBtn();
        }
        if (request.isPaused()) {
            showResumeBtn();
        }


        chaptersProgressBar.setMax(quantity);
        chaptersProgressBar.setProgress(currentChapter);
        progressText = ++currentChapter + "/" + quantity;
        chaptersProgress.setText(progressText);



    }

    @Override
    public void onPause() {
        activity.unbindService(serviceConnection);
        if (service != null) {
            service.removeObserver(handler);
        }
        super.onPause();
    }

    private class DownloadServiceConnectionListener implements ServiceConnectionListener<MangaDownloadService> {

        @Override
        public void onServiceConnected(final MangaDownloadService service) {
            DownloadManagerFragment.this.service = service;
            service.addObserver(handler);
            activity.startService(serviceIntent);

            Intent i = activity.getIntent();
            final Manga manga = i.getParcelableExtra(Constants.MANGA_PARCEL_KEY);
            final ArrayList<Integer> selectedChapters = i.getIntegerArrayListExtra(Constants.SELECTED_CHAPTERS_KEY);
            if (selectedChapters != null) {
                startDownload(manga, selectedChapters);
                i.removeExtra(Constants.SELECTED_CHAPTERS_KEY);
            }

        }

        @Override
        public void onServiceDisconnected(final MangaDownloadService service) {
            service.removeObserver(handler);
        }

    }

    private void startDownload(final Manga manga, final ArrayList<Integer> selectedChapters) {
        Thread t = new Thread() {

            @Override
            public void run() {
                RepositoryEngine engine = manga.getRepository().getEngine();
                try {
                    engine.queryForChapters(manga);
                    service.addDownload(manga, selectedChapters);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        };
        t.start();
    }

    private void showResumeBtn() {
        resumeButton.setVisibility(View.VISIBLE);
        removeButton.setVisibility(View.VISIBLE);
        pauseButton.setVisibility(View.INVISIBLE);
        restartButton.setVisibility(View.INVISIBLE);
        skipButton.setVisibility(View.INVISIBLE);
    }

    private void showPauseBtn() {
        pauseButton.setVisibility(View.VISIBLE);
        removeButton.setVisibility(View.VISIBLE);
        resumeButton.setVisibility(View.INVISIBLE);
        restartButton.setVisibility(View.INVISIBLE);
        skipButton.setVisibility(View.INVISIBLE);
    }

    private void showRestartBtn() {
        restartButton.setVisibility(View.VISIBLE);
        skipButton.setVisibility(View.VISIBLE);
        resumeButton.setVisibility(View.INVISIBLE);
        pauseButton.setVisibility(View.INVISIBLE);
        removeButton.setVisibility(View.INVISIBLE);
    }

    private class RequestsAdapter extends BaseAdapter<Holder, MangaDownloadService.MangaDownloadRequest> {

        private List<MangaDownloadService.MangaDownloadRequest> requests = null;

        public RequestsAdapter(final Context context, final int resource, final List<MangaDownloadService.MangaDownloadRequest> objects) {
            super(context, resource, objects);
            requests = objects;
        }

        @Override
        public void onBindViewHolder(final Holder holder, final int position) {
            final MangaDownloadService.MangaDownloadRequest request = requests.get(position);
            holder.title.setText(request.getManga().getTitle());
            holder.removeButton.setOnClickListener(view1 -> {
                if (service != null) {
                    service.deleteSomeRequest(request);
                }
            });
        }

        @Override
        public int getCount() {
            if (requests == null) {
                return 0;
            }
            return requests.size();
        }

        @Override
        public Holder onCreateViewHolder(final ViewGroup viewGroup, final int position) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.downloading_item, viewGroup, false);
            return new Holder(v);
        }

    }

    private static class Holder extends BaseAdapter.BaseHolder {

        public ImageButton removeButton;
        public TextView title;

        protected Holder(final View view) {
            super(view);
            removeButton = findViewById(R.id.remove_btn);
            title = findViewById(R.id.title);
        }

    }

}
