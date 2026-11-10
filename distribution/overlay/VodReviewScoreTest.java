package com.fongmi.android.tv.bean;

import com.google.gson.Gson;

import org.junit.Test;
import org.simpleframework.xml.core.Persister;

import static org.junit.Assert.assertEquals;

public class VodReviewScoreTest {
    private final Gson gson = new Gson();

    @Test public void acceptsExplicitStringAndNumericTenPointScores() {
        for (String key : new String[]{"vod_score", "vod_douban_score", "douban_score"}) {
            assertEquals(key, "8.6", gson.fromJson("{\"" + key + "\":\" 8.6 \"}", Vod.class).getScore());
            assertEquals(key, "9.2", gson.fromJson("{\"" + key + "\":9.2}", Vod.class).getScore());
        }
    }

    @Test public void invalidOptionalScoresDoNotBreakTheResult() {
        for (String score : new String[]{"null", "true", "[]", "{}", "\"暂无评分\"", "0", "-1", "11", "100",
                "\"NaN\"", "\"Infinity\"", "\"9.9/10\"", "\"1e1\"", "\"\""}) {
            Vod vod = gson.fromJson("{\"vod_score\":" + score + ",\"vod_year\":\"2026\"}", Vod.class);
            assertEquals(score, "", vod.getScore());
            assertEquals("2026", vod.getYear());
        }
    }

    @Test public void scoreDoesNotComeFromCertificationRemarksOrAmbiguousRating() {
        Vod vod = gson.fromJson("{\"vod_certification\":\"PG-13\",\"vod_remarks\":\"9.2分\","
                + "\"rating\":9.2,\"score\":8.6}", Vod.class);
        assertEquals("", vod.getScore());
        assertEquals("PG-13", vod.getContentRating());
        assertEquals("", new Vod().getScore());
    }

    @Test public void preservesScoreWhenSerializedOrReadFromXml() throws Exception {
        Vod vod = gson.fromJson("{\"douban_score\":\"10.0\"}", Vod.class);
        assertEquals("10.0", gson.fromJson(gson.toJson(vod), Vod.class).getScore());
        assertEquals("8.9", new Persister().read(Vod.class, "<vod><score>8.9</score></vod>").getScore());
    }
}
