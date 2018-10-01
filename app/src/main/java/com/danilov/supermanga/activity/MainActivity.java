package com.danilov.supermanga.activity;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.test.UiThreadTest;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.danilov.supermanga.R;
import com.danilov.supermanga.core.application.ApplicationSettings;
import com.danilov.supermanga.core.database.DatabaseAccessException;
import com.danilov.supermanga.core.database.UpdatesDAO;
import com.danilov.supermanga.core.dialog.CustomDialogFragment;
import com.danilov.supermanga.core.model.Manga;
import com.danilov.supermanga.core.model.MangaChapter;
import com.danilov.supermanga.core.theme.ThemeUtils;
import com.danilov.supermanga.core.util.Constants;
import com.danilov.supermanga.core.util.DrawerStub;
import com.danilov.supermanga.core.util.ServiceContainer;
import com.danilov.supermanga.core.util.Utils;
import com.danilov.supermanga.fragment.AddJSRepositoryFragment;
import com.danilov.supermanga.fragment.AddLocalMangaFragment;
import com.danilov.supermanga.fragment.BaseFragmentNative;
import com.danilov.supermanga.fragment.ChapterManagementFragment;
import com.danilov.supermanga.fragment.DownloadManagerFragment;
import com.danilov.supermanga.fragment.DownloadedMangaFragment;
import com.danilov.supermanga.fragment.FavoritesFragment;
import com.danilov.supermanga.fragment.HistoryMangaFragment;
import com.danilov.supermanga.fragment.MainFragment;
import com.danilov.supermanga.fragment.RepositoryPickerFragment;
import com.danilov.supermanga.fragment.SettingsFragment;
import com.danilov.supermanga.fragment.TrackingFragment;
import com.github.amlcurran.showcaseview.OnShowcaseEventListener;
import com.github.amlcurran.showcaseview.ShowcaseView;
import com.github.amlcurran.showcaseview.targets.ViewTarget;

/**
 * Created by Semyon Danilov on 07.10.2014.
 */
public class MainActivity extends BaseToolbarActivity implements FragmentManager.OnBackStackChangedListener, ChapterManagementFragment.Callback {

    private static final String FIRST_LAUNCH = "FIRST_LAUNCH";

    public static final String PAGE = "PAGE";

    private UpdatesDAO updatesDAO = null;

    private View drawerLayout;

    private View drawerMenu;

    private TextView userNameTextView;

    private DrawerLayout castedDrawerLayout;

    private ActionBarDrawerToggle drawerToggle;

    private ListView drawerList;

    private boolean isLargeLandscape = false;

    private int updatesQuantity = 0;

    private DrawerListAdapter adapter;

    private boolean firstLaunch = true;

    private BaseFragmentNative currentFragment = null;


    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        setContentView(R.layout.manga_main_activity);

        //используем лиснер бэкстека, так как
        //нужно синхронизировать состояние стрелочки (дровера)
        //а изменение бэкстека -- асинхронное
        getFragmentManager().addOnBackStackChangedListener(this);

