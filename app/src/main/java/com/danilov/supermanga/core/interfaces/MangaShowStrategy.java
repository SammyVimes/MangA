package com.danilov.supermanga.core.interfaces;

import com.danilov.supermanga.core.repository.RepositoryEngine;
import com.danilov.supermanga.core.strategy.ShowMangaException;
import com.danilov.supermanga.core.strategy.StrategyDelegate;
import com.danilov.supermanga.core.view.CompatPager;

import java.util.List;

/**
 * Created by Semyon Danilov on 21.06.2014.
 */
public interface MangaShowStrategy extends CompatPager.OnPageChangeListener {

    boolean restoreState();

    void showImage(final int i);

    void showChapter(int i, boolean fromNext);

    void showChapterAndImage(int chapterNumber, int imageNumber, boolean fromNext);

    void onCallbackDelivered(final StrategyDelegate.ActionType actionType);

    void next();

    void initStrategy(final int chapter, final int image);

    void previous() throws ShowMangaException;

    int getCurrentImageNumber();

    int getTotalImageNumber();

    int getCurrentChapterNumber();

    int getTotalChaptersNumber();

    List<String> getChapterUris();

    void setOnStrategyListener(final StrategyDelegate.MangaShowListener listener);

    boolean isOnline();

    boolean isInitInProgress();

    //TODO: return them actually!
    public enum Result {
        ERROR,
        SUCCESS,
        ALREADY_FINAL_CHAPTER,
        NO_SUCH_CHAPTER,
        LAST_DOWNLOADED,
        NOT_DOWNLOADED
    }

    RepositoryEngine getEngine();

}
