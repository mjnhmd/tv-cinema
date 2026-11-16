package com.fongmi.android.tv.ui.rex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.bean.RexSection;
import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.utils.Prefers;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import fi.iki.elonen.NanoHTTPD;

@RunWith(AndroidJUnit4.class)
public final class CinemaFeedLoaderAndroidTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private Object savedBase;
    private Object savedKey;
    private FeedServer server;
    private RexFeedLoader loader;

    @Before public void setup() throws Exception {
        assertEquals("com.fongmi.android.tv.sourceprobe", instrumentation.getTargetContext().getPackageName());
        server = new FeedServer();
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);
        main(() -> {
            savedBase = Prefers.getPrefers().getAll().get("browse_bridge_base");
            savedKey = Prefers.getPrefers().getAll().get("browse_bridge_sk");
            Setting.putBridgeBase("http://127.0.0.1:" + server.getListeningPort());
            Setting.putBridgeSk("local-fixture");
            loader = new RexFeedLoader(true);
        });
    }

    @After public void cleanup() {
        if (server != null) server.release.countDown();
        main(() -> {
            if (loader != null) loader.close();
            if (savedBase == null) Prefers.remove("browse_bridge_base"); else Prefers.put("browse_bridge_base", savedBase);
            if (savedKey == null) Prefers.remove("browse_bridge_sk"); else Prefers.put("browse_bridge_sk", savedKey);
        });
        if (server != null) server.stop();
    }

    @Test public void limitsNetworkFanoutAndDeliversAllFeedsInDeclaredOrder() throws Exception {
        List<RexSection> sections = sections("bounded", 8);
        AtomicInteger delivered = new AtomicInteger();
        CountDownLatch complete = new CountDownLatch(1);
        main(() -> loader.load(sections, results -> {
            if (!loader.isLoading()) {
                assertEquals(sections.stream().map(RexSection::getKey).toList(),
                        results.stream().map(result -> result.getSection().getKey()).toList());
                delivered.set(results.size());
                complete.countDown();
            }
        }));
        assertTrue(server.firstThree.await(3, TimeUnit.SECONDS));
        SystemClock.sleep(250);
        assertEquals("Only three requests may start before a slot completes", 3, server.requests.get());
        server.release.countDown();
        assertTrue("All queued sections must eventually arrive", complete.await(8, TimeUnit.SECONDS));
        assertEquals(8, delivered.get());
        assertTrue("Active HTTP work is bounded", server.maximum.get() <= 3);
        System.out.println("CINEMA_PERFORMANCE feed_max_concurrent=" + server.maximum.get());
    }

    @Test public void closingDropsQueuedSectionsAndLateCallbacks() throws Exception {
        AtomicInteger delivered = new AtomicInteger();
        main(() -> loader.load(sections("cancelled", 8), results -> delivered.incrementAndGet()));
        assertTrue(server.firstThree.await(3, TimeUnit.SECONDS));
        main(loader::close);
        server.release.countDown();
        SystemClock.sleep(500);
        assertEquals(3, server.requests.get());
        assertEquals(0, delivered.get());
        main(() -> assertFalse(loader.isLoading()));
    }

    @Test public void expiredSectionReleasesItsSlotWithoutDiscardingQueuedSections() throws Exception {
        CountDownLatch complete = new CountDownLatch(1);
        AtomicInteger finalCount = new AtomicInteger();
        main(() -> loader.load(sections("expired", 8), results -> {
            if (!loader.isLoading()) {
                finalCount.set(results.size());
                complete.countDown();
            }
        }));
        assertTrue(server.firstThree.await(3, TimeUnit.SECONDS));
        main(() -> {
            try {
                Field field = RexFeedLoader.class.getDeclaredField("timeouts");
                field.setAccessible(true);
                @SuppressWarnings("unchecked") Map<String, Runnable> timeouts = (Map<String, Runnable>) field.get(loader);
                Runnable expired = timeouts.values().iterator().next();
                expired.run();
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
        long deadline = SystemClock.elapsedRealtime() + 3_000;
        while (server.requests.get() < 4 && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50);
        assertEquals("An expired slot starts the next queued section", 4, server.requests.get());
        server.release.countDown();
        assertTrue(complete.await(8, TimeUnit.SECONDS));
        assertEquals("Only the timed-out section may be absent", 7, finalCount.get());
    }

    @Test public void discoveryRetainsItsOriginalFanout() throws Exception {
        main(() -> {
            loader.close();
            loader = new RexFeedLoader();
            loader.load(sections("discovery", 8), results -> {});
            try {
                Field field = RexFeedLoader.class.getDeclaredField("inFlight");
                field.setAccessible(true);
                assertEquals("Compact-home throttling must not serialize the discovery page", 8,
                        ((java.util.Set<?>) field.get(loader)).size());
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
        assertTrue(server.firstThree.await(3, TimeUnit.SECONDS));
    }

    private List<RexSection> sections(String prefix, int count) {
        List<RexSection> sections = new ArrayList<>();
        for (int index = 0; index < count; index++) sections.add(RexSection.bridge(prefix + index, 0, prefix + index));
        return sections;
    }

    private void main(Runnable action) { instrumentation.runOnMainSync(action); }

    private static final class FeedServer extends NanoHTTPD {
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch firstThree = new CountDownLatch(3);
        final AtomicInteger requests = new AtomicInteger();
        final AtomicInteger active = new AtomicInteger();
        final AtomicInteger maximum = new AtomicInteger();

        FeedServer() { super("127.0.0.1", 0); }

        @Override public Response serve(IHTTPSession session) {
            int current = active.incrementAndGet();
            maximum.accumulateAndGet(current, Math::max);
            requests.incrementAndGet();
            firstThree.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
                String name = session.getUri().substring(session.getUri().lastIndexOf('/') + 1);
                return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"items\":[{\"doubanId\":\""
                        + name + "\",\"title\":\"Fixture film\",\"images\":{\"poster\":\"https://example.test/poster.jpg\"}}]}");
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return newFixedLengthResponse(Response.Status.SERVICE_UNAVAILABLE, "text/plain", "cancelled");
            } finally { active.decrementAndGet(); }
        }
    }
}
