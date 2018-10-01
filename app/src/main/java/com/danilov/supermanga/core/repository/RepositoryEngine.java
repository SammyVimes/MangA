package com.danilov.supermanga.core.repository;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.danilov.supermanga.R;
import com.danilov.supermanga.core.http.RequestPreprocessor;
import com.danilov.supermanga.core.model.Manga;
import com.danilov.supermanga.core.model.MangaChapter;
import com.danilov.supermanga.core.model.MangaSuggestion;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by Semyon Danilov on 17.05.2014.
 */
public interface RepositoryEngine {

    /**
     * Language of repository (e.g. Engurish, Russiano desu)
     * @return
     */
    String getLanguage();

    /**
     * Does the engine requires authorization
     * @return
     */
    boolean requiresAuth();

    /**
     * Search for suggestions
     * @param query user input
     * @return suggestions
     */
    List<MangaSuggestion> getSuggestions(final String query) throws RepositoryException;

    /**
     *
     * @param query user input
     * @return list of mangas matching query
     */
    List<Manga> queryRepository(final String query, final List<Filter.FilterValue> filterValues) throws RepositoryException;

    /**
     *
     * @param genre user selected genre
     * @return list of mangas matching query
     */
    List<Manga> queryRepository(final Genre genre) throws RepositoryException;

    /**
     * Getting info about manga (description and chapters)
     * @param manga
     * @return must return true if query was successful
     */
    boolean queryForMangaDescription(final Manga manga) throws RepositoryException;

    boolean queryForChapters(final Manga manga) throws RepositoryException;

    List<String> getChapterImages(final MangaChapter chapter) throws RepositoryException;

    String getBaseSearchUri();

    String getBaseUri();

    @NonNull
    List<FilterGroup> getFilters();

    @NonNull
    List<Genre> getGenres();

    @Nullable
    RequestPreprocessor getRequestPreprocessor();

    interface Repository {

        int getCountryIconId();

        String getName();

        RepositoryEngine getEngine();

    }

    //enum of names containing matched engines
    enum DefaultRepository implements Repository {

        READMANGA(new ReadmangaEngine(), "ReadManga (RU)", true, R.drawable.ic_russia),
        ADULTMANGA(new AdultmangaEngine(), "MintManga (RU)", true, R.drawable.ic_russia),
        KISSMANGA(new KissmangaEngine(), "KissManga (EN)", true, R.drawable.ic_english),
        MANGACHAN(new MangachanEngine(), "MangaChan (RU)", true, R.drawable.ic_russia),
        MANGAREADERNET(new MangaReaderNetEngine(), "MangaReader (EN)", true, R.drawable.ic_english),
        HENTAICHAN(new HentaichanEngine(), "Hentaichan (RU)", true, R.drawable.ic_russia),
        ALLHENTAI(new AllHentaiEngine(), "AllHentai (RU)", true, R.drawable.ic_russia),
        OFFLINE(new OfflineEngine(), "", false, -1);

        private static DefaultRepository[] withoutOffline = {READMANGA, ADULTMANGA, MANGACHAN, KISSMANGA, ALLHENTAI, MANGAREADERNET};

        private RepositoryEngine engine;
        private String name;
        private boolean isAdult;
        private int countryIconId;

        DefaultRepository(final RepositoryEngine engine, final String name, final boolean isAdult, final int countryIconId) {
            this.engine = engine;
            this.name = name;
            this.isAdult = isAdult;
            this.countryIconId = countryIconId;
        }

        @Override
        public int getCountryIconId() {
            return countryIconId;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public RepositoryEngine getEngine() {
            return engine;
        }

        public static DefaultRepository[] getWithoutOffline() {
            return withoutOffline;
        }

        public static DefaultRepository[] getWithoutAdult() {
            List<DefaultRepository> repositories = new LinkedList<>();
            for (DefaultRepository repository : withoutOffline) {
                if (!repository.isAdult) {
                    repositories.add(repository);
                }
            }
            return repositories.toArray(new DefaultRepository[repositories.size()]);
        }

        public static DefaultRepository[] getBySettings(final String[] names) {
            List<DefaultRepository> repositories = new LinkedList<>();
            for (DefaultRepository repository : withoutOffline) {
                if (!repository.isAdult) {
                    repositories.add(repository);
                    continue;
                }
                for (String name : names) {
                    if (repository.toString().equals(name)) {
                        repositories.add(repository);
                        break;
                    }
                }
            }
            return repositories.toArray(new DefaultRepository[repositories.size()]);
        }

        public static DefaultRepository[] getNotAdded(final String[] names) {
            List<DefaultRepository> repositories = new LinkedList<>();
            for (DefaultRepository repository : withoutOffline) {
                if (!repository.isAdult) {
                    continue;
                }
                boolean added = false;
                for (String name : names) {
                    if (repository.toString().equals(name)) {
                        added = true;
                    }
                }
                if (!added) {
                    repositories.add(repository);
                }
            }
            return repositories.toArray(new DefaultRepository[repositories.size()]);
        }

    }

    public class Genre {

        private String name;

        public Genre(final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

    }

    public class FilterGroup implements Iterable<Filter> {

        private List<Filter> filters;

        private String name;

        public FilterGroup(final String name, final int size) {
            this.name = name;
            filters = new ArrayList<>(size);
        }

        public int size() {
            return filters.size();
        }

        public String getName() {
            return name;
        }

        public void add(final Filter filter) {
            this.filters.add(filter);
        }

        public Filter get(final int i) {
            return filters.get(i);
        }

        @Override
        public Iterator<Filter> iterator() {
            return filters.iterator();
        }

    }

    public abstract class Filter<T> {

        private String name;

        public Filter(final String name) {
            this.name = name;
        }

        public abstract FilterType getType();

        public String getName() {
            return name;
        }

        public FilterValue newValue() {
            return new FilterValue();
        }

        public abstract String apply(final String uri, final FilterValue value);

        public abstract T getDefault();

        public enum FilterType {
            TRI_STATE,
            TWO_STATE;
        }

        public class FilterValue {

            private T value;

            public FilterValue() {
            }

            public void setValue(final T value) {
                this.value = value;
            }

            public T getValue() {
                return value;
            }

            public String apply(final String uri) {
                return Filter.this.apply(uri, this);
            }

        }

    }

}
