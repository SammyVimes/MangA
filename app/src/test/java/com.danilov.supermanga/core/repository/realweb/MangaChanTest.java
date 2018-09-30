package com.danilov.supermanga.core.repository.realweb;

import com.danilov.supermanga.BuildConfig;
import com.danilov.supermanga.core.model.Manga;
import com.danilov.supermanga.core.model.MangaChapter;
import com.danilov.supermanga.core.repository.RepositoryEngine;
import com.danilov.supermanga.core.repository.RepositoryException;
import com.danilov.supermanga.core.repository.RepositoryHolder;
import com.danilov.supermanga.core.util.ServiceContainer;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.List;

/**
 * Created by Semyon on 19.03.2016.
 */
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21)
public class MangaChanTest {

    private RepositoryEngine engine;

    @Before
    public void setUp() {
        RepositoryHolder service = ServiceContainer.getService(RepositoryHolder.class);
        RepositoryEngine.Repository repository = service.valueOf(RepositoryEngine.DefaultRepository.MANGACHAN.toString());
        engine = repository.getEngine();
    }

    @After
    public void tearDown() {

    }

//    @Test
//    public void testGetSuggestions() {
//        List<MangaSuggestion> suggestions = null;
//        try {
//            suggestions = engine.getSuggestions("Naru");
//        } catch (RepositoryException e) {
//            Assert.fail("Should not fail: " + e.getMessage());
//        }
//        Assert.assertNotNull(suggestions);
//        Assert.assertTrue(!suggestions.isEmpty());
//    }

    @Test
    public void testQueryRepository() {
        List<Manga> searchResults = null;
        try {
            searchResults = engine.queryRepository("Dragon", Collections.emptyList());
        } catch (RepositoryException e) {
            Assert.fail("Should not fail: " + e.getMessage());
        }
        Assert.assertNotNull(searchResults);
        Assert.assertTrue(!searchResults.isEmpty());
    }

    @Test
    public void testQueryForMangaDescription() {
        List<Manga> searchResults = null;
        try {
            searchResults = engine.queryRepository("Dragon", Collections.emptyList());
        } catch (RepositoryException e) {
            Assert.fail("Should not fail: " + e.getMessage());
        }
        Assert.assertNotNull(searchResults);
        Assert.assertTrue(!searchResults.isEmpty());
        Manga manga = searchResults.get(0);
        try {
            boolean success = engine.queryForMangaDescription(manga);
            Assert.assertTrue(success);
        } catch (RepositoryException e) {
            Assert.fail("Should not fail: " + e.getMessage());
        }
    }

    @Test
    public void testQueryForMangaChapters() {
        List<Manga> searchResults = null;
        try {
            searchResults = engine.queryRepository("Dragon", Collections.emptyList());
        } catch (RepositoryException e) {
            Assert.fail("Should not fail: " + e.getMessage());
        }
        Assert.assertNotNull(searchResults);
        Assert.assertTrue(!searchResults.isEmpty());
        Manga manga = searchResults.get(0);
        try {
            boolean success = engine.queryForChapters(manga);
            Assert.assertTrue(success);
        } catch (RepositoryException e) {
            Assert.fail("Should not fail: " + e.getMessage());
        }
    }

    @Test
    public void testQueryForChapterImages() {
        List<Manga> searchResults = null;
        try {
            searchResults = engine.queryRepository("Dragon", Collections.emptyList());
        } catch (RepositoryException e) {
            Assert.fail("Should not fail: " + e.getMessage());
        }
        Assert.assertNotNull(searchResults);
        Assert.assertTrue(!searchResults.isEmpty());
        Manga manga = searchResults.get(0);
        try {
            boolean success = engine.queryForChapters(manga);
            Assert.assertTrue(success);
        } catch (RepositoryException e) {
            Assert.fail("Should not fail: " + e.getMessage());
        }
        List<MangaChapter> chapters = manga.getChapters();
        Assert.assertNotNull(chapters);
        Assert.assertTrue(chapters.size() > 0);
        MangaChapter mangaChapter = chapters.get(0);
        try {
            List<String> chapterImages = engine.getChapterImages(mangaChapter);
            Assert.assertTrue(chapterImages.size() > 0);
        } catch (RepositoryException e) {
            Assert.fail("Should not fail: " + e.getMessage());
        }
    }

}
