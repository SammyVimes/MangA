package com.danilov.supermanga.activity;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.widget.SwitchCompat;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.danilov.supermanga.R;
import com.danilov.supermanga.core.application.ApplicationSettings;
import com.danilov.supermanga.core.application.MangaApplication;
import com.danilov.supermanga.core.dialog.CustomDialog;
import com.danilov.supermanga.core.util.Constants;
import com.danilov.supermanga.core.util.Utils;
import com.danilov.supermanga.core.view.UnderToolbarScrollView;
import com.danilov.supermanga.core.view.ViewV16;
import com.software.shell.fab.ActionButton;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * Created by Semyon on 28.08.2015.
 */
public class ProfileActivity extends BaseToolbarActivity {

    @Bind(R.id.scroll_view)
    public UnderToolbarScrollView scrollView;

    @Bind(R.id.take_photo)
    public ActionButton takePhoto;

    @Bind(R.id.fake_bar)
    public ViewGroup fakeBar;

    @Bind(R.id.fake_bar_inner)
    public ViewGroup fakeBarInner;

    @Bind(R.id.user_name)
    public TextView userNameTextView;

    @Bind(R.id.user_name_card)
    public View userNameCard;

    @Bind(R.id.email_card)
    public View emailCard;

    @Bind(R.id.always_show_buttons_card)
    public View alwaysShowButtonsCard;

    @Bind(R.id.go_to_cloud)
    public View goToCloud;

    @Bind(R.id.download_path_card)
    public View downloadPathCard;

    @Bind(R.id.user_name_small)
    public TextView userNameSmall;

    @Bind(R.id.email)
    public TextView email;

    @Bind(R.id.time_read)
    public TextView timeRead;

    @Bind(R.id.download_path)
    public TextView downloadPath;

    @Bind(R.id.mangas_complete)
    public TextView mangasComplete;

    @Bind(R.id.megabytes_downloaded)
    public TextView megabytesDownloaded;

    @Bind(R.id.app_version)
    public TextView appVersion;

    @Bind(R.id.show_btns_switch)
    public SwitchCompat showBtnsSwitch;

