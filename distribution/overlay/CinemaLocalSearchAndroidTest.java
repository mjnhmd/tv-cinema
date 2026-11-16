package com.fongmi.android.tv.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.ui.activity.SearchActivity;
import com.fongmi.android.tv.ui.adapter.WordAdapter;
import com.github.catvod.net.OkHttp;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.OkHttpClient;

@RunWith(AndroidJUnit4.class)
public final class CinemaLocalSearchAndroidTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    @Test public void systemChineseIndexSupportsInitialsAndKeepsLiteralMatchesFirst() {
        assertEquals("qyn2", CinemaLocalSearch.initials("庆余年 2"));
        assertEquals("zxc", CinemaLocalSearch.initials("周星驰"));
        CinemaLocalSearch index = new CinemaLocalSearch(List.of("庆余年", "QYN Documentary", "庆余年", "周星驰"));
        assertEquals(List.of("QYN Documentary", "庆余年"), index.find("qyn"));
        assertEquals(List.of("周星驰"), index.find("ZXC"));
        assertTrue(index.find("").isEmpty());
        assertTrue(index.find("missing").isEmpty());
    }

    @Test public void savedTitlesAppearInRealSearchEvenWhenSuggestionNetworkFails() throws Exception {
        assertEquals("com.fongmi.android.tv.sourceprobe", instrumentation.getTargetContext().getPackageName());
        AtomicReference<Keep> saved = new AtomicReference<>();
        AtomicReference<OkHttpClient> original = new AtomicReference<>();
        main(() -> {
            VodConfig.get().init();
            Keep keep = new Keep();
            keep.setKey("cinema-local-search-" + UUID.randomUUID());
            keep.setCid(VodConfig.getCid());
            keep.setType(Keep.TYPE_VOD);
            keep.setVodName("庆余年本地测试");
            keep.setCreateTime(System.currentTimeMillis());
            AppDatabase.get().getKeepDao().insertOrUpdate(keep);
            saved.set(keep);
            original.set(OkHttp.client());
            setField(OkHttp.get(), "client", original.get().newBuilder().addInterceptor(chain -> {
                throw new java.io.IOException("Controlled offline suggestion fixture");
            }).build());
        });
        SearchActivity activity = null;
        try {
            main(() -> instrumentation.getTargetContext().startActivity(new Intent(
                    instrumentation.getTargetContext(), SearchActivity.class).putExtra("keyword", "qyn")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            AtomicReference<SearchActivity> resumed = new AtomicReference<>();
            long launchDeadline = SystemClock.elapsedRealtime() + 5_000;
            while (resumed.get() == null && SystemClock.elapsedRealtime() < launchDeadline) {
                main(() -> ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .forEach(candidate -> { if (candidate instanceof SearchActivity search) resumed.set(search); }));
                if (resumed.get() == null) SystemClock.sleep(50);
            }
            assertTrue("Search activity must reach resumed state", resumed.get() != null);
            activity = resumed.get();
            SearchActivity target = activity;
            long deadline = SystemClock.elapsedRealtime() + 5_000;
            AtomicReference<Boolean> found = new AtomicReference<>(false);
            while (SystemClock.elapsedRealtime() < deadline) {
                main(() -> {
                    androidx.recyclerview.widget.RecyclerView list = target.findViewById(R.id.wordRecycler);
                    WordAdapter adapter = (WordAdapter) list.getAdapter();
                    found.set(adapter.getItems().stream().anyMatch(item -> item.getTitle().equals("庆余年本地测试")));
                });
                if (found.get()) break;
                SystemClock.sleep(50);
            }
            assertTrue("Saved-film initials must work without a suggestion service", found.get());
            assertFalse(target.isFinishing());
            main(() -> {
                androidx.recyclerview.widget.RecyclerView list = target.findViewById(R.id.wordRecycler);
                androidx.recyclerview.widget.RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(0);
                assertTrue("The local suggestion must have a focus target", holder != null && holder.itemView.requestFocus());
                try {
                    java.lang.reflect.Method update = SearchActivity.class.getDeclaredMethod("setWords", List.class);
                    update.setAccessible(true);
                    update.invoke(target, List.of(com.fongmi.android.tv.bean.Word.Data.create("新建议"),
                            com.fongmi.android.tv.bean.Word.Data.create("庆余年本地测试")));
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
            AtomicReference<Boolean> retained = new AtomicReference<>(false);
            long focusDeadline = SystemClock.elapsedRealtime() + 3_000;
            while (!retained.get() && SystemClock.elapsedRealtime() < focusDeadline) {
                main(() -> {
                    androidx.recyclerview.widget.RecyclerView list = target.findViewById(R.id.wordRecycler);
                    android.view.View focused = target.getCurrentFocus();
                    android.view.View row = focused == null ? null : list.findContainingItemView(focused);
                    retained.set(row != null && list.getChildAdapterPosition(row) == 1);
                });
                if (!retained.get()) SystemClock.sleep(50);
            }
            assertTrue("New suggestions must preserve focus on the same title", retained.get());
        } finally {
            SearchActivity target = activity;
            main(() -> {
                if (target != null) target.finish();
                if (saved.get() != null) AppDatabase.get().getKeepDao().delete(saved.get().getCid(), saved.get().getKey());
                if (original.get() != null) setField(OkHttp.get(), "client", original.get());
            });
        }
    }

    private void main(Runnable action) { instrumentation.runOnMainSync(action); }

    private static void setField(Object object, String name, Object value) {
        try {
            Field field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(object, value);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
}
