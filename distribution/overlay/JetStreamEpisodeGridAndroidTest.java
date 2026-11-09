package com.fongmi.android.tv.ui.custom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;

import com.fongmi.android.tv.test.CorePlaybackActivity;
import com.fongmi.android.tv.test.NativeUiEvidence;
import com.fongmi.android.tv.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

@RunWith(AndroidJUnit4.class)
public final class JetStreamEpisodeGridAndroidTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private CorePlaybackActivity activity;
    private JetStreamEpisodeGrid grid;
    private Button before;
    private Button after;
    private final AtomicInteger clicked = new AtomicInteger(-1);

    @Before
    public void setup() {
        assertEquals("com.fongmi.android.tv.sourceprobe", instrumentation.getTargetContext().getPackageName());
        activity = (CorePlaybackActivity) instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), CorePlaybackActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.runOnMainSync(() -> {
            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            before = new Button(activity);
            before.setId(View.generateViewId());
            before.setText("Before episodes");
            before.setFocusableInTouchMode(true);
            after = new Button(activity);
            after.setId(View.generateViewId());
            after.setText("Episode ranges");
            after.setFocusableInTouchMode(true);
            grid = new JetStreamEpisodeGrid(activity);
            grid.setId(View.generateViewId());
            grid.setNextFocusUpId(before.getId());
            grid.setNextFocusDownId(after.getId());
            before.setNextFocusDownId(grid.getId());
            after.setNextFocusUpId(grid.getId());
            content.addView(before, new LinearLayout.LayoutParams(-1, dp(48)));
            content.addView(grid, new LinearLayout.LayoutParams(-1, -2));
            content.addView(after, new LinearLayout.LayoutParams(-1, dp(48)));
            activity.setContentView(content);
            grid.setOnChipClickListener(clicked::set);
            before.requestFocus();
        });
        await(() -> activity.hasWindowFocus(), "window focus");
    }

    @After
    public void cleanup() {
        if (activity != null) instrumentation.runOnMainSync(activity::finish);
    }

    @Test(timeout = 60000)
    public void dpadMovesAcrossRowsWithoutPlayingAndRangeJumpFocusesRequestedEpisode() {
        List<String> episodes = new ArrayList<>();
        for (int index = 1; index <= 100; index++) episodes.add("第" + index + "集");
        instrumentation.runOnMainSync(() -> grid.setItems(episodes, 0));
        await(() -> grid.getHeight() > 0, "grid laid out");
        instrumentation.runOnMainSync(() -> assertTrue("grid must accept initial focus", grid.requestFocus()));
        await(() -> grid.hasFocus() && grid.getFocusedPosition() == 0, "first episode focus");
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        await(() -> grid.getFocusedPosition() == 1, "right episode");
        int firstTop = episodeBounds("第2集").top;
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        await(() -> grid.getFocusedPosition() > 1, "next row");
        int secondIndex = grid.getFocusedPosition();
        int secondTop = episodeBounds(episodes.get(secondIndex)).top;
        assertTrue("Down must move to the row below", secondTop > firstTop);
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        await(() -> grid.getFocusedPosition() > secondIndex, "third row");
        int thirdIndex = grid.getFocusedPosition();
        assertTrue(episodeBounds(episodes.get(thirdIndex)).top > secondTop);
        assertEquals("Moving focus must not play", -1, clicked.get());
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(thirdIndex, clicked.get());
        instrumentation.runOnMainSync(() -> {
            after.requestFocus();
            grid.setFocusedPosition(80);
        });
        await(() -> grid.hasFocus() && grid.getFocusedPosition() == 80, "range jump to episode 81");
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        await(() -> grid.getFocusedPosition() > 80, "row after range jump");
        assertEquals("Moving focus must not play another episode", thirdIndex, clicked.get());
    }

    @Test(timeout = 60000)
    public void dpadLeavesOnlyAtFirstAndLastRowsAndKeepsSelectedEpisode() {
        List<String> episodes = new ArrayList<>();
        for (int index = 1; index <= 20; index++) episodes.add("第" + index + "集");
        instrumentation.runOnMainSync(() -> grid.setItems(episodes, 1));
        await(() -> grid.getHeight() > 0, "grid laid out");
        instrumentation.runOnMainSync(() -> grid.requestFocus());
        await(() -> grid.getFocusedPosition() == 1, "selected episode focus");
        press(KeyEvent.KEYCODE_DPAD_UP);
        await(before::hasFocus, "up from first row");
        instrumentation.runOnMainSync(() -> grid.setFocusedPosition(19));
        await(() -> grid.getFocusedPosition() == 19, "last episode focus");
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        await(after::hasFocus, "down from last row");
        assertEquals("Moving and jumping do not change selected episode", 1, grid.getSelectedPosition());
        assertEquals("Moving and jumping do not invoke playback", -1, clicked.get());
    }

    @Test(timeout = 60000)
    public void sizesMixedTitlesNaturallyAndRepacksWhenWidthChanges() {
        List<String> episodes = List.of("第1集", "Episode 12 · 导演剪辑版", "纯享版", "第123集", "幕后花絮");
        instrumentation.runOnMainSync(() -> {
            grid.setLayoutParams(new LinearLayout.LayoutParams(dp(650), -2));
            grid.setItems(episodes, 0);
        });
        await(() -> grid.getWidth() == dp(650), "wide episode grid");
        Rect shortTitle = episodeBounds(episodes.get(0));
        Rect longTitle = episodeBounds(episodes.get(1));
        assertEquals("A longer title must not force a new row", shortTitle.top, longTitle.top);
        assertTrue("Longer text gets more width", longTitle.width() > shortTitle.width() * 2);
        assertEquals(shortTitle.top, episodeBounds(episodes.get(2)).top);
        NativeUiEvidence.capture("episode-flow-mixed");
        instrumentation.runOnMainSync(() -> grid.setLayoutParams(new LinearLayout.LayoutParams(dp(235), -2)));
        await(() -> grid.getWidth() == dp(235), "narrow episode grid");
        await(() -> episodeBounds(episodes.get(2)).top > episodeBounds(episodes.get(0)).top, "titles repacked");
        assertEquals(0, grid.getSelectedPosition());
        assertEquals(-1, clicked.get());
    }

    @Test(timeout = 60000)
    public void overlongTitleScrollsOnlyWhileFocusedWithoutPlaying() {
        String title = "[1.11GB]2026.10.24 喜剧之王单口季第三季 · 上半场 1080P WEB-DL H264 AAC "
                + "完整节目及特别加更字幕版本 · Episode 20261024 · 高清完整版";
        instrumentation.runOnMainSync(() -> {
            before.setText("选集");
            after.setText("集数范围  1–20  21–40");
            grid.setLayoutParams(new LinearLayout.LayoutParams(dp(650), -2));
            grid.setItems(List.of("第1集", "第2集 · 加更", "幕后花絮", title, "正片"), 0);
        });
        await(() -> grid.getHeight() > 0, "mixed overflow grid");
        Rect bounds = episodeBounds(title);
        assertTrue("Only a row-wide title is capped: " + bounds + " grid=" + grid.getWidth(),
                bounds.width() >= grid.getWidth() - dp(34));
        NativeUiEvidence.capture("episode-flow-overflow-idle");
        instrumentation.runOnMainSync(() -> grid.setFocusedPosition(3));
        await(() -> grid.getFocusedPosition() == 3, "overlong episode focus");
        SystemClock.sleep(1700);
        assertTrue("Focused text must move automatically", changedPixels(bounds, 650) > 100);
        NativeUiEvidence.capture("episode-flow-overflow-focused");
        instrumentation.runOnMainSync(() -> before.requestFocus());
        await(before::hasFocus, "leave overflow title");
        SystemClock.sleep(800);
        assertEquals("Unfocused text must stop scrolling", 0, changedPixels(bounds, 650));
        assertEquals("Focus alone does not select another episode", 0, grid.getSelectedPosition());
        assertEquals("Focus alone does not start playback", -1, clicked.get());
        press(KeyEvent.KEYCODE_BACK);
        await(activity::isFinishing, "Back leaves episode screen");
    }

    @Test(timeout = 60000)
    public void largerSystemFontRecalculatesTextWidthsAndRows() {
        List<String> episodes = List.of("第1集", "Episode 12 · 导演剪辑版", "纯享版", "花絮");
        instrumentation.runOnMainSync(() -> {
            grid.setLayoutParams(new LinearLayout.LayoutParams(dp(350), -2));
            grid.setItems(episodes, 0);
        });
        await(() -> grid.getWidth() == dp(350), "original font layout");
        Rect original = episodeBounds(episodes.get(1));
        assertEquals(episodeBounds(episodes.get(0)).top, original.top);
        instrumentation.runOnMainSync(() -> {
            Configuration configuration = new Configuration();
            configuration.fontScale = 1.6f;
            ContextThemeWrapper context = new ContextThemeWrapper(activity, R.style.Theme_App);
            context.applyOverrideConfiguration(configuration);
            grid = new JetStreamEpisodeGrid(context);
            grid.setItems(episodes, 0);
            grid.setOnChipClickListener(clicked::set);
            activity.setContentView(grid, new LinearLayout.LayoutParams(dp(350), -2));
        });
        await(() -> grid.getWidth() == dp(350) && grid.getHeight() > 0, "larger font layout");
        Rect scaled = episodeBounds(episodes.get(1));
        assertTrue("Larger font must increase measured title width", scaled.width() > original.width() * 1.3f);
        assertTrue("Larger text must wrap before truncating", scaled.top > episodeBounds(episodes.get(0)).top);
        assertEquals(-1, clicked.get());
    }

    @Test(timeout = 30000)
    public void playbackSelectionUpdatesDoNotStealBrowseFocus() {
        List<String> episodes = new ArrayList<>();
        for (int index = 1; index <= 100; index++) episodes.add("第" + index + "集");
        instrumentation.runOnMainSync(() -> {
            grid.setItems(episodes, 0);
            grid.setFocusedPosition(11);
        });
        await(() -> grid.getFocusedPosition() == 11, "browsed episode");
        instrumentation.runOnMainSync(() -> grid.setSelectedPosition(45));
        instrumentation.waitForIdleSync();
        assertEquals("Playback updates do not move remote focus", 11, grid.getFocusedPosition());
        assertEquals(45, grid.getSelectedPosition());
        instrumentation.runOnMainSync(() -> grid.setItems(episodes, 46));
        instrumentation.waitForIdleSync();
        assertEquals("Same-list refresh does not move remote focus", 11, grid.getFocusedPosition());
        assertEquals(46, grid.getSelectedPosition());
        assertEquals(-1, clicked.get());
    }

    @Test(timeout = 30000)
    public void repeatedRangeRequestKeepsTheActualChipFocus() {
        instrumentation.runOnMainSync(() -> {
            grid.setItems(List.of("第1集", "第2集", "第3集"), 0);
            grid.setFocusedPosition(1);
        });
        await(() -> grid.getFocusedPosition() == 1, "requested episode");
        instrumentation.runOnMainSync(() -> grid.setFocusedPosition(1));
        instrumentation.waitForIdleSync();
        assertEquals("Already-focused targets stay ready", 1, grid.getFocusedPosition());
    }

    @Test(timeout = 60000)
    public void dpadScrollsAcrossUncomposedRowsAndReturnsWithoutPlaying() {
        List<String> episodes = new ArrayList<>();
        for (int index = 1; index <= 300; index++) episodes.add("第" + index + "集");
        instrumentation.runOnMainSync(() -> {
            grid.setLayoutParams(new LinearLayout.LayoutParams(dp(350), -2));
            grid.setItems(episodes, 0);
            grid.setFocusedPosition(0);
        });
        await(() -> grid.getFocusedPosition() == 0, "first row");
        for (int step = 0; step < 15; step++) {
            int previous = grid.getFocusedPosition();
            press(KeyEvent.KEYCODE_DPAD_DOWN);
            await(() -> grid.getFocusedPosition() > previous, "next lazily composed row " + step);
        }
        assertTrue(grid.getFocusedPosition() > 50);
        for (int step = 0; step < 15; step++) {
            int previous = grid.getFocusedPosition();
            press(KeyEvent.KEYCODE_DPAD_UP);
            await(() -> grid.getFocusedPosition() >= 0 && grid.getFocusedPosition() < previous,
                    "previous lazily composed row " + step);
        }
        assertEquals(0, grid.getSelectedPosition());
        assertEquals(-1, clicked.get());
    }

    @Test(timeout = 60000)
    public void overlongTitleStopsWhenAnotherWindowTakesFocus() {
        String part = "2026 喜剧之王单口季 · 高清导演剪辑与完整加更字幕 · Episode WEB-DL "
                + "1080P H264 AAC · 完整节目字幕与片尾花絮特别珍藏版";
        String title = part + part;
        instrumentation.runOnMainSync(() -> {
            grid.setLayoutParams(new LinearLayout.LayoutParams(dp(350), -2));
            grid.setItems(List.of(title), 0);
            grid.setFocusedPosition(0);
        });
        await(() -> grid.getFocusedPosition() == 0, "focused long title");
        Rect bounds = episodeBounds(title);
        AtomicReference<Dialog> overlay = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            Dialog dialog = new Dialog(activity);
            overlay.set(dialog);
            TextView message = new TextView(activity);
            message.setText("测试弹窗");
            dialog.setContentView(message);
            dialog.show();
            dialog.getWindow().setGravity(Gravity.BOTTOM);
            dialog.getWindow().setLayout(dp(200), dp(48));
        });
        try {
            await(() -> !grid.hasWindowFocus(), "other window focus");
            SystemClock.sleep(700);
            assertEquals("No background marquee while a dialog owns focus", 0, changedPixels(bounds, 650));
        } finally {
            instrumentation.runOnMainSync(() -> overlay.get().dismiss());
        }
        await(grid::hasWindowFocus, "window focus restored");
        SystemClock.sleep(1500);
        assertTrue("Marquee resumes after returning to the window", changedPixels(bounds, 650) > 100);
        assertEquals(-1, clicked.get());
    }

    @Test(timeout = 60000)
    public void measuresLargeEpisodeListsOnceAndKeepsRemoteActionsResponsive() {
        for (int count : List.of(100, 300, 1000)) {
            List<String> episodes = new ArrayList<>();
            for (int index = 1; index <= count; index++) episodes.add(count + "集 · 第" + index + "集");
            long started = SystemClock.uptimeMillis();
            instrumentation.runOnMainSync(() -> {
                grid.setItems(episodes, 0);
                grid.setFocusedPosition(0);
            });
            await(() -> grid.getFocusedPosition() == 0, "large list first focus");
            long elapsed = SystemClock.uptimeMillis() - started;
            android.util.Log.i("CinemaEpisodePerf", "items=" + count + " firstFocusMillis=" + elapsed);
            press(KeyEvent.KEYCODE_DPAD_RIGHT);
            await(() -> grid.getFocusedPosition() == 1, "large list remote navigation");
        }
        assertEquals(-1, clicked.get());
    }

    private void press(int key) {
        instrumentation.sendKeyDownUpSync(key);
        instrumentation.waitForIdleSync();
    }

    private Rect episodeBounds(String title) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) instrumentation.getUiAutomation().clearCache();
        UiObject2 text = UiDevice.getInstance(instrumentation).findObject(By.text(title));
        assertTrue("Visible episode " + title, text != null);
        return text.getVisibleBounds();
    }

    private int changedPixels(Rect bounds, long delay) {
        Bitmap first = instrumentation.getUiAutomation().takeScreenshot();
        SystemClock.sleep(delay);
        Bitmap second = instrumentation.getUiAutomation().takeScreenshot();
        try {
            int count = 0;
            for (int vertical = bounds.top; vertical < bounds.bottom; vertical++) {
                for (int horizontal = bounds.left; horizontal < bounds.right; horizontal++) {
                    if (first.getPixel(horizontal, vertical) != second.getPixel(horizontal, vertical)) count++;
                }
            }
            return count;
        } finally {
            first.recycle();
            second.recycle();
        }
    }

    private void await(BooleanSupplier condition, String name) {
        long deadline = SystemClock.uptimeMillis() + 7000;
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition.getAsBoolean()) return;
            SystemClock.sleep(50);
        }
        assertTrue(name, condition.getAsBoolean());
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
