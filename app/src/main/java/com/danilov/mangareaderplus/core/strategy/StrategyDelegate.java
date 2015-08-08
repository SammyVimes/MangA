package com.danilov.mangareaderplus.core.strategy;

import android.os.Handler;
import android.support.annotation.Nullable;

import com.danilov.mangareaderplus.core.interfaces.MangaShowObserver;
import com.danilov.mangareaderplus.core.interfaces.MangaShowStrategy;
import com.danilov.mangareaderplus.core.view.MangaViewPager;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Semyon on 07.08.2015.
 */
public class StrategyDelegate {

    private MangaShowStrategy strategy;

    private MangaShowListener listener;

    private boolean canHandle = false;

    private List<DelayedAction> delayedActions = new ArrayList<>();

    private Handler handler = new Handler();

    private boolean isStrategyInitialized = false;

    public StrategyDelegate(final MangaShowStrategy strategy) {
        this.strategy = strategy;
        strategy.setOnStrategyListener(listenerWrapper);
    }

    public boolean restoreState(final List<String> uris, final int chapter, final int image, final MangaViewPager mangaViewPager) {
        return strategy.restoreState(uris, chapter, image, mangaViewPager);
    }

    public void showImage(final int i) {
        strategy.showImage(i);
    }

    public int getCurrentImageNumber() {
        return strategy.getCurrentImageNumber();
    }

    public int getCurrentChapterNumber() {
        return strategy.getCurrentChapterNumber();
    }

    public boolean isOnline() {
        return strategy.isOnline();
    }

    public void previous() throws ShowMangaException {
        strategy.previous();
    }

    public int getTotalChaptersNumber() {
        return strategy.getTotalChaptersNumber();
    }

    public int getTotalImageNumber() {
        return strategy.getTotalImageNumber();
    }

    public List<String> getChapterUris() {
        return strategy.getChapterUris();
    }

    public void initStrategy(final int chapter, final int image) {
        strategy.initStrategy(chapter, image);
    }

    public void next() {
        strategy.next();
    }

    public void showChapter(final int i) {
        strategy.showChapter(i);
    }

    private MangaShowListener listenerWrapper = new MangaShowListener() {

        @Override
        public void onShowImage(final int number) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (canHandle && listener != null) {
                            listener.onShowImage(number);
                    } else {
                        delayedActions.add(new DelayedAction(ActionType.ON_SHOW_IMAGE, number));
                    }
                }
            });
        }

        @Override
        public void onPreviousPicture() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (canHandle && listener != null) {
                            listener.onPreviousPicture();
                    } else {
                        delayedActions.add(new DelayedAction(ActionType.ON_PREVIOUS_PICTURE));
                    }
                }
            });
        }

        @Override
        public void onShowChapter(final MangaShowStrategy.Result result, final String message) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (canHandle && listener != null) {
                        listener.onShowChapter(result, message);
                        strategy.onCallbackDelivered(ActionType.ON_SHOW_CHAPTER);
                    } else {
                        delayedActions.add(new DelayedAction(ActionType.ON_SHOW_CHAPTER, result, message));
                    }
                }
            });
        }

        @Override
        public void onNext(@Nullable final Integer chapterNum) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (canHandle && listener != null) {
                            listener.onNext(chapterNum);
                    } else {
                        delayedActions.add(new DelayedAction(ActionType.ON_NEXT, chapterNum));
                    }
                }
            });
        }

        @Override
        public void onInit(final MangaShowStrategy.Result result, final String message) {
            if (result == MangaShowStrategy.Result.SUCCESS) {
                isStrategyInitialized = true;
            }
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (canHandle && listener != null) {
                            listener.onInit(result, message);
                    } else {
                        delayedActions.add(new DelayedAction(ActionType.ON_INIT, result, message));
                    }
                }
            });
        }

    };

    public boolean isStrategyInitialized() {
        return isStrategyInitialized;
    }

    public interface MangaShowListener {

        void onShowImage(final int pageNum);

        void onPreviousPicture();

        void onShowChapter(final MangaShowStrategy.Result result, final String message);

        void onNext(@Nullable final Integer chapterNum);

        void onInit(final MangaShowStrategy.Result result, final String message);

    }

    private class DelayedAction {

        private ActionType type;

        private Integer number;

        private MangaShowStrategy.Result result;
        private String message;

        private DelayedAction(final ActionType type) {
            this.type = type;
        }

        private DelayedAction(final ActionType type, final MangaShowStrategy.Result result, final String message) {
            this.type = type;
            this.result = result;
            this.message = message;
        }

        private DelayedAction(final ActionType type, final Integer number) {
            this.type = type;
            this.number = number;
        }

        public void run() {
            switch (type) {
                case ON_SHOW_IMAGE:
                    listenerWrapper.onShowImage(number);
                    break;
                case ON_PREVIOUS_PICTURE:
                    listenerWrapper.onPreviousPicture();
                    break;
                case ON_SHOW_CHAPTER:
                    listenerWrapper.onShowChapter(result, message);
                    break;
                case ON_NEXT:
                    listenerWrapper.onNext(number);
                    break;
                case ON_INIT:
                    listenerWrapper.onInit(result, message);
                    break;
            }
        }

    }

    public void showChapterAndImage(final int chapterNumber, final int imageNumber) {
        strategy.showChapterAndImage(chapterNumber, imageNumber);
    }

    public enum ActionType {
        ON_SHOW_IMAGE,
        ON_PREVIOUS_PICTURE,
        ON_SHOW_CHAPTER,
        ON_NEXT,
        ON_INIT
    }

    public void onResume(final MangaShowListener listener) {
        this.canHandle = true;
        this.listener = listener;
        while (!delayedActions.isEmpty()) {
            DelayedAction delayedAction = delayedActions.remove(0);
            delayedAction.run();
        }
    }

    public void onPause() {
        this.canHandle = false;
        this.listener = null;
    }

}