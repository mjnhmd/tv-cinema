package com.fongmi.android.tv.ui.custom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.test.NativeUiEvidence;
import com.fongmi.android.tv.ui.activity.VideoActivity;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class CinemaEpisodePanelAndroidTest {

    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private VideoActivity activity;

    @After public void cleanup() {
        if (activity != null) instrumentation.runOnMainSync(activity::finish);
    }

    @Test(timeout = 90_000)
    public void sourceRangeAndEpisodePanelStayInViewWithPredictableFocus() throws Exception {
        assertEquals("com.fongmi.android.tv.sourceprobe", instrumentation.getTargetContext().getPackageName());
        File fixture = new File(instrumentation.getTargetContext().getExternalFilesDir(null), "cinema-window-render-fixture.mp4");
        assertTrue("Install the local playback fixture", fixture.isFile());
        activity = (VideoActivity) instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), VideoActivity.class)
                .putExtra("key", "push_agent").putExtra("id", Uri.fromFile(fixture).toString())
                .putExtra("name", "选集布局回归").putExtra("pic", "")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(() -> binding() != null, "activity binding");

        List<Flag> flags = new ArrayList<>();
        Flag main = new Flag("main");
        main.setFlag("夸父智1");
        main.getEpisodes().addAll(episodes(61));
        Flag secondary = new Flag("backup");
        secondary.setFlag("嘟嘟无限2");
        secondary.getEpisodes().addAll(episodes(61));
        secondary.setSelected(main);
        flags.add(main);
        flags.add(secondary);
        main.setSelected(main);

        main(() -> setField("mHistory", new History()));
        invoke("showTitleText", String.class, "现在就出发 第四季");
        invoke("renderFlags", List.class, flags);
        invoke("renderEpisodes", List.class, main.getEpisodes());
        invoke("renderDescription", String.class, "本地测试简介：用于验证少集数时右栏不会留出没有信息的大片空白。");
        main(() -> progress().showContent());
        await(() -> episodes().getHeight() > 0, "episode panel laid out");

        View flag = view(R.id.flag);
        View array = view(R.id.array);
        View grid = view(R.id.episode);
        int flagTop = top(flag);
        int arrayTop = top(array);
        int gridTop = top(grid);
        assertTrue("Playback source must appear above the ranges", flagTop < arrayTop);
        assertTrue("Ranges must stay above the scrolling episode grid", arrayTop < gridTop);
        assertTrue("Ranges must remain on the first screen", arrayTop < dpHeight() / 2);
        assertTrue("The episode grid must own the remaining right rail", grid.getHeight() >= dp(180));
        assertEquals("Preview stays 16:9", 16f / 9f, view(R.id.video).getWidth() / (float) view(R.id.video).getHeight(), 0.02f);

        main(() -> grid.requestFocus());
        await(grid::hasFocus, "episode grid focus");
        press(KeyEvent.KEYCODE_DPAD_LEFT);
        await(() -> !grid.hasFocus(), "left leaves the grid for the tool rail");
        assertTrue("Left from a row start must hand the remote back to the range rail",
                read(() -> inside(R.id.array)));
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        await(() -> read(() -> activity.findViewById(R.id.episode) != null && inside(R.id.episode)),
                "down from the range rail returns to the episode grid");
        press(KeyEvent.KEYCODE_DPAD_LEFT);
        await(() -> read(() -> inside(R.id.array)), "back to the range rail");
        press(KeyEvent.KEYCODE_DPAD_LEFT);
        await(() -> read(() -> inside(R.id.detail) || inside(R.id.video)),
                "left at the first range chip must leave the horizontal rail");

        main(() -> episodes().setFocusedPosition(40));
        await(() -> episodes().getFocusedPosition() == 40, "focus long episode through range jump");
        TextView name = (TextView) view(R.id.episodeFocusName);
        assertTrue("The focused item's full original name is shown", name.getText().toString().contains("2026"));
        NativeUiEvidence.capture("cinema-episodes-long");

        List<Episode> few = episodes(3);
        invoke("renderEpisodes", List.class, few);
        await(() -> view(R.id.episodeSummary).getVisibility() == View.VISIBLE, "short-list summary");
        NativeUiEvidence.capture("cinema-episodes-short");
    }

    private List<Episode> episodes(int count) {
        List<Episode> items = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            String day = String.format("%02d", index);
            items.add(Episode.create("[2." + day + "GB] Xz出发 2026.10." + day
                    + "-第" + index + "期上-4K.mp4", "fixture-" + index));
        }
        return items;
    }

    private void press(int key) {
        instrumentation.sendKeyDownUpSync(key);
        SystemClock.sleep(120);
    }

    private int focusedId() {
        return read(() -> {
            View focus = activity.getCurrentFocus();
            return focus == null ? View.NO_ID : focus.getId();
        });
    }

    private boolean inside(int id) {
        View target = activity.findViewById(id);
        View focus = activity.getCurrentFocus();
        while (focus != null) {
            if (focus == target) return true;
            android.view.ViewParent parent = focus.getParent();
            focus = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private int top(View view) {
        int[] location = new int[2];
        main(() -> view.getLocationOnScreen(location));
        return location[1];
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private int dpHeight() {
        return activity.getResources().getConfiguration().screenHeightDp * activity.getResources().getDisplayMetrics().densityDpi / 160;
    }

    private View view(int id) {
        return read(() -> activity.findViewById(id));
    }

    private com.fongmi.android.tv.databinding.ActivityVideoBinding binding() throws Exception {
        return (com.fongmi.android.tv.databinding.ActivityVideoBinding) read(() -> {
            try {
                Field field = VideoActivity.class.getDeclaredField("mBinding");
                field.setAccessible(true);
                return field.get(activity);
            } catch (ReflectiveOperationException error) {
                throw new AssertionError(error);
            }
        });
    }

    private com.fongmi.android.tv.ui.custom.JetStreamEpisodeGrid episodes() {
        return (com.fongmi.android.tv.ui.custom.JetStreamEpisodeGrid) view(R.id.episode);
    }

    private com.fongmi.android.tv.ui.custom.ProgressLayout progress() {
        return (com.fongmi.android.tv.ui.custom.ProgressLayout) view(R.id.progressLayout);
    }

    private void invoke(String name, Class<?> type, Object value) throws Exception {
        main(() -> {
            try {
                Method method = VideoActivity.class.getDeclaredMethod(name, type);
                method.setAccessible(true);
                method.invoke(activity, value);
            } catch (ReflectiveOperationException error) {
                throw new AssertionError(error);
            }
        });
    }

    private void setField(String name, Object value) {
        try {
            Field field = VideoActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(activity, value);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private void main(Runnable action) {
        instrumentation.runOnMainSync(action);
    }

    private <T> T read(java.util.concurrent.Callable<T> callable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            try {
                return callable.call();
            } catch (Exception error) {
                throw new AssertionError(error);
            }
        }
        final Object[] result = new Object[1];
        instrumentation.runOnMainSync(() -> {
            try {
                result[0] = callable.call();
            } catch (Exception error) {
                throw new AssertionError(error);
            }
        });
        @SuppressWarnings("unchecked") T value = (T) result[0];
        return value;
    }

    private void await(BooleanSupplier condition, String message) {
        long deadline = SystemClock.elapsedRealtime() + 10_000;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (read(() -> condition.getAsBoolean())) return;
            SystemClock.sleep(100);
        }
        throw new AssertionError(message);
    }

    private interface BooleanSupplier {
        boolean getAsBoolean() throws Exception;
    }
}
