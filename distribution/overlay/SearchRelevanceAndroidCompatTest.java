package com.fongmi.android.tv.source;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class SearchRelevanceAndroidCompatTest {
    @Test
    public void initializesAndMatchesTitlesOnAndroid() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(context.getPackageName().equals("com.fongmi.android.tv.preview")
                || context.getPackageName().equals("com.fongmi.android.tv.sourceprobe"));

        SearchRelevance.Query chinese = new SearchRelevance.Query("庆余年2", List.of(), "2024", null);
        assertTrue(SearchRelevance.evaluate("庆余年 第二季", "2024", chinese).strict());
        assertFalse(SearchRelevance.evaluate("庆余年 第一季", "2024", chinese).relevant());
        assertFalse(SearchRelevance.evaluate("庆余年 第二季", "2019", chinese).relevant());

        assertTrue(SearchRelevance.evaluate("Rocky 2", "", SearchRelevance.Query.of("Rocky II")).strict());
        assertFalse(SearchRelevance.evaluate("Rocky II", "", SearchRelevance.Query.of("Rocky")).strict());
        assertTrue(SearchRelevance.evaluate("1917", "", SearchRelevance.Query.of("1917")).strict());
        assertFalse(SearchRelevance.evaluate("Titanic", "", SearchRelevance.Query.of("It")).relevant());
    }
}
