package com.danilov.supermanga.core.repository;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.danilov.supermanga.core.application.MangaApplication;
import com.danilov.supermanga.core.http.HttpBytesReader;
import com.danilov.supermanga.core.http.HttpRequestException;
import com.danilov.supermanga.core.http.HttpStreamReader;
import com.danilov.supermanga.core.http.RequestPreprocessor;
import com.danilov.supermanga.core.model.Manga;
import com.danilov.supermanga.core.model.MangaChapter;
import com.danilov.supermanga.core.model.MangaSuggestion;
import com.danilov.supermanga.core.repository.filter.BasicFilters;
import com.danilov.supermanga.core.util.Constants;
import com.danilov.supermanga.core.util.IoUtils;
import com.danilov.supermanga.core.util.Promise;
import com.danilov.supermanga.core.util.Utils;

import org.apache.http.protocol.HTTP;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;

/**
 * Created by Semyon on 19.10.2014.
 */
public class MangaReaderNetEngine implements RepositoryEngine {

    private static final String TAG = "MangaReaderNetEngine";

    private String baseSuggestionUri = "https://www.mangareader.net/actions/search/?q=";

    private String baseUri = "https://www.mangareader.net";

    private String baseSearchUri = "https://www.mangareader.net/search/?w=";

    @Inject
    public HttpStreamReader httpStreamReader;

    @Inject
    public HttpBytesReader httpBytesReader;

    @Override
    public String getLanguage() {
        return "English";
    }

    @Override
    public boolean requiresAuth() {
        return false;
    }

    public MangaReaderNetEngine() {
        MangaApplication.get().applicationComponent().inject(this);
    }

    @Override
    public List<MangaSuggestion> getSuggestions(String query) throws RepositoryException {
        List<MangaSuggestion> suggestions = null;
        if (httpBytesReader != null) {
            Exception ex = null;
            try {
                String uri = baseSuggestionUri + URLEncoder.encode(query, Charset.forName(HTTP.UTF_8).name()) + "&limit=100";
                byte[] response = httpBytesReader.fromUri(uri);
                String responseString = IoUtils.convertBytesToString(response);
                suggestions = parseSuggestionsResponse(responseString);
            } catch (UnsupportedEncodingException | HttpRequestException e) {
                ex = e;
            }
            if (ex != null) {
                throw new RepositoryException(ex.getMessage());
            }
        }
        return suggestions;
    }

    private List<MangaSuggestion> parseSuggestionsResponse(final String responseString) {
        List<MangaSuggestion> mangaSuggestions = new ArrayList<>();
        String[] lines = responseString.split("\n");
        if (lines.length > 0) {
            for (String line : lines) {
                String[] content = line.split("\\|");
                if (content.length > 5) {
                    String title = content[0];
                    String link = content[4];
                    MangaSuggestion suggestion = new MangaSuggestion(title, link, DefaultRepository.MANGAREADERNET);
                    mangaSuggestions.add(suggestion);
                }
            }
        }
        return mangaSuggestions;
    }

    @Override
    public List<Manga> queryRepository(String query, final List<Filter.FilterValue> filterValues) throws RepositoryException {
        List<Manga> mangaList = null;
        if (httpBytesReader != null) {
            try {
                String uri = baseSearchUri + URLEncoder.encode(query, Charset.forName(HTTP.UTF_8).name());
                uri += "&genre=";
                for (Filter.FilterValue filterValue : filterValues) {
                    uri = filterValue.apply(uri);
                }
                uri += "&order=2";
                byte[] response = httpBytesReader.fromUri(uri);
                String responseString = IoUtils.convertBytesToString(response);
                mangaList = parseMangaSearchResponse(Utils.toDocument(responseString));
            } catch (UnsupportedEncodingException | HttpRequestException e) {
                throw new RepositoryException("Failed to load: " + e.getMessage());
            }
        }
        return mangaList;
    }

    @Override
    public List<Manga> queryRepository(final Genre genre) throws RepositoryException {
        return Collections.emptyList();
    }

    @Override
    public boolean queryForMangaDescription(Manga manga) throws RepositoryException {
        if (httpBytesReader != null) {
            String uri = baseUri + manga.getUri();
            byte[] response = null;
            try {
                response = httpBytesReader.fromUri(uri);
            } catch (HttpRequestException e) {
                if (e.getMessage() != null) {
                    Log.d(TAG, e.getMessage());
                } else {
                    Log.d(TAG, "Failed to load manga description");
                }
                throw new RepositoryException(e.getMessage());
            }
            String responseString = IoUtils.convertBytesToString(response);
            return parseMangaDescriptionResponse(manga, Utils.toDocument(responseString));
        }
        return false;
    }


    @Override
    public boolean queryForChapters(final Manga manga) throws RepositoryException {
        if (httpBytesReader != null) {
            String uri = baseUri + manga.getUri();
            byte[] response = null;
            try {
                response = httpBytesReader.fromUri(uri);
            } catch (HttpRequestException e) {
                if (e.getMessage() != null) {
                    Log.d(TAG, e.getMessage());
                } else {
                    Log.d(TAG, "Failed to load manga description");
                }
                throw new RepositoryException(e.getMessage());
            }
            String responseString = IoUtils.convertBytesToString(response);
            List<MangaChapter> chapters = parseMangaChaptersResponse(manga, Utils.toDocument(responseString));
            manga.setChapters(chapters);
            return true;
        }
        return false;
    }

    @Override
    public List<String> getChapterImages(final MangaChapter chapter) throws RepositoryException {
        if (httpBytesReader != null) {
            String uri = baseUri + chapter.getUri();
            byte[] response = null;
            try {
                response = httpBytesReader.fromUri(uri);
            } catch (HttpRequestException e) {
                if (e.getMessage() != null) {
                    Log.d(TAG, e.getMessage());
                } else {
                    Log.d(TAG, "Failed to load manga description");
                }
                throw new RepositoryException(e.getMessage());
            }
            String responseString = IoUtils.convertBytesToString(response);
            List<String> pageUris = getImagePagesUris(Utils.toDocument(responseString));
            final String[] imageUrisArray = new String[pageUris.size()];
            Promise[] promises = new Promise[pageUris.size()];
            int i = 0;
            for (String pageUri : pageUris) {
                PagePromiseRunnable pageImageThread = new PagePromiseRunnable(httpBytesReader, pageUri);
                Promise p = Promise.run(pageImageThread, true);
                promises[i] = p;
                i++;
            }
            Promise<Object[]> allImages = Promise.all(promises);
            final Exception[] holder = new Exception[1];

            allImages.catchException((data, success) -> {
                holder[0] = data;
                return null;
            });
            allImages.then((data, success) -> {
                System.arraycopy(data, 0, imageUrisArray, 0, data.length);
                return null;
            });
            allImages.waitForIt();
            if (holder[0] != null) {
                throw new RepositoryException(holder[0]);
            }
            return Arrays.asList(imageUrisArray);
        }
        return null;
    }

    private class PagePromiseRunnable implements Promise.PromiseRunnable<String> {

        private HttpBytesReader httpBytesReader;
        private String pageUri;

        public PagePromiseRunnable(final HttpBytesReader reader, final String pageUri) {
            this.httpBytesReader = reader;
            this.pageUri = pageUri;
        }

        @Override
        public void run(final Promise<String>.Resolver resolver) {
            String uri = baseUri + pageUri;
            byte[] response = null;
            try {
                response = httpBytesReader.fromUri(uri);
            } catch (HttpRequestException e) {
                if (e.getMessage() != null) {
                    Log.d(TAG, e.getMessage());
                } else {
                    Log.d(TAG, "Failed to load manga description");
                }
                resolver.except(e);
            }
            String responseString = IoUtils.convertBytesToString(response);
            String imageUri = parsePageForImageUrl(Utils.toDocument(responseString));
            resolver.resolve(imageUri);
        }

    }

    @Override
    public String getBaseSearchUri() {
        return null;
    }

    @Override
    public String getBaseUri() {
        return baseUri;
    }



    //html values
    private String linkValueAttr = "href";
    private String RESULTS_ID = "mangaresults";
    private String RESULT_CLASS = "mangaresultinner";
    private String BACKGROUND_HOLDER_CLASS = "imgsearchresults";

    private List<Manga> parseMangaSearchResponse(final Document document) {
        Element results = document.getElementById(RESULTS_ID);
        Elements mangaResults = results.getElementsByClass(RESULT_CLASS);

        List<Manga> mangas = new ArrayList<>();
        for (Element mangaResult : mangaResults) {
            Element nameElement = mangaResult.select(".manga_name a").get(0);
            String title = nameElement.text();
            String url = nameElement.attr(linkValueAttr);

            Element backgroundHolder = mangaResult.getElementsByClass(BACKGROUND_HOLDER_CLASS).get(0);
            String style = backgroundHolder.attr("style");
            String imageUrl = style.replace("background-image:url('", "").replace("')", "");

            Manga manga = new Manga(title, url, DefaultRepository.MANGAREADERNET);
            manga.setCoverUri(imageUrl);
            mangas.add(manga);
        }

        return mangas;
    }

    private String imgContainerId = "mangaimg";
    private String descriptionContainerId = "readmangasum";
    private String chaptersContainerId = "listing";

    private boolean parseMangaDescriptionResponse(Manga manga, Document document) {
        Element imageElement = document.getElementById(imgContainerId);
        if (imageElement == null) {
            manga.setCoverUri("");
        } else {
            Elements imgs = imageElement.getElementsByTag("img");
            if (!imgs.isEmpty()) {
                String coverUrl = imgs.get(0).attr("src");
                manga.setCoverUri(coverUrl);
            } else {
                manga.setCoverUri("");
            }
        }
        Element descriptionContainer = document.getElementById(descriptionContainerId);
        if (descriptionContainer != null) {
            Elements desc = descriptionContainer.getElementsByTag("p");
            if (!desc.isEmpty()) {
                String description = desc.text();
                manga.setDescription(description);
            } else {
                manga.setDescription("");
            }
        } else {
            manga.setDescription("");
        }
        Element chaptersContainer = document.getElementById(chaptersContainerId);
        if (chaptersContainer == null) {
            manga.setChaptersQuantity(0);
        } else {
            Elements chapters = chaptersContainer.getElementsByTag("a");
            manga.setChaptersQuantity(chapters.size());
        }

        Element mangaproperties = document.getElementById("mangaproperties");
        if (mangaproperties != null) {
            Elements table = mangaproperties.getElementsByTag("table");
            if (!table.isEmpty()) {
                Elements rows = table.first().getElementsByTag("tr");
                if (rows.size() > 4) {
                    Elements authorElements = rows.get(4).children();
                    if (authorElements.size() > 1) {
                        manga.setAuthor(authorElements.get(1).text());
                    }
                }
                if (rows.size() > 7) {
                    Elements genreElements = rows.get(7).children();
                    if (genreElements.size() > 1) {
                        Elements a = genreElements.get(1).getElementsByTag("a");
                        StringBuilder lst = new StringBuilder();
                        if (!a.isEmpty()) {
                            int i = 0;
                            for (Element links : a) {
                                String txt = links.text();
                                if (i > 0) {
                                    lst.append(", ");
                                }
                                lst.append(txt);
                                i++;
                            }
                        }
                        manga.setGenres(lst.toString());
                    }
                }
            }
        }

        return true;
    }

    private List<MangaChapter> parseMangaChaptersResponse(final Manga manga, final Document document) {
        Element chaptersContainer = document.getElementById(chaptersContainerId);
        if (chaptersContainer == null) {
            manga.setChaptersQuantity(0);
            return null;
        }
        Elements chapterLinks = chaptersContainer.getElementsByTag("a");
        List<MangaChapter> chapters = new ArrayList<>(chapterLinks.size());
        int number = 0;
        for (Element element : chapterLinks) {
            String link = element.attr(linkValueAttr);
            String title = element.parent().text();
            MangaChapter chapter = new MangaChapter(title, number, link);
            chapters.add(chapter);
            number++;
        }
        manga.setChaptersQuantity(chapters.size());
        return chapters;
    }

    private String selectorId = "pageMenu";

    private List<String> getImagePagesUris(final Document document) {
        Element element = document.getElementById(selectorId);
        List<String> uris = new LinkedList<>();
        if (element == null) {
            return uris;
        }
        for (Element option : element.children()) {
            uris.add(option.attr("value"));
        }
        return uris;
    }

    private String imgHolderId = "imgholder";
    private String imgElement = "img";

    private String parsePageForImageUrl(final Document document) {
        Element element = document.getElementById(imgHolderId);
        if (element == null) {
            return null;
        }
        Elements imgs = element.getElementsByTag(imgElement);
        if (imgs.isEmpty()) {
            return null;
        }
        return imgs.get(0).attr("src");
    }


    private List<FilterGroup> filterGroups = new ArrayList<>(2);

    {
        FilterGroup genres = new FilterGroup("Genre", 37);

        genres.add(new BasicFilters.MangaReaderTriState("Action"));
        genres.add(new BasicFilters.MangaReaderTriState("Adventure"));
        genres.add(new BasicFilters.MangaReaderTriState("Comedy"));
        genres.add(new BasicFilters.MangaReaderTriState("Demons"));
        genres.add(new BasicFilters.MangaReaderTriState("Drama"));

        if (!Constants.IS_MARKET_VERSION) {
            genres.add(new BasicFilters.MangaReaderTriState("Ecchi"));
        }

        genres.add(new BasicFilters.MangaReaderTriState("Fantasy"));
        genres.add(new BasicFilters.MangaReaderTriState("Gender Bender"));
        genres.add(new BasicFilters.MangaReaderTriState("Harem"));
        genres.add(new BasicFilters.MangaReaderTriState("Historical"));
        genres.add(new BasicFilters.MangaReaderTriState("Horror"));
        genres.add(new BasicFilters.MangaReaderTriState("Josei"));
        genres.add(new BasicFilters.MangaReaderTriState("Magic"));
        genres.add(new BasicFilters.MangaReaderTriState("Martial Arts"));
        if (!Constants.IS_MARKET_VERSION) {
            genres.add(new BasicFilters.MangaReaderTriState("Mature"));
        }
        genres.add(new BasicFilters.MangaReaderTriState("Mecha"));
        genres.add(new BasicFilters.MangaReaderTriState("Military"));
        genres.add(new BasicFilters.MangaReaderTriState("Mystery"));
        genres.add(new BasicFilters.MangaReaderTriState("One Shot"));
        genres.add(new BasicFilters.MangaReaderTriState("Psychological"));
        genres.add(new BasicFilters.MangaReaderTriState("Romance"));
        genres.add(new BasicFilters.MangaReaderTriState("School Life"));
        genres.add(new BasicFilters.MangaReaderTriState("Sci-Fi"));
        genres.add(new BasicFilters.MangaReaderTriState("Seinen"));
        genres.add(new BasicFilters.MangaReaderTriState("Shoujo"));
        genres.add(new BasicFilters.MangaReaderTriState("Shoujoai"));
        genres.add(new BasicFilters.MangaReaderTriState("Shounen"));
        genres.add(new BasicFilters.MangaReaderTriState("Shounenai"));
        genres.add(new BasicFilters.MangaReaderTriState("Slice of Life"));
        genres.add(new BasicFilters.MangaReaderTriState("Smut"));
        genres.add(new BasicFilters.MangaReaderTriState("Sports"));
        genres.add(new BasicFilters.MangaReaderTriState("Super Power"));
        genres.add(new BasicFilters.MangaReaderTriState("Supernatural"));
        genres.add(new BasicFilters.MangaReaderTriState("Tragedy"));
        genres.add(new BasicFilters.MangaReaderTriState("Vampire"));
        if (!Constants.IS_MARKET_VERSION) {
            genres.add(new BasicFilters.MangaReaderTriState("Yaoi"));
            genres.add(new BasicFilters.MangaReaderTriState("Yuri"));
        }

        filterGroups.add(genres);

    }

    @NonNull
    @Override
    public List<FilterGroup> getFilters() {
        return filterGroups;
    }

    @NonNull
    @Override
    public List<Genre> getGenres() {
        return Collections.emptyList();
    }

    @Nullable
    @Override
    public RequestPreprocessor getRequestPreprocessor() {
        return null;
    }

}
