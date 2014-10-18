package com.danilov.manga.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.danilov.manga.R;
import com.danilov.manga.core.util.DrawerStub;
import com.danilov.manga.fragment.DownloadedMangaFragment;
import com.danilov.manga.fragment.HistoryMangaFragment;
import com.danilov.manga.fragment.MainFragment;
import com.danilov.manga.fragment.RepositoryPickerFragment;

/**
 * Created by Semyon Danilov on 07.10.2014.
 */
public class MainActivity extends ActionBarActivity {

    private View drawerLayout;

    private DrawerLayout castedDrawerLayout;

    private ActionBarDrawerToggle drawerToggle;

    private ListView drawerList;

    private boolean isOnMainFragment = false;

    private boolean isLargeLandscape = false;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.manga_main_activity);
        drawerLayout = findViewById(R.id.drawer_layout);
        isLargeLandscape = findViewById(R.id.is_large) != null; //dealing with landscape pads
        drawerList = (ListView) findViewById(R.id.left_drawer);

        // Set the adapter for the list view
        drawerList.setAdapter(new DrawerListAdapter(this, R.layout.drawer_menu_item, DrawerMenuItem.values()));
        drawerList.setOnItemClickListener(new DrawerItemClickListener());

        if (!isLargeLandscape) {
            castedDrawerLayout = (DrawerLayout) drawerLayout;
            drawerToggle = new ActionBarDrawerToggle(this, castedDrawerLayout,
                    R.drawable.ic_navigation_drawer, R.string.sv_drawer_open, R.string.sv_drawer_close) {

                /** Called when a drawer has settled in a completely closed state. */
                public void onDrawerClosed(View view) {
                    super.onDrawerClosed(view);
                }

                /** Called when a drawer has settled in a completely open state. */
                public void onDrawerOpened(View drawerView) {
                    super.onDrawerOpened(drawerView);
                }

            };
            // Set the drawer toggle as the DrawerListener
            castedDrawerLayout.setDrawerListener(drawerToggle);

        } else {
            castedDrawerLayout = new DrawerStub(this);
        }
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(false);
        actionBar.setHomeButtonEnabled(true);
        showMainFragment();
        syncToggle();
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                if (castedDrawerLayout.isDrawerOpen(drawerList)) {
                    castedDrawerLayout.closeDrawer(drawerList);
                } else {
                    castedDrawerLayout.openDrawer(drawerList);
                }
                if (drawerToggle != null) {
                    drawerToggle.syncState();
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (drawerToggle != null) {
            drawerToggle.syncState();
        }
    }

    private enum DrawerMenuItem {

        SEARCH(R.drawable.ic_action_search, R.string.menu_search),
        HISTORY(R.drawable.ic_action_time, R.string.menu_history),
        FAVORITE(R.drawable.ic_action_important, R.string.menu_favorite),
        LOCAL(R.drawable.ic_action_download, R.string.menu_local),
        DOWNLOAD_MANAGER(R.drawable.ic_action_settings, R.string.menu_download),
        SETTINGS(R.drawable.ic_action_settings, R.string.menu_settings);

        private int iconId;

        private int stringId;

        private DrawerMenuItem(final int iconId, final int stringId) {
            this.iconId = iconId;
            this.stringId = stringId;
        }

        public int getIconId() {
            return iconId;
        }

        public int getStringId() {
            return stringId;
        }

    }

    private class DrawerItemClickListener implements AdapterView.OnItemClickListener {

        @Override
        public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
            DrawerMenuItem item = DrawerMenuItem.values()[position];
            view.setSelected(true);
            Intent intent = null;
            switch (item) {
                case SEARCH:
                    showRepositoryPickerFragment();
                    break;
                case HISTORY:
                    showHistoryFragment();
                    break;
                case FAVORITE:
                    break;
                case LOCAL:
                    showDownloadedMangaFragment();
                    break;
                case DOWNLOAD_MANAGER:
                    break;
                case SETTINGS:
                    break;
            }
            syncToggle();
            closeDrawer();
        }

    }

    private void syncToggle() {
        if (drawerToggle != null) {
            drawerToggle.syncState();
        }
        ActionBar actionBar = getSupportActionBar();
        if (!isOnMainFragment) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        } else {
            if (drawerToggle != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                drawerToggle.setDrawerIndicatorEnabled(true);
            } else {
                actionBar.setDisplayHomeAsUpEnabled(false);
            }
        }
    }

    private void closeDrawer() {
        castedDrawerLayout.closeDrawer(drawerList);
        if (drawerToggle != null) {
            drawerToggle.syncState();
        }
    }

    private void showRepositoryPickerFragment() {
        RepositoryPickerFragment fragment = RepositoryPickerFragment.newInstance();
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.content_frame, fragment)
                .commit();
        if (drawerToggle != null) {
            drawerToggle.setDrawerIndicatorEnabled(false);
        }
        isOnMainFragment = false;
    }

    private void showDownloadedMangaFragment() {
        Fragment fragment = DownloadedMangaFragment.newInstance();
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.content_frame, fragment)
                .commit();
        if (drawerToggle != null) {
            drawerToggle.setDrawerIndicatorEnabled(false);
        }
        isOnMainFragment = false;
    }

    private void showHistoryFragment() {
        Fragment fragment = HistoryMangaFragment.newInstance();
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.content_frame, fragment)
                .commit();
        if (drawerToggle != null) {
            drawerToggle.setDrawerIndicatorEnabled(false);
        }
        isOnMainFragment = false;
    }

    private void showMainFragment() {
        Fragment fragment = MainFragment.newInstance();
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.content_frame, fragment)
                .commit();
        if (drawerToggle != null) {
            drawerToggle.setDrawerIndicatorEnabled(true);
        }
        isOnMainFragment = true;
    }

    private class DrawerListAdapter extends ArrayAdapter<DrawerMenuItem> {

        private DrawerMenuItem[] objects;
        private int resourceId;
        private Context context;

        @Override
        public int getCount() {
            return objects.length;
        }

        public DrawerListAdapter(final Context context, final int resource, final DrawerMenuItem[] objects) {
            super(context, resource, objects);
            this.context = context;
            this.objects = objects;
            this.resourceId = resource;
        }

        @Override
        public View getView(final int position, final View convertView, final ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(resourceId, parent, false); //attaching to parent == (false), because it attaching later by android
            }
            Object tag = view.getTag();
            ViewHolder holder = null;
            TextView text;
            ImageView icon;
            if (tag != null) {
                holder = (ViewHolder) tag;
                text = holder.text;
                icon = holder.icon;
            } else {
                holder = new ViewHolder();
                text = (TextView) view.findViewById(R.id.text);
                icon = (ImageView) view.findViewById(R.id.icon);
                holder.text = text;
                holder.icon = icon;
            }
            DrawerMenuItem item = objects[position];
            text.setText(context.getString(item.getStringId()));
            icon.setImageResource(item.getIconId());
            return view;
        }

        private class ViewHolder {
            public TextView text;
            public ImageView icon;
        }

    }

}