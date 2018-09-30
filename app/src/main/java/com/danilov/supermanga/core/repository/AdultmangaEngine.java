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
import org.apache.http.client.ClientProtocolException;
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
public class AdultmangaEngine implements RepositoryEngine {

    private static final String TAG = "AdultmangaEngine";

//  ADULT
    private String baseSearchUri = "http://mintmanga.com/search/advanced";
    private String baseSuggestionUri = "http://mintmanga.com/search/suggestion?query=";
    public static final String baseUri = "http://mintmanga.com";

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

    public AdultmangaEngine() {
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
        AdultGenre genre = (AdultGenre) _genre;
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
    private String exclusionValue = "Автор ";
    private String exclusionValue2 = "Переводчик ";
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
                    MangaSuggestion mangaSuggestion = new MangaSuggestion(value, link, DefaultRepository.ADULTMANGA);
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
            Manga manga = new Manga(mangaName, uri, DefaultRepository.ADULTMANGA);
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
            Manga manga = new Manga(mangaName, uri, DefaultRepository.ADULTMANGA);
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

    private class AdultGenre extends Genre {

        private String link;

        private AdultGenre(final String name, final String link) {
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

    private List<FilterGroup> filterGroups = new ArrayList<>(2);

    {
        FilterGroup genres = new FilterGroup("Жанр", 41);

        genres.add(new BasicFilters.RABaseTriState("Арт", "el_2220"));
        genres.add(new BasicFilters.RABaseTriState("Бара", "el_1353"));
        genres.add(new BasicFilters.RABaseTriState("Боевик", "el_1346"));
        genres.add(new BasicFilters.RABaseTriState("Боевые искусства", "el_1334"));
        genres.add(new BasicFilters.RABaseTriState("Вампиры", "el_1339"));
        genres.add(new BasicFilters.RABaseTriState("Гарем", "el_1333"));
        genres.add(new BasicFilters.RABaseTriState("Гендерная интрига", "el_1347"));
        genres.add(new BasicFilters.RABaseTriState("Героическое фэнтези", "el_1337"));
        genres.add(new BasicFilters.RABaseTriState("Детектив", "el_1343"));
        genres.add(new BasicFilters.RABaseTriState("Дзёсэй", "el_1349"));
        genres.add(new BasicFilters.RABaseTriState("Додзинси", "el_1332"));
        genres.add(new BasicFilters.RABaseTriState("Драма", "el_1310"));
        genres.add(new BasicFilters.RABaseTriState("История", "el_1311"));
        genres.add(new BasicFilters.RABaseTriState("Киберпанк", "el_1351"));
        genres.add(new BasicFilters.RABaseTriState("Комедия", "el_1328"));
        genres.add(new BasicFilters.RABaseTriState("Меха", "el_1318"));
        genres.add(new BasicFilters.RABaseTriState("Мистика", "el_1324"));
        genres.add(new BasicFilters.RABaseTriState("Научная фантастика", "el_1325"));
        genres.add(new BasicFilters.RABaseTriState("Повседневность", "el_1327"));
        genres.add(new BasicFilters.RABaseTriState("Постапокалиптика", "el_1342"));
        genres.add(new BasicFilters.RABaseTriState("Приключения", "el_1322"));
        genres.add(new BasicFilters.RABaseTriState("Психология", "el_1335"));
        genres.add(new BasicFilters.RABaseTriState("Романтика", "el_1313"));
        genres.add(new BasicFilters.RABaseTriState("Самурайский боевик", "el_1316"));
        genres.add(new BasicFilters.RABaseTriState("Сверхъестественное", "el_1350"));
        genres.add(new BasicFilters.RABaseTriState("Сёдзё", "el_1314"));
        genres.add(new BasicFilters.RABaseTriState("Сёдзё-ай", "el_1320"));
        genres.add(new BasicFilters.RABaseTriState("Сёнэн", "el_1326"));
        genres.add(new BasicFilters.RABaseTriState("Сёнэн-ай", "el_1330"));
        genres.add(new BasicFilters.RABaseTriState("Спорт", "el_1321"));
        genres.add(new BasicFilters.RABaseTriState("Сэйнэн", "el_1329"));
        genres.add(new BasicFilters.RABaseTriState("Трагедия", "el_1344"));
        genres.add(new BasicFilters.RABaseTriState("Триллер", "el_1341"));
        genres.add(new BasicFilters.RABaseTriState("Ужасы", "el_1317"));
        genres.add(new BasicFilters.RABaseTriState("Фантастика", "el_1331"));
        genres.add(new BasicFilters.RABaseTriState("Фэнтези", "el_1323"));
        genres.add(new BasicFilters.RABaseTriState("Школа", "el_1319"));

        if (!Constants.IS_MARKET_VERSION) {
            genres.add(new BasicFilters.RABaseTriState("Эротика", "el_1340"));
            genres.add(new BasicFilters.RABaseTriState("Этти", "el_1354"));
            genres.add(new BasicFilters.RABaseTriState("Юри", "el_1315"));
            genres.add(new BasicFilters.RABaseTriState("Яой", "el_1336"));
        }

        FilterGroup categories = new FilterGroup("Категории", 15);

        categories.add(new BasicFilters.RABaseTriState("В цвете", "el_4614"));
        categories.add(new BasicFilters.RABaseTriState("Веб", "el_1355"));
        categories.add(new BasicFilters.RABaseTriState("Ёнкома", "el_2741"));
        categories.add(new BasicFilters.RABaseTriState("Комикс западный", "el_1903"));
        categories.add(new BasicFilters.RABaseTriState("Комикс русский", "el_2173"));
        categories.add(new BasicFilters.RABaseTriState("Манхва", "el_1873"));
        categories.add(new BasicFilters.RABaseTriState("Маньхуа", "el_1875"));
        categories.add(new BasicFilters.RABaseTriState("Не Яой", "el_1874"));
        categories.add(new BasicFilters.RABaseTriState("Сборник", "el_1348"));
        categories.add(new BasicFilters.RABaseTriState("Высокий рейтинг", "s_high_rate"));
        categories.add(new BasicFilters.RABaseTriState("Сингл", "s_single"));

        if (!Constants.IS_MARKET_VERSION) {
            categories.add(new BasicFilters.RABaseTriState("Для взрослых", "s_mature"));
        }
        categories.add(new BasicFilters.RABaseTriState("Завершенная", "s_completed"));
        categories.add(new BasicFilters.RABaseTriState("Переведено", "s_translated"));
        categories.add(new BasicFilters.RABaseTriState("Ожидает загрузки", "s_wait_upload"));


        FilterGroup agelings = new FilterGroup("Возрастные рекомендации", 3);

        agelings.add(new BasicFilters.RABaseTriState("NC-17", "el_3969"));
        agelings.add(new BasicFilters.RABaseTriState("R", "el_3968"));
        if (!Constants.IS_MARKET_VERSION) {
            agelings.add(new BasicFilters.RABaseTriState("R18+", "el_3990"));
        }

        filterGroups.add(genres);
        filterGroups.add(categories);
        filterGroups.add(agelings);

    }

    @NonNull
    @Override
    public List<FilterGroup> getFilters() {
        return filterGroups;
    }


    private List<Genre> genres = new ArrayList<>();

    {
        genres.add(new AdultGenre("Все", "/list?sortType="));
        genres.add(new AdultGenre("Арт", "/list/genre/art"));
        genres.add(new AdultGenre("Бара", "/list/genre/bara"));
        genres.add(new AdultGenre("Боевик", "/list/genre/action"));
        genres.add(new AdultGenre("Боевые искусства", "/list/genre/martial_arts"));
        genres.add(new AdultGenre("Вампиры", "/list/genre/vampires"));
        genres.add(new AdultGenre("Гарем", "/list/genre/harem"));
        genres.add(new AdultGenre("Гендерная интрига", "/list/genre/gender_intriga"));
        genres.add(new AdultGenre("Героическое фэнтези", "/list/genre/heroic_fantasy"));
        genres.add(new AdultGenre("Детектив", "/list/genre/detective"));
        genres.add(new AdultGenre("Дзёсэй", "/list/genre/josei"));
        genres.add(new AdultGenre("Додзинси", "/list/genre/doujinshi"));
        genres.add(new AdultGenre("Драма", "/list/genre/drama"));
        genres.add(new AdultGenre("История", "/list/genre/historical"));
        genres.add(new AdultGenre("Киберпанк", "/list/genre/cyberpunk"));
        genres.add(new AdultGenre("Комедия", "/list/genre/comedy"));
        genres.add(new AdultGenre("Меха", "/list/genre/mecha"));
        genres.add(new AdultGenre("Мистика", "/list/genre/mystery"));
        genres.add(new AdultGenre("Научная фантастика", "/list/genre/sci_fi"));
        genres.add(new AdultGenre("Повседневность", "/list/genre/natural"));
        genres.add(new AdultGenre("Постапокалиптика", "/list/genre/postapocalypse"));
        genres.add(new AdultGenre("Приключения", "/list/genre/adventure"));
        genres.add(new AdultGenre("Психология", "/list/genre/psychological"));
        genres.add(new AdultGenre("Романтика", "/list/genre/romance"));
        genres.add(new AdultGenre("Самурайский боевик", "/list/genre/samurai"));
        genres.add(new AdultGenre("Сверхъестественное", "/list/genre/supernatural"));
        genres.add(new AdultGenre("Сёдзё", "/list/genre/shoujo"));
        genres.add(new AdultGenre("Сёдзё-ай", "/list/genre/shoujo_ai"));
        genres.add(new AdultGenre("Сёнэн", "/list/genre/shounen"));
        genres.add(new AdultGenre("Сёнэн-ай", "/list/genre/shounen_ai"));
        genres.add(new AdultGenre("Спорт", "/list/genre/sports"));
        genres.add(new AdultGenre("Сэйнэн", "/list/genre/seinen"));
        genres.add(new AdultGenre("Трагедия", "/list/genre/tragedy"));
        genres.add(new AdultGenre("Триллер", "/list/genre/thriller"));
        genres.add(new AdultGenre("Ужасы", "/list/genre/horror"));
        genres.add(new AdultGenre("Фантастика", "/list/genre/fantastic"));
        genres.add(new AdultGenre("Фэнтези", "/list/genre/fantasy"));
        genres.add(new AdultGenre("Школа", "/list/genre/school"));

        if (!Constants.IS_MARKET_VERSION) {
            genres.add(new AdultGenre("Эротика", "/list/genre/erotica"));
            genres.add(new AdultGenre("Этти", "/list/genre/ecchi"));
            genres.add(new AdultGenre("Юри", "/list/genre/yuri"));
            genres.add(new AdultGenre("Яой", "/list/genre/yaoi"));
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
