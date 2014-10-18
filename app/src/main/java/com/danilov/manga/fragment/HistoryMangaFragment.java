package com.danilov.manga.fragment;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import com.danilov.manga.R;
import com.danilov.manga.activity.MangaViewerActivity;
import com.danilov.manga.core.database.DatabaseAccessException;
import com.danilov.manga.core.database.DownloadedMangaDAO;
import com.danilov.manga.core.database.HistoryDAO;
import com.danilov.manga.core.model.HistoryElement;
import com.danilov.manga.core.model.LocalManga;
import com.danilov.manga.core.service.LocalImageManager;
import com.danilov.manga.core.util.Constants;
import com.danilov.manga.core.util.ServiceContainer;

import java.util.List;

/**
 * Created by Semyon Danilov on 07.10.2014.
 */
public class HistoryMangaFragment extends Fragment implements AdapterView.OnItemClickListener {

    private static final String TAG = "DownloadedMangaFragment";

    private View view;

    private LocalImageManager localImageManager = null;
    private HistoryDAO historyDAO = null;

    private int sizeOfImage;

    public static HistoryMangaFragment newInstance() {
        return new HistoryMangaFragment();
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.manga_downloaded_fragment, container, false);
        return view;
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        localImageManager = ServiceContainer.getService(LocalImageManager.class);
        historyDAO = ServiceContainer.getService(HistoryDAO.class);
        GridView gridView = (GridView) view.findViewById(R.id.grid_view);
        try {
            List<HistoryElement> history = historyDAO.getAllLocalMangaHistory();
            HistoryMangaAdapter adapter = new HistoryMangaAdapter(this.getActivity(), history);
            gridView.setAdapter(adapter);
        } catch (DatabaseAccessException e) {
            Log.e(TAG, "Failed to get downloaded manga: " + e.getMessage());
        }
        gridView.setOnItemClickListener(this);
        sizeOfImage = getActivity().getResources().getDimensionPixelSize(R.dimen.manga_list_image_height);
        super.onActivityCreated(savedInstanceState);
    }



    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
        HistoryMangaAdapter adapter = (HistoryMangaAdapter) parent.getAdapter();
        HistoryElement historyElement = adapter.getHistoryElements().get(position);
        Intent intent = new Intent(this.getActivity(), MangaViewerActivity.class);
        intent.putExtra(Constants.MANGA_PARCEL_KEY, historyElement.getManga());
        intent.putExtra(Constants.FROM_PAGE_KEY, historyElement.getPage());
        intent.putExtra(Constants.FROM_CHAPTER_KEY, historyElement.getChapter());
        startActivity(intent);
    }



    private class HistoryMangaAdapter extends ArrayAdapter<HistoryElement> {

        private List<HistoryElement> history = null;

        @Override
        public int getCount() {
            return history.size();
        }

        public HistoryMangaAdapter(final Context context, final List<HistoryElement> history) {
            super(context, 0, history);
            this.history = history;
        }

        public List<HistoryElement> getHistoryElements() {
            return history;
        }

        @Override
        public View getView(final int position, final View convertView, final ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(R.layout.history_grid_item, parent, false);
            }
            GridItemHolder holder = null;
            Object tag = view.getTag();
            if (tag instanceof GridItemHolder) {
                holder = (GridItemHolder) tag;
            }
            if (holder == null) {
                holder = new GridItemHolder();
                holder.mangaCover = (ImageView) view.findViewById(R.id.manga_cover);
                holder.mangaTitle = (TextView) view.findViewById(R.id.manga_title);
            }
            LocalManga manga = (LocalManga) history.get(position).getManga();
            holder.mangaTitle.setText(manga.getTitle());
            String mangaUri = manga.getLocalUri();
            Bitmap bitmap = localImageManager.loadBitmap(holder.mangaCover, mangaUri + "/cover", sizeOfImage);
            if (bitmap != null) {
                holder.mangaCover.setImageBitmap(bitmap);
            }
            return view;
        }

        private class GridItemHolder {

            public ImageView mangaCover;
            public TextView mangaTitle;

        }

    }

}