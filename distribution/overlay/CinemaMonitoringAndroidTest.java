package com.fongmi.android.tv.monitoring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.test.CorePlaybackActivity;
import com.tencent.bugly.CrashModule;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class CinemaMonitoringAndroidTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private CorePlaybackActivity activity;

    @Before public void setup() {
        assertEquals("Use the isolated test app only", "com.fongmi.android.tv.sourceprobe",
                instrumentation.getTargetContext().getPackageName());
        assertFalse("The isolated app must not initialize production Bugly", CrashModule.getInstance().hasInitialized());
    }

    @After public void cleanup() {
        if (activity != null) instrumentation.runOnMainSync(activity::finish);
        CinemaMonitoring.setTestSink(null);
    }

    @Test public void uiStallCapturesTheBlockedMainThreadBeforeItRecovers() throws Exception {
        CountDownLatch incident = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch released = new CountDownLatch(1);
        AtomicReference<String> message = new AtomicReference<>();
        AtomicReference<StackTraceElement[]> stack = new AtomicReference<>();
        CinemaMonitoring.setTestSink((value, frames) -> {
            if (value.contains("event=ui_stall")) {
                message.set(value);
                stack.set(frames);
                incident.countDown();
            }
        });
        activity = (CorePlaybackActivity) instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), CorePlaybackActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        SystemClock.sleep(300);
        new Handler(Looper.getMainLooper()).post(() -> blockMain(entered, released));
        assertTrue("Fixture must block the main thread", entered.await(2, TimeUnit.SECONDS));
        assertTrue("Stall must be captured while still blocked", incident.await(4_500, TimeUnit.MILLISECONDS));
        assertFalse("Report must not wait for the main thread to recover", released.await(0, TimeUnit.MILLISECONDS));
        assertTrue(message.get().contains("screen=CorePlaybackActivity"));
        assertNotNull(stack.get());
        assertTrue("Capture the actual blocked main-thread stack",
                Arrays.stream(stack.get()).anyMatch(frame -> frame.getMethodName().equals("blockMain")));
        assertTrue(released.await(3, TimeUnit.SECONDS));
    }

    @Test public void slowSourceIsReportedOnceAndCancellationDoesNotReport() throws Exception {
        AtomicInteger events = new AtomicInteger();
        CountDownLatch incident = new CountDownLatch(1);
        CinemaMonitoring.setTestSink((value, stack) -> {
            if (value.contains("event=slow_source")) {
                assertTrue(value.contains("type=3"));
                assertTrue(value.contains("finished=false"));
                events.incrementAndGet();
                incident.countDown();
            }
        });
        AtomicReference<CinemaSourceSpan> active = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            CinemaSourceSpan cancelled = new CinemaSourceSpan(7, 100);
            cancelled.cancel();
            active.set(new CinemaSourceSpan(3, 150));
        });
        assertTrue(incident.await(2, TimeUnit.SECONDS));
        instrumentation.runOnMainSync(() -> active.get().finish(true));
        SystemClock.sleep(250);
        assertEquals("A timed-out phase must not be reported again when it finishes", 1, events.get());
    }

    @Test public void sourceFailureTriggersANonfatalIncident() throws Exception {
        CountDownLatch incident = new CountDownLatch(1);
        AtomicReference<String> message = new AtomicReference<>();
        CinemaMonitoring.setTestSink((value, stack) -> {
            if (value.contains("event=source_error")) {
                message.set(value);
                incident.countDown();
            }
        });
        instrumentation.runOnMainSync(() -> new CinemaSourceSpan(4, 10_000).finish(false));
        assertTrue("A failed source must be observable without a crash", incident.await(2, TimeUnit.SECONDS));
        assertTrue(message.get().contains("type=4"));
        assertFalse("The event contains no playback URL", message.get().contains("http"));
    }

    private static void blockMain(CountDownLatch entered, CountDownLatch released) {
        entered.countDown();
        SystemClock.sleep(5_000);
        released.countDown();
    }
}