        View profileOverlayButton = findViewWithId(R.id.profile_overlay_button);
        profileOverlayButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
            startActivity(intent);
        });

        updatesDAO = ServiceContainer.getService(UpdatesDAO.class);
        drawerLayout = findViewById(R.id.drawer_layout);
        userNameTextView = findViewWithId(R.id.user_name);
        isLargeLandscape = findViewById(R.id.is_large) != null; //dealing with landscape pads
        drawerList = (ListView) findViewById(R.id.left_drawer);
        drawerMenu = findViewWithId(R.id.drawer_menu);
        // Set the adapter for the list view
        adapter = new DrawerListAdapter(this, R.layout.drawer_menu_item, MainMenuItem.values());
        drawerList.setAdapter(adapter);
        drawerList.setOnItemClickListener(new DrawerItemClickListener());

        if (!isLargeLandscape) {
            castedDrawerLayout = (DrawerLayout) drawerLayout;
            drawerToggle = new ActionBarDrawerToggle(this, castedDrawerLayout, R.string.sv_drawer_open, R.string.sv_drawer_close) {

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
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayShowHomeEnabled(true);
        getToolbar().setLogo(null);

        final ApplicationSettings applicationSettings = ApplicationSettings.get(this.getApplicationContext());
        firstLaunch = applicationSettings.isFirstLaunch();
        boolean tutorialMenuPassed = applicationSettings.isTutorialMenuPassed();
        //TODO: check if we need to display main fragment or we need to show restored
        if (firstLaunch) {
            if (savedInstanceState == null) {
                showMainFragment();
            }
        } else {
            if (savedInstanceState == null) {
                String page = getIntent().getStringExtra(PAGE);
                MainMenuItem item = MainMenuItem.UPDATES;
                if (page == null || "".equals(page)) {
                    item = MainMenuItem.valueOf(applicationSettings.getMainMenuItem());
                } else {
                    item = MainMenuItem.valueOf(page);
                }
                showPage(item);
            }
        }
        applicationSettings.setFirstLaunch(false);
        applicationSettings.update(getApplicationContext());
        syncToggle();
        updateQuantity();

        ApplicationSettings.UserSettings userSettings = applicationSettings.getUserSettings();
        String userName = userSettings.getUserName();
        if (userName != null && !userName.isEmpty()) {
            userNameTextView.setText(userName);
            userNameTextView.setBackgroundColor(getResources().getColor(android.R.color.transparent));
        }

        if (!tutorialMenuPassed) {
            View fakeMenuOpenBtn = findViewById(R.id.fake_home);
            if (fakeMenuOpenBtn != null) {
                ShowcaseView showcaseView = new ShowcaseView.Builder(this)
                        .setTarget(new ViewTarget(findViewById(R.id.fake_home)))
                        .setContentTitle(getString(R.string.tutorial_menu_title))
                        .setStyle(R.style.ShowcaseView_Dark)
                        .setContentText(getString(R.string.tutorial_menu_text))
                        .hideOnTouchOutside()
                        .build();
                showcaseView.setButtonText(getString(R.string.tutorial_close));
                showcaseView.setButtonPosition(Utils.getRightParam(this, getResources()));
                showcaseView.setOnShowcaseEventListener(new OnShowcaseEventListener() {
                    @Override
                    public void onShowcaseViewHide(final ShowcaseView showcaseView) {
                        View v = (View) showcaseView.getParent();
                        applicationSettings.setTutorialMenuPassed(true);
                        applicationSettings.setFirstLaunch(false);
                        applicationSettings.update(getApplicationContext());
                    }

                    @Override
                    public void onShowcaseViewDidHide(final ShowcaseView showcaseView) {

                    }

                    @Override
                    public void onShowcaseViewShow(final ShowcaseView showcaseView) {

                    }
                });
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    private CustomDialogFragment dialogFragment = null;

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        outState.putBoolean(FIRST_LAUNCH, firstLaunch);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull final Bundle savedInstanceState) {
        firstLaunch = savedInstanceState.getBoolean(FIRST_LAUNCH);
        super.onRestoreInstanceState(savedInstanceState);
    }

    @UiThreadTest
    private void testShowHeadsUp() {
//        HeadsUpNotification headsUpNotification = new HeadsUpNotification(getApplicationContext(), 1, R.layout.test_headsup_notification);
//        headsUpNotification.show();
    }

    public void updateQuantity() {
        new Thread() {
            @Override
            public void run() {
                try {
                    updatesQuantity = updatesDAO.getUpdatesQuantity();
                } catch (DatabaseAccessException e) {
                    e.printStackTrace();
                }
                runOnUiThread(adapter::notifyDataSetChanged);
            }
        }.start();
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                if (castedDrawerLayout.isDrawerOpen(drawerMenu)) {
                    castedDrawerLayout.closeDrawer(drawerMenu);
                } else {
                    currentFragment = (BaseFragmentNative) getFragmentManager().findFragmentById(R.id.content_frame);
                    if (currentFragment != null && currentFragment.onBackPressed()) {
                        return true;
                    }
                    if (getFragmentManager().getBackStackEntryCount() > 1) {
                        getFragmentManager().popBackStack();
                        return true;
                    }
                    castedDrawerLayout.openDrawer(drawerMenu);
                }
                if (drawerToggle != null) {
                    drawerToggle.syncState();
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackStackChanged() {
        syncToggle();

        BaseFragmentNative fragment = (BaseFragmentNative) getFragmentManager().findFragmentById(R.id.content_frame);
        int titleId = R.string.app_name;
        if (fragment instanceof MainFragment) {
            titleId = R.string.menu_updates;
        } else if (fragment instanceof HistoryMangaFragment) {
            titleId = R.string.menu_history;
        } else if (fragment instanceof DownloadManagerFragment) {
            titleId = R.string.menu_download;
        } else if (fragment instanceof FavoritesFragment) {
            titleId = R.string.menu_love;
        } else if (fragment instanceof DownloadedMangaFragment) {
            titleId = R.string.menu_local;
        } else if (fragment instanceof SettingsFragment) {
            titleId = R.string.menu_settings;
        } else if (fragment instanceof TrackingFragment) {
            titleId = R.string.sv_tracking;
        } else if (fragment instanceof RepositoryPickerFragment) {
            titleId = R.string.menu_search;
        }
        //получаем название вкладки
        String itemName = getString(titleId);
        //пишем название в тулбар
        ActionBar supportActionBar = getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setTitle(itemName);
        }
    }

    public enum MainMenuItem {

        UPDATES(R.attr.ic_action_new, R.string.menu_updates),
        SEARCH(R.attr.ic_action_search_black, R.string.menu_search),
        HISTORY(R.attr.ic_action_time, R.string.menu_history),
        FAVORITE(R.drawable.button_love_checked_icon, R.string.menu_love),
        LOCAL(R.attr.ic_action_downloads, R.string.menu_local),
        DOWNLOAD_MANAGER(R.attr.ic_download_manager, R.string.menu_download),
        SETTINGS(R.attr.ic_action_settings, R.string.menu_settings);

        private int iconId;

        private int stringId;

        private boolean isSelected = false;

        MainMenuItem(final int iconId, final int stringId) {
            this.iconId = iconId;
            this.stringId = stringId;
        }

        public void setSelected(final boolean isSelected) {
            this.isSelected = isSelected;
        }

        public boolean isSelected() {
            return isSelected;
        }

        public int getIconId() {
            return iconId;
        }

        public int getStringId() {
            return stringId;
        }

    }

    private class DrawerItemClickListener implements AdapterView.OnItemClickListener {

        private MainMenuItem prevSelected = null;

        @Override
        public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
            if (prevSelected != null) {
                prevSelected.setSelected(false);
            }
            MainMenuItem item = MainMenuItem.values()[position];
            view.setSelected(true);
            item.setSelected(true);
            prevSelected = item;
            showPage(item);
            syncToggle();
            closeDrawer();
        }

    }

    private void showPage(final MainMenuItem item) {
        switch (item) {
            case UPDATES:
                showMainFragment();
                break;
            case SEARCH:
                showRepositoryPickerFragment();
                break;
            case HISTORY:
                showHistoryFragment();
                break;
            case FAVORITE:
                showFavoriteMangaFragment();
                break;
            case LOCAL:
                showDownloadedMangaFragment();
                break;
            case DOWNLOAD_MANAGER:
                showDownloadManagerFragment();
                break;
            case SETTINGS:
                showSettingsFragment();
                break;
        }
    }

    private void syncToggle() {
        boolean isOnMainFragment = !(getFragmentManager().getBackStackEntryCount() > 1);

        if (drawerToggle != null) {
            drawerToggle.syncState();
        }
        ActionBar actionBar = getSupportActionBar();
        if (drawerToggle != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            drawerToggle.setDrawerIndicatorEnabled(isOnMainFragment);
        } else {
            actionBar.setDisplayHomeAsUpEnabled(false);
        }
    }

    private void closeDrawer() {
        castedDrawerLayout.closeDrawer(drawerMenu);
        if (drawerToggle != null) {
            drawerToggle.syncState();
        }
    }

    @Override
    public void onBackPressed() {
        if (castedDrawerLayout.isDrawerOpen(drawerMenu)) {
            castedDrawerLayout.closeDrawer(drawerMenu);
            if (drawerToggle != null) {
                drawerToggle.syncState();
            }
        } else {
            currentFragment = (BaseFragmentNative) getFragmentManager().findFragmentById(R.id.content_frame);
            if (currentFragment != null && currentFragment.onBackPressed()) {
                return;
            }
            if (getFragmentManager().getBackStackEntryCount() > 1) {
                getFragmentManager().popBackStack();
                return;
            }
            super.onBackPressed();
        }
    }

    public void showRepositoryPickerFragment() {
        currentFragment = RepositoryPickerFragment.newInstance();
        showFragment(currentFragment);
    }

    public void showDownloadedMangaFragment() {
        currentFragment = DownloadedMangaFragment.newInstance();
        showFragment(currentFragment);
    }

    private void showFavoriteMangaFragment() {
        currentFragment = FavoritesFragment.newInstance();
        showFragment(currentFragment);
    }

    private void showDownloadManagerFragment() {
        currentFragment = DownloadManagerFragment.newInstance();
        showFragment(currentFragment);
    }

    private void showSettingsFragment() {
        currentFragment = SettingsFragment.newInstance();
        showFragment(currentFragment);
    }

    private void showHistoryFragment() {
        currentFragment = HistoryMangaFragment.newInstance();
        showFragment(currentFragment);
    }

    public void showAddLocalMangaFragment() {
        currentFragment = AddLocalMangaFragment.newInstance();
        showFragment(currentFragment);
    }

    public void showChaptersFragment(final Manga manga) {
        currentFragment = ChapterManagementFragment.newInstance(manga, false);
        showFragment(currentFragment);
    }

    public void showAddJSRepositoryFragment() {
        currentFragment = AddJSRepositoryFragment.newInstance();
        showFragment(currentFragment);
    }

    public void showTrackingFragment() {
        currentFragment = TrackingFragment.newInstance();
        showFragment(currentFragment);
    }

    public void showMainFragment() {
        currentFragment = MainFragment.newInstance(firstLaunch);
        showFragment(currentFragment);
    }

    private void showFragment(final Fragment fragment) {
        FragmentManager fragmentManager = getFragmentManager();

        String transactionName = fragment.getClass().toString();
        for (int i = 0; i < fragmentManager.getBackStackEntryCount(); i++) {
            FragmentManager.BackStackEntry backStackEntryAt = fragmentManager.getBackStackEntryAt(i);
            String name = backStackEntryAt.getName();
            if (name != null && name.equals(transactionName)) {
                fragmentManager.popBackStack(transactionName, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                break;
            }
        }

        fragmentManager.beginTransaction()
                .replace(R.id.content_frame, fragment)
                .setCustomAnimations(
                        android.R.animator.fade_in,
                        android.R.animator.fade_out,
                        android.R.animator.fade_in,
                        android.R.animator.fade_out)
                .addToBackStack(transactionName)
                .commit();
    }

    public void changeUpdatesQuantity(final int updatesQuantity) {
        this.updatesQuantity = updatesQuantity;
        adapter.notifyDataSetChanged();
    }

    private class DrawerListAdapter extends ArrayAdapter<MainMenuItem> {

        private MainMenuItem[] objects;
        private int resourceId;
        private Context context;

        @Override
        public int getCount() {
            return objects.length;
        }

        public DrawerListAdapter(final Context context, final int resource, final MainMenuItem[] objects) {
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
            MainMenuItem item = objects[position];
            if (item.isSelected()) {
                view.setSelected(true);
            } else {
                view.setSelected(false);
            }
            if (item == MainMenuItem.UPDATES) {
                TextView quantityNew = (TextView) view.findViewById(R.id.quantity_new);
                quantityNew.setVisibility(View.VISIBLE);
                quantityNew.setText(updatesQuantity + "");
            } else {
                TextView quantityNew = (TextView) view.findViewById(R.id.quantity_new);
                quantityNew.setVisibility(View.INVISIBLE);
            }
            text.setText(context.getString(item.getStringId()));
            if (item == MainMenuItem.FAVORITE) {
                icon.setImageResource(item.getIconId());
            } else {
                icon.setImageDrawable(ThemeUtils.getReferencedDrawable(item.getIconId(), MainActivity.this));
            }
            return view;
        }

        private class ViewHolder {
            public TextView text;
            public ImageView icon;
        }

    }

    @Override
    public void onChapterSelected(final Manga manga, final MangaChapter chapter, final boolean isOnline) {
        Intent intent = new Intent(this, MangaViewerActivity.class);
        intent.putExtra(Constants.FROM_CHAPTER_KEY, chapter.getNumber());
        intent.putExtra(Constants.MANGA_PARCEL_KEY, manga);
        intent.putExtra(Constants.SHOW_ONLINE, isOnline);
        startActivity(intent);
    }

}