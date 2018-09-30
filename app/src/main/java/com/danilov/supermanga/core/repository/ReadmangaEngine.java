package com.danilov.supermanga.core.repository;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.danilov.supermanga.core.application.MangaApplication;
import com.danilov.supermanga.core.http.HttpBytesReader;
import com.danilov.supermanga.core.http.HttpRequestException;
import com.danilov.supermanga.core.http.HttpStreamModel;
import com.danilov.supermanga.core.http.HttpStreamReader;
import com.danilov.supermanga.core.http.LinesSearchInputStream;
import com.danilov.supermanga.core.http.RequestPreprocessor;
import com.danilov.supermanga.core.model.Manga;
import com.danilov.supermanga.core.model.MangaChapter;
import com.danilov.supermanga.core.model.MangaSuggestion;
import com.danilov.supermanga.core.repository.filter.BasicFilters;
import com.danilov.supermanga.core.util.Constants;
import com.danilov.supermanga.core.util.IoUtils;
import com.danilov.supermanga.core.util.RequestUtils;
import com.danilov.supermanga.core.util.Utils;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

/**
 * Created by Semyon Danilov on 17.05.2014.
 */

/**
 * Engine for russian most popular manga site, geh
 */
public class ReadmangaEngine implements RepositoryEngine {

    private static final String TAG = "ReadmangaEngine";

    private String baseSearchUri = "http://readmanga.me/search/advanced";
    private String baseSuggestionUri = "http://readmanga.me/search/suggestion?query=";
    public static final String baseUri = "http://readmanga.me";

    @Inject
    public HttpStreamReader httpStreamReader;

    @Inject
    public HttpBytesReader httpBytesReader;

    @Inject
    public HttpClient httpClient;

    @Override
    public String getLanguage() {
        return "Русский";
    }

    @Override
    public boolean requiresAuth() {
        return false;
    }

    public ReadmangaEngine() {
        MangaApplication.get().applicationComponent().inject(this);
    }

    @Override
    public List<MangaSuggestion> getSuggestions(final String query) throws RepositoryException {
        List<MangaSuggestion> suggestions = null;
        if (httpBytesReader != null) {
            Exception ex = null;
            try {
                String uri = baseSuggestionUri + URLEncoder.encode(query, Charset.forName(HTTP.UTF_8).name());
                byte[] response = httpBytesReader.fromUri(uri);
                String responseString = IoUtils.convertBytesToString(response);
                suggestions = parseSuggestionsResponse(Utils.toJSON(responseString));
            } catch (UnsupportedEncodingException | JSONException | HttpRequestException e) {
                ex = e;
            }
            if (ex != null) {
                throw new RepositoryException(ex.getMessage());
            }
        }
        return suggestions;
    }

    @Override
    public List<Manga> queryRepository(final String query, final List<Filter.FilterValue> filterValues) throws RepositoryException {
        List<Manga> mangaList = null;
        if (httpBytesReader != null) {
            try {
                String uri = baseSearchUri;
                final HttpPost request = RequestUtils.from(uri, "q", query);

                HttpResponse response = httpClient.execute(request);
                byte[] result = IoUtils.convertStreamToBytes(response.getEntity().getContent());
                String responseString = IoUtils.convertBytesToString(result);

                for (Filter.FilterValue filterValue : filterValues) {
                    uri = filterValue.apply(uri);
                }

                mangaList = parseSearchResponse(Utils.toDocument(responseString));
            } catch (IOException e) {
                throw new RepositoryException("Failed to load: " + e.getMessage());
            }
        }
        return mangaList;
    }

    @Override
    public List<Manga> queryRepository(final Genre _genre) throws RepositoryException {
        List<Manga> mangaList = null;
        ReadmangaGenre genre = (ReadmangaGenre) _genre;
        if (httpBytesReader != null) {
            try {
                String uri = baseUri + genre.getLink();
                byte[] response = httpBytesReader.fromUri(uri);
                String responseString = IoUtils.convertBytesToString(response);
                mangaList = parseGenreSearchResponse(Utils.toDocument(responseString));
            } catch (HttpRequestException e) {
                throw new RepositoryException("Failed to load: " + e.getMessage());
            }
        }
        return mangaList;
    }

    @Override
    public boolean queryForMangaDescription(final Manga manga) throws RepositoryException {
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
                Log.d(TAG, e.getMessage());
                throw new RepositoryException(e.getMessage());
            }
            String responseString = IoUtils.convertBytesToString(response);
            List<MangaChapter> chapters = parseMangaChaptersResponse(Utils.toDocument(responseString));
            if (chapters != null) {
                manga.setChapters(chapters);
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> getChapterImages(final MangaChapter chapter) throws RepositoryException {
        String uri = baseUri + chapter.getUri() + "?mature=1";
        byte[] bytes = new byte[1024];
        List<String> imageUrls = null;
        LinesSearchInputStream inputStream = null;
        try {
            HttpStreamModel model = httpStreamReader.fromUri(uri);
            inputStream = new LinesSearchInputStream(model.stream, "rm_h.init( [", "], 0, false)");
            int status = LinesSearchInputStream.SEARCHING;
            while (status == LinesSearchInputStream.SEARCHING) {
                status = inputStream.read(bytes);
            }
            bytes = inputStream.getResult();
            String str = IoUtils.convertBytesToString(bytes);
            imageUrls = extractUrls(str);
        } catch (IOException | HttpRequestException e) {
            Log.d(TAG, e.getMessage());
            throw new RepositoryException(e.getMessage());
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    Log.d(TAG, e.getMessage());
                }
            }
        }
        return imageUrls;
    }

    private final Pattern arrayItemPattern = Pattern.compile("\\[('.*?'),('.*?'),(\".*?\"),.*?,.*?],");

    private List<String> extractUrls(final String str) {
        List<String> urls = new ArrayList<>();
        final String newStr = str + ","; //savant
        Matcher matcher = arrayItemPattern.matcher(newStr);
        while (matcher.find()) {

            String base = matcher.group(2).replace("'", "");
            String fldr = matcher.group(1).replace("'", "");
            String pic = matcher.group(3).replace("\"", "");

            String url = base + fldr + pic;
            if (!url.contains("http")) {
                url = baseUri + url;
            }
            urls.add(url);
        }
        return urls;
    }

    //json names

    private String suggestionsElementName = "suggestions";
    private String exclusionValue = "Автор";
    private String exclusionValue2 = "Переводчик";
    private String valueElementName = "value";
    private String dataElementName = "data";
    private String linkElementName = "link";


    //!json names

    private List<MangaSuggestion> parseSuggestionsResponse(final JSONObject object) {
        List<MangaSuggestion> mangaSuggestions = new ArrayList<>();
        try {
            JSONArray suggestions = object.getJSONArray(suggestionsElementName);
            for (int i = 0; i < suggestions.length(); i++) {
                JSONObject suggestion = suggestions.getJSONObject(i);
                String value = suggestion.getString(valueElementName);
                if (value == null
                        || value.contains(exclusionValue)
                        || value.contains(exclusionValue2)) {
                    continue;
                }
                try {
                    String link = suggestion.getString(linkElementName);
                    MangaSuggestion mangaSuggestion = new MangaSuggestion(value, link, DefaultRepository.READMANGA);
                    mangaSuggestions.add(mangaSuggestion);
                } catch (JSONException e ) {
                    Log.d(TAG, e.getMessage());
                }
            }
        } catch (JSONException e) {
            Log.d(TAG, e.getMessage());
        }
        return mangaSuggestions;
    }

    //html values
    private String searchElementId = "mangaResults";
    private String mangaTileClass = "tile";
    private String mangaCoverClass = "img";
    private String mangaDescClass = "desc";

    //!html values

    private List<Manga> parseSearchResponse(final Document document) {
        List<Manga> mangaList = null;
        Element searchResults = document.getElementById(searchElementId);
        List<Element> mangaLinks = searchResults.getElementsByClass(mangaTileClass);
        mangaList = new ArrayList<>(mangaLinks.size());
        for (Element mangaLink : mangaLinks) {
            String uri = null;
            String mangaName = null;
            Elements tmp = mangaLink.getElementsByClass(mangaDescClass);
            if (!tmp.isEmpty()) {
                tmp = tmp.get(0).getElementsByTag("h3");
                if (!tmp.isEmpty()) {
                    tmp = tmp.get(0).getElementsByTag("a");
                    if (!tmp.isEmpty()) {
                        Element realLink = tmp.get(0);
                        uri = realLink.attr("href");
                        mangaName = realLink.attr("title");
                    }
                }
            }

            Element screenElement = mangaLink.getElementsByClass(mangaCoverClass).get(0);

            tmp = screenElement.getElementsByTag("img");
            String coverUri = null;
            if (!tmp.isEmpty()) {
                Element img = tmp.get(0);
                coverUri = img != null ? img.attr("data-original") : "";
                if (coverUri.endsWith("_p.jpg")) {
                    coverUri = coverUri.replace("_p.jpg", ".jpg");
                } else if (coverUri.endsWith("_p.png")) {
                    coverUri = coverUri.replace("_p.png", ".png");
                }
            }
            Manga manga = new Manga(mangaName, uri, DefaultRepository.READMANGA);
            manga.setCoverUri(coverUri);
            mangaList.add(manga);
        }
        return mangaList;
    }

    //html values
    private String genresClass = "tiles";

    //!html values

    private List<Manga> parseGenreSearchResponse(final Document document) {
        List<Manga> mangaList = null;
        Elements els = document.getElementsByClass(genresClass);
        if (els.isEmpty()) {
            return null;
        }
        Element searchResults = els.first();
        List<Element> mangaLinks = searchResults.getElementsByClass(mangaTileClass);
        mangaList = new ArrayList<>(mangaLinks.size());
        for (Element mangaLink : mangaLinks) {
            String uri = null;
            String mangaName = null;
            Elements tmp = mangaLink.getElementsByClass(mangaDescClass);
            if (!tmp.isEmpty()) {
                tmp = tmp.get(0).getElementsByTag("h3");
                if (!tmp.isEmpty()) {
                    tmp = tmp.get(0).getElementsByTag("a");
                    if (!tmp.isEmpty()) {
                        Element realLink = tmp.get(0);
                        uri = realLink.attr("href");
                        mangaName = realLink.attr("title");
                    }
                }
            }

            Element screenElement = mangaLink.getElementsByClass(mangaCoverClass).get(0);

            tmp = screenElement.getElementsByTag("img");
            String coverUri = null;
            if (!tmp.isEmpty()) {
                Element img = tmp.get(0);
                coverUri = img != null ? img.attr("data-original") : "";
                if (coverUri.endsWith("_p.jpg")) {
                    coverUri = coverUri.replace("_p.jpg", ".jpg");
                } else if (coverUri.endsWith("_p.png")) {
                    coverUri = coverUri.replace("_p.png", ".png");
                }
            }
            Manga manga = new Manga(mangaName, uri, DefaultRepository.READMANGA);
            manga.setCoverUri(coverUri);
            mangaList.add(manga);
        }
        return mangaList;
    }

    //html values
    private String descriptionElementClass = "manga-description";
    private String chaptersElementClass = "chapters-link";
    private String coverClassName = "subject-cower";
    private String coverClass = "full-list-pic";
    private String altCoverClass = "nivo-imageLink";

    private boolean parseMangaDescriptionResponse(final Manga manga, final Document document) {

        Elements tmp = document.getElementsByClass("leftContent");
        if (tmp == null) {
            return false;
        }
        Element baseElem = tmp.first();
        if (baseElem == null) {
            return false;
        }
        Elements authorsElement = baseElem.getElementsByClass("elem_author");
        if (!authorsElement.isEmpty()) {
            StringBuilder authorList = new StringBuilder();
            int i = 0;
            for (Element element : authorsElement) {
                Elements holder = element.getElementsByTag("a");
                if (holder == null) {
                    continue;
                }
                String author = holder.text();
                if (i > 0) {
                    authorList.append(", ");
                }
                authorList.append(author);
                i++;
            }
            String authors = authorList.toString();
            manga.setAuthor(authors);
        }
        Elements genresElement = baseElem.getElementsByClass("elem_genre");
        if (!genresElement.isEmpty()) {
            StringBuilder genreList = new StringBuilder();
            int i = 0;
            for (Element element : genresElement) {
                Elements holder = element.getElementsByTag("a");
                if (holder == null) {
                    continue;
                }
                String genre = holder.text();
                if (i > 0) {
                    genreList.append(", ");
                }
                genreList.append(genre);
                i++;
            }
            String genres = genreList.toString();
            manga.setGenres(genres);
        }


        Elements mangaDescriptionElements = baseElem.getElementsByClass(descriptionElementClass);
        Elements links = null;
        if (!mangaDescriptionElements.isEmpty()) {
            Element mangaDescription = mangaDescriptionElements.first();
            links = mangaDescription.getElementsByTag("a");
            if (!links.isEmpty()) {
                links.remove();
            }
            String description = mangaDescription.text();
            manga.setDescription(description);
        }
        Elements chaptersElements = baseElem.getElementsByClass(chaptersElementClass);
        int quantity = 0;
        if (chaptersElements.isEmpty()) {
            quantity = 0;
        } else {
            Element chaptersElement = chaptersElements.first();
            links = chaptersElement.getElementsByTag("a");
            quantity = links.size();
        }
        manga.setChaptersQuantity(quantity);
        if (manga.getCoverUri() != null) {
            return true;
        }
        Elements coverContainer = baseElem.getElementsByClass(coverClassName);
        String coverUri = null;
        if (coverContainer.size() >= 1) {
            Element cover = coverContainer.get(0);
            Elements coverUriElements = cover.getElementsByClass(coverClass); //cover.getElementsByClass(altCoverClass);
            if (coverUriElements.size() >= 1) {
                //a lot of images
                Element e = coverUriElements.get(0);
                coverUri = e.attr("href");
            } else {
                coverUriElements = cover.getElementsByClass(altCoverClass); // cover.getElementsByClass(coverClass);
                if (coverUriElements.size() >= 1) {
                    //more than one
                    coverUri = coverUriElements.get(0).attr("href");
                } else {
                    //only one
                    Elements elements = cover.getElementsByTag("img");
                    if (elements.size() >= 1) {
                        coverUri = elements.get(0).attr("src");
                    }
                }
            }
        }
        manga.setCoverUri(coverUri);
        return true;
    }

    //html values
    private String linkValueAttr = "href";

    private List<MangaChapter> parseMangaChaptersResponse(final Document document) {
        Elements chaptersElements = document.getElementsByClass(chaptersElementClass);
        if (chaptersElements.isEmpty()) {
            return null;
        }
        Element chaptersElement = chaptersElements.first();
        Elements links = chaptersElement.getElementsByTag("a");
        if (links.isEmpty()) {
            return null;
        }
        List<MangaChapter> chapters = new ArrayList<>(links.size());
        int number = 0;
        for (int i = links.size() - 1; i >= 0; i--) {
            Element element = links.get(i);
            String link = element.attr(linkValueAttr);
            String title = element.text();
            MangaChapter chapter = new MangaChapter(title, number, link);
            chapters.add(chapter);
            number++;
        }
        return chapters;
    }

    @Override
    public String getBaseSearchUri() {
        return baseSearchUri;
    }

    @Override
    public String getBaseUri() {
        return baseUri;
    }

    private List<FilterGroup> filterGroups = new ArrayList<>(2);

    {
        FilterGroup categories = new FilterGroup("Категории", 13);
        categories.add(new BasicFilters.RABaseTriState("В цвете", "el_7290"));
        categories.add(new BasicFilters.RABaseTriState("Веб", "el_2160"));
        categories.add(new BasicFilters.RABaseTriState("Ёнкома", "el_2161"));
        categories.add(new BasicFilters.RABaseTriState("Комикс западный", "el_3515"));
        categories.add(new BasicFilters.RABaseTriState("Манхва", "el_3001"));
        categories.add(new BasicFilters.RABaseTriState("Маньхуа", "el_3002"));
        categories.add(new BasicFilters.RABaseTriState("Сборник", "el_2157"));
        categories.add(new BasicFilters.RABaseTriState("Высокий рейтинг", "s_high_rate"));
        categories.add(new BasicFilters.RABaseTriState("Сингл", "s_single"));
        if (!Constants.IS_MARKET_VERSION) {
            categories.add(new BasicFilters.RABaseTriState("Для взрослых", "s_mature"));
        }
        categories.add(new BasicFilters.RABaseTriState("Завершенная", "s_completed"));
        categories.add(new BasicFilters.RABaseTriState("Переведено", "s_translated"));
        categories.add(new BasicFilters.RABaseTriState("Ожидает загрузки", "s_wait_upload"));
        FilterGroup genres = new FilterGroup("Жанр", 40);

        genres.add(new BasicFilters.RABaseTriState("Арт", "el_5685"));
        genres.add(new BasicFilters.RABaseTriState("Боевик", "el_2155"));
        genres.add(new BasicFilters.RABaseTriState("Боевые искусства", "el_2143"));
        genres.add(new BasicFilters.RABaseTriState("Вампиры", "el_2148"));
        genres.add(new BasicFilters.RABaseTriState("Гарем", "el_2142"));
        genres.add(new BasicFilters.RABaseTriState("Гендерная интрига", "el_2156"));
        genres.add(new BasicFilters.RABaseTriState("Героическое фэнтези", "el_2146"));
        genres.add(new BasicFilters.RABaseTriState("Детектив", "el_2152"));
        genres.add(new BasicFilters.RABaseTriState("Дзёсэй", "el_2158"));
        genres.add(new BasicFilters.RABaseTriState("Додзинси", "el_2141"));
        genres.add(new BasicFilters.RABaseTriState("Драма", "el_2118"));
        genres.add(new BasicFilters.RABaseTriState("Игра", "el_2154"));
        genres.add(new BasicFilters.RABaseTriState("История", "el_2119"));
        genres.add(new BasicFilters.RABaseTriState("Кодомо", "el_2137"));
        genres.add(new BasicFilters.RABaseTriState("Комедия", "el_2136"));
        genres.add(new BasicFilters.RABaseTriState("Махо-сёдзё", "el_2147"));
        genres.add(new BasicFilters.RABaseTriState("Меха", "el_2126"));
        genres.add(new BasicFilters.RABaseTriState("Мистика", "el_2132"));
        genres.add(new BasicFilters.RABaseTriState("Научная фантастика", "el_2133"));
        genres.add(new BasicFilters.RABaseTriState("Повседневность", "el_2135"));
        genres.add(new BasicFilters.RABaseTriState("Постапокалиптика", "el_2151"));
        genres.add(new BasicFilters.RABaseTriState("Приключения", "el_2130"));
        genres.add(new BasicFilters.RABaseTriState("Психология", "el_2144"));
        genres.add(new BasicFilters.RABaseTriState("Романтика", "el_2121"));
        genres.add(new BasicFilters.RABaseTriState("Самурайский боевик", "el_2124"));
        genres.add(new BasicFilters.RABaseTriState("Сверхъестественное", "el_2159"));
        genres.add(new BasicFilters.RABaseTriState("Сёдзё", "el_2122"));
        genres.add(new BasicFilters.RABaseTriState("Сёдзё-ай", "el_2128"));
        genres.add(new BasicFilters.RABaseTriState("Сёнэн", "el_2134"));
        genres.add(new BasicFilters.RABaseTriState("Сёнэн-ай", "el_2139"));
        genres.add(new BasicFilters.RABaseTriState("Спорт", "el_2129"));
        genres.add(new BasicFilters.RABaseTriState("Сэйнэн", "el_2138"));
        genres.add(new BasicFilters.RABaseTriState("Трагедия", "el_2153"));
        genres.add(new BasicFilters.RABaseTriState("Триллер", "el_2150"));
        genres.add(new BasicFilters.RABaseTriState("Ужасы", "el_2125"));
        genres.add(new BasicFilters.RABaseTriState("Фантастика", "el_2140"));
        genres.add(new BasicFilters.RABaseTriState("Фэнтези", "el_2131"));
        genres.add(new BasicFilters.RABaseTriState("Школа", "el_2127"));

        if (!Constants.IS_MARKET_VERSION) {
            genres.add(new BasicFilters.RABaseTriState("Этти", "el_2149"));
            genres.add(new BasicFilters.RABaseTriState("Юри", "el_2123"));
        }

        filterGroups.add(genres);
        filterGroups.add(categories);

    }

    @NonNull
    @Override
    public List<FilterGroup> getFilters() {
        return filterGroups;
    }

    private class ReadmangaGenre extends Genre {

        private String link;

        private ReadmangaGenre(final String name, final String link) {
            super(name);
            this.link = link;
        }

        public String getLink() {
            return link;
        }

        public void setLink(final String link) {
            this.link = link;
        }
    }

    private List<Genre> genres = new ArrayList<>();

    {
        genres.add(new ReadmangaGenre("Все", "/list?sortType="));
        genres.add(new ReadmangaGenre("Арт", "/list/genre/art"));
        genres.add(new ReadmangaGenre("Боевик", "/list/genre/action"));
        genres.add(new ReadmangaGenre("Боевые искусства", "/list/genre/martial_arts"));
        genres.add(new ReadmangaGenre("Вампиры", "/list/genre/vampires"));
        genres.add(new ReadmangaGenre("Гарем", "/list/genre/harem"));
        genres.add(new ReadmangaGenre("Гендерная интрига", "/list/genre/gender_intriga"));
        genres.add(new ReadmangaGenre("Героическое фэнтези", "/list/genre/heroic_fantasy"));
        genres.add(new ReadmangaGenre("Детектив", "/list/genre/detective"));
        genres.add(new ReadmangaGenre("Дзёсэй", "/list/genre/josei"));
        genres.add(new ReadmangaGenre("Додзинси", "/list/genre/doujinshi"));
        genres.add(new ReadmangaGenre("Драма", "/list/genre/drama"));
        genres.add(new ReadmangaGenre("Игра", "/list/genre/game"));
        genres.add(new ReadmangaGenre("История", "/list/genre/historical"));
        genres.add(new ReadmangaGenre("Кодомо", "/list/genre/codomo"));
        genres.add(new ReadmangaGenre("Комедия", "/list/genre/comedy"));
        genres.add(new ReadmangaGenre("Махо-сёдзё", "/list/genre/maho_shoujo"));
        genres.add(new ReadmangaGenre("Меха", "/list/genre/mecha"));
        genres.add(new ReadmangaGenre("Мистика", "/list/genre/mystery"));
        genres.add(new ReadmangaGenre("Научная фантастика", "/list/genre/sci_fi"));
        genres.add(new ReadmangaGenre("Повседневность", "/list/genre/natural"));
        genres.add(new ReadmangaGenre("Постапокалиптика", "/list/genre/postapocalypse"));
        genres.add(new ReadmangaGenre("Приключения", "/list/genre/adventure"));
        genres.add(new ReadmangaGenre("Психология", "/list/genre/psychological"));
        genres.add(new ReadmangaGenre("Романтика", "/list/genre/romance"));
        genres.add(new ReadmangaGenre("Самурайский боевик", "/list/genre/samurai"));
        genres.add(new ReadmangaGenre("Сверхъестественное", "/list/genre/supernatural"));
        genres.add(new ReadmangaGenre("Сёдзё", "/list/genre/shoujo"));
        genres.add(new ReadmangaGenre("Сёдзё-ай", "/list/genre/shoujo_ai"));
        genres.add(new ReadmangaGenre("Сёнэн", "/list/genre/shounen"));
        genres.add(new ReadmangaGenre("Сёнэн-ай", "/list/genre/shounen_ai"));
        genres.add(new ReadmangaGenre("Спорт", "/list/genre/sports"));
        genres.add(new ReadmangaGenre("Сэйнэн", "/list/genre/seinen"));
        genres.add(new ReadmangaGenre("Трагедия", "/list/genre/tragedy"));
        genres.add(new ReadmangaGenre("Триллер", "/list/genre/thriller"));
        genres.add(new ReadmangaGenre("Ужасы", "/list/genre/horror"));
        genres.add(new ReadmangaGenre("Фантастика", "/list/genre/fantastic"));
        genres.add(new ReadmangaGenre("Фэнтези", "/list/genre/fantasy"));
        genres.add(new ReadmangaGenre("Школа", "/list/genre/school"));
        if (!Constants.IS_MARKET_VERSION) {
            genres.add(new ReadmangaGenre("Этти", "/list/genre/ecchi"));
            genres.add(new ReadmangaGenre("Юри", "/list/genre/yuri"));
        }
    }

    @NonNull
    @Override
    public List<Genre> getGenres() {
        return genres;
    }

    @Nullable
    @Override
    public RequestPreprocessor getRequestPreprocessor() {
        return null;
    }

}