    @Bind(R.id.orbotproxy_switch)
    public SwitchCompat orbotProxySwitch;
    @Bind(R.id.use_orbot_proxy)
    public View useOrbotProxy;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_profile);
        ButterKnife.bind(this);

        final View rootView = getWindow().getDecorView().findViewById(android.R.id.content);

        rootView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                final ViewV16 takePhotoView = ViewV16.wrap(takePhoto);
                takePhotoView.setPivotX(takePhoto.getWidth() / 2);
                takePhotoView.setPivotY(takePhoto.getHeight() / 2);
                scrollView.setListener(new UnderToolbarScrollView.UnderToolbarScrollListener() {

                    final int USER_NAME_MARGIN_BOTTOM = Utils.dpToPx(16);
                    final int MARGIN_TOP = Utils.dpToPx(58);
                    final int MARGIN_LEFT = Utils.dpToPx(48);
                    final int MAX_HEIGHT = fakeBar.getHeight();
                    final int MIN_HEIGHT = Utils.dpToPx(58);
                    final int EXTRA_HEIGHT = MAX_HEIGHT - MIN_HEIGHT;

                    @Override
                    public int onScroll(final int x, final int y) {

                        int height = Math.max(MIN_HEIGHT, MAX_HEIGHT - y);
                        height = Math.min(MAX_HEIGHT, height);

                        RelativeLayout.LayoutParams fakeBarParameters = (RelativeLayout.LayoutParams) fakeBar.getLayoutParams();
                        fakeBarParameters.height = height;
                        fakeBar.setLayoutParams(fakeBarParameters);


                        int curExtra = height - MIN_HEIGHT;
                        float percentage = (float) curExtra / EXTRA_HEIGHT;

                        takePhotoView.setScaleX(percentage);
                        takePhotoView.setScaleY(percentage);
                        int marginTop = (int) (percentage * MARGIN_TOP);
                        int marginLeft = (int) ((1 - percentage) * MARGIN_LEFT);
                        RelativeLayout.LayoutParams fakeBarInnerParameters = (RelativeLayout.LayoutParams) fakeBarInner.getLayoutParams();
                        fakeBarInnerParameters.topMargin = marginTop;
                        fakeBarInnerParameters.leftMargin = marginLeft;
                        fakeBarInner.setLayoutParams(fakeBarInnerParameters);


                        RelativeLayout.LayoutParams userNameParams = (RelativeLayout.LayoutParams) userNameTextView.getLayoutParams();
                        userNameParams.bottomMargin = (int) (percentage * USER_NAME_MARGIN_BOTTOM);
                        userNameTextView.setLayoutParams(userNameParams);

                        return 0;
                    }
                });
                rootView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
            }
        });
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        init();
    }


    private void init() {
        final ApplicationSettings.UserSettings userSettings = ApplicationSettings.get(this).getUserSettings();

        final String userNameString = userSettings.getUserName();
        final String emailString = userSettings.getEmail();
        long timeReadLong = userSettings.getTimeRead();
        final String downloadPathString = userSettings.getDownloadPath();
        int mangasCompleteInt = userSettings.getMangasComplete();
        long megabytesDownloadedLong = userSettings.getBytesDownloaded() / (1024 * 1024);
        boolean alwaysShowButtons = userSettings.isAlwaysShowButtons();
        boolean orbotProxy =userSettings.isOrbotProxy();
        long seconds = timeReadLong / 1000;
        long hours = seconds / 3600;
        long secondsDelta = seconds - (hours * 3600);

        long minutes = secondsDelta / 60;
        long secs = secondsDelta - (minutes * 60);

        PackageManager manager = getPackageManager();
        try {
            PackageInfo info = manager.getPackageInfo(getPackageName(), 0);
            int versionCode = info.versionCode;
            String versionName = info.versionName;
            appVersion.setText(new StringBuilder().append(versionName).append(" (").append(versionCode).append(")").toString());
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        userNameTextView.setText(userNameString);
        userNameSmall.setText(userNameString);
        email.setText(emailString);
        timeRead.setText(hours + " " + getString(R.string.hrs) + " " + minutes + " " + getString(R.string.min) + " " +secs + " " + getString(R.string.sec));
        downloadPath.setText(downloadPathString);
        mangasComplete.setText(mangasCompleteInt + "");
        megabytesDownloaded.setText(megabytesDownloadedLong + "");
        showBtnsSwitch.setChecked(alwaysShowButtons);
        alwaysShowButtonsCard.setOnClickListener(v -> {
            boolean isChecked = !showBtnsSwitch.isChecked();
            showBtnsSwitch.setChecked(isChecked);
            userSettings.setAlwaysShowButtons(isChecked);
            Context context = getApplicationContext();
            ApplicationSettings.get(context).update(context);
        });
        showBtnsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            userSettings.setAlwaysShowButtons(isChecked);
            Context context = getApplicationContext();
            ApplicationSettings.get(context).update(context);
        });
        orbotProxySwitch.setChecked(orbotProxy);
        useOrbotProxy.setOnClickListener(v -> {
            boolean isChecked = !orbotProxySwitch.isChecked();
            orbotProxySwitch.setChecked(isChecked);
            userSettings.setOrbotProxy(isChecked);
            Context context = getApplicationContext();
            ApplicationSettings.get(context).update(context);


        });
        orbotProxySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            userSettings.setOrbotProxy(isChecked);
            Context context = getApplicationContext();
            ApplicationSettings.get(context).update(context);
        });
        userNameCard.setOnClickListener(v -> {
            ValueDialogFragment dialogFragment = ValueDialogFragment.createDialog(getString(R.string.username), userNameString, Constants.Settings.USER_NAME);
            dialogFragment.show(getFragmentManager(), ValueDialogFragment.TAG);
        });
        emailCard.setOnClickListener(v -> {
            ValueDialogFragment dialogFragment = ValueDialogFragment.createDialog(getString(R.string.email), emailString, Constants.Settings.EMAIL);
            dialogFragment.show(getFragmentManager(), ValueDialogFragment.TAG);
        });
        downloadPathCard.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileActivity.this, FolderPickerActivity.class);
            intent.putExtra(FolderPickerActivity.FOLDER_KEY, downloadPathString);
            startActivityForResult(intent, FOLDER_PICKER_REQUEST);
        });
        goToCloud.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileActivity.this, CloudStorageActivity.class);
            startActivity(intent);
        });
    }

    private static final int FOLDER_PICKER_REQUEST = 2;

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case FOLDER_PICKER_REQUEST:
                if (resultCode != Activity.RESULT_OK) {
                    return;
                }
                ApplicationSettings settings = ApplicationSettings.get(this);
                final ApplicationSettings.UserSettings userSettings = settings.getUserSettings();
                String path = data.getStringExtra(FolderPickerActivity.FOLDER_KEY);
                userSettings.setDownloadPath(path);
                downloadPath.setText(path);
                settings.update(this);
                init();
                break;
        }
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

    @Override
    protected void setupToolbar() {
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
    }

    public static class ValueDialogFragment extends DialogFragment {

        public static final String TAG = "ValueDialogFragment";

        public static final String TITLE = "TITLE";
        public static final String VALUE = "VALUE";
        public static final String PARAMETER_NAME = "PARAMETER_NAME";

        private String title = "";
        private String value = "";
        private String parameterName = "";

        private EditText editText = null;

        public ValueDialogFragment() {
            super();
        }

        public static ValueDialogFragment createDialog(final String title, final String value, final String parameterName) {
            ValueDialogFragment valueDialogFragment = new ValueDialogFragment();
            Bundle bundle = new Bundle();
            bundle.putString(TITLE, title);
            bundle.putString(VALUE, value);
            bundle.putString(PARAMETER_NAME, parameterName);
            valueDialogFragment.setArguments(bundle);
            return valueDialogFragment;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(final Bundle savedInstanceState) {
            Bundle b = getArguments();
            if (savedInstanceState != null) {
                b = savedInstanceState;
            }

            title = b.getString(TITLE);
            value = b.getString(VALUE);
            parameterName = b.getString(PARAMETER_NAME);

            CustomDialog.Builder builder = new CustomDialog.Builder(getActivity());
            builder.setPositiveButton(getString(R.string.sv_ok), (arg0, arg1) -> {
                Context context = MangaApplication.getContext();
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
                preferences.edit().putString(parameterName, editText.getText().toString()).apply();
                ApplicationSettings.get(context).invalidate(context);
                ProfileActivity activity = (ProfileActivity) getActivity();
                if (activity != null) {
                    activity.init();
                }
                dismiss();
            });
            builder.setNegativeButton(getString(R.string.sv_cancel), (dialogInterface, i) -> {
                dismiss();
            });
            LayoutInflater layoutInflater = getActivity().getLayoutInflater();
            View contentView = layoutInflater.inflate(R.layout.dialog_enter_string, null);
            editText = (EditText) contentView.findViewById(R.id.value);
            editText.setText(value);
            builder.setView(contentView);
            builder.setTitle(title);
            return builder.build();
        }

        @Override
        public void onSaveInstanceState(final Bundle outState) {
            outState.putString(TITLE, title);
            outState.putString(VALUE, editText.getText().toString());
            outState.putString(PARAMETER_NAME, parameterName);
            super.onSaveInstanceState(outState);
        }


    }

}
