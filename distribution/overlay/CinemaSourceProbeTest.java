package com.fongmi.android.tv.api.config;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CinemaSourceProbeTest {

    private static final String GOOD = "{\"sites\":[{\"key\":\"a\",\"name\":\"a\",\"type\":3,\"api\":\"csp_A\"}]}";
    private static final String NO_SITES = "{\"sites\":[]}";
    private static final String NOT_JSON = "<html>blocked</html>";
    private static final String COMMENTED = "{\n\"sites\":[\n//{\"key\":\"b\",\"name\":\"b\",\"type\":3,\"api\":\"csp_B\"},\n{\"key\":\"a\",\"name\":\"a\",\"type\":3,\"api\":\"csp_A\"}\n]\n}";

    private static CinemaSourceProbe.Target target(String label) {
        return new CinemaSourceProbe.Target(label, "https://" + label + ".example/tv");
    }

    private static CinemaSourceProbe.Fetcher fetcher(long millis, String body) {
        return url -> {
            sleep(millis);
            return body;
        };
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    public void picksFastestValidEndpoint() {
        CinemaSourceProbe.Fetcher fetcher = url -> {
            sleep(url.contains("slow") ? 160 : 10);
            return GOOD;
        };
        CinemaSourceProbe.Report report = CinemaSourceProbe.probe(
                List.of(target("slow"), target("fast")), fetcher, 5_000, 0);
        assertTrue(report.ok());
        assertEquals("fast", report.best().label());
        assertEquals(GOOD, report.body());
        assertEquals(2, report.samples().size());
    }

    @Test
    public void skipsInvalidAndKeepsLaterValidEndpoint() {
        CinemaSourceProbe.Fetcher fetcher = url -> {
            if (url.contains("empty")) return NO_SITES;
            if (url.contains("html")) return NOT_JSON;
            return GOOD;
        };
        CinemaSourceProbe.Report report = CinemaSourceProbe.probe(
                List.of(target("empty"), target("html"), target("good")), fetcher, 5_000, 0);
        assertTrue(report.ok());
        assertEquals("good", report.best().label());
        assertFalse(report.samples().get(0).ok());
        assertEquals("no-sites", report.samples().get(0).detail());
        assertFalse(report.samples().get(1).ok());
    }

    @Test
    public void reportsFailureWhenEveryEndpointFails() {
        CinemaSourceProbe.Fetcher fetcher = url -> {
            throw new java.io.IOException("refused");
        };
        CinemaSourceProbe.Report report = CinemaSourceProbe.probe(
                List.of(target("one"), target("two")), fetcher, 5_000, 0);
        assertFalse(report.ok());
        assertNull(report.best());
        assertEquals("", report.body());
        assertEquals(2, report.samples().size());
        assertEquals("refused", report.samples().get(0).detail());
    }

    @Test
    public void stopsAfterAGoodEnoughEndpoint() {
        CinemaSourceProbe.Fetcher fetcher = url -> {
            sleep(5);
            return GOOD;
        };
        CinemaSourceProbe.Report report = CinemaSourceProbe.probe(
                List.of(target("first"), target("second"), target("third")), fetcher, 5_000, 1_000);
        assertTrue(report.ok());
        assertEquals("first", report.best().label());
        assertEquals(1, report.samples().size());
    }

    @Test
    public void timesOutStalledEndpointAndFallsThrough() {
        CinemaSourceProbe.Fetcher fetcher = url -> {
            if (url.contains("stall")) {
                sleep(2_000);
                return GOOD;
            }
            return GOOD;
        };
        CinemaSourceProbe.Report report = CinemaSourceProbe.probe(
                List.of(target("stall"), target("ok")), fetcher, 150, 0);
        assertTrue(report.ok());
        assertEquals("ok", report.best().label());
        assertEquals("timeout", report.samples().get(0).detail());
    }

    @Test
    public void acceptsOfficialConfigWithCommentLines() {
        CinemaSourceProbe.Report report = CinemaSourceProbe.probe(
                List.of(target("official")), fetcher(0, COMMENTED), 5_000, 0);
        assertTrue(report.ok());
        assertFalse(report.body().contains("//{"));
        assertTrue(report.body().contains("\"key\":\"a\""));
    }

    @Test
    public void keepsSchemeSlashesInsideStrings() {
        String body = "{\"sites\":[{\"key\":\"a\",\"api\":\"https://x.example/a\"}]}";
        assertTrue(CinemaSourceProbe.stripComments(body).contains("https://x.example/a"));
    }
}
