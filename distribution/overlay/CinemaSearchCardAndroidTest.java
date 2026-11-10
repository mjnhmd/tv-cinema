package com.fongmi.android.tv.ui;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.os.Parcel;
import android.os.SystemClock;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.test.CorePlaybackActivity;
import com.fongmi.android.tv.test.NativeUiEvidence;
import com.fongmi.android.tv.ui.base.BaseVodHolder;
import com.fongmi.android.tv.ui.presenter.CinemaSearchPresenter;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
import com.google.gson.Gson;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** Real search cards in the existing isolated native validation app. */
@RunWith(AndroidJUnit4.class)
public final class CinemaSearchCardAndroidTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final List<BaseVodHolder> holders = new ArrayList<>();
    private final AtomicInteger clicks = new AtomicInteger();
    private final AtomicInteger longClicks = new AtomicInteger();
    private CorePlaybackActivity activity;
    private LinearLayout row;
    private Button other;
    private String poster;

    @Before public void setup() throws Exception {
        assertEquals("com.fongmi.android.tv.sourceprobe", instrumentation.getTargetContext().getPackageName());
        activity = (CorePlaybackActivity) instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), CorePlaybackActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Bitmap bitmap = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint();
        paint.setShader(new LinearGradient(0, 0, 240, 320,
                Color.rgb(111, 166, 153), Color.rgb(33, 52, 67), Shader.TileMode.CLAMP));
        new Canvas(bitmap).drawRect(0, 0, 240, 320, paint);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        poster = "data:image/png;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
        bitmap.recycle();
        instrumentation.runOnMainSync(() -> {
            LinearLayout host = new LinearLayout(activity);
            host.setOrientation(LinearLayout.VERTICAL);
            host.setPadding(dp(32), dp(24), dp(32), dp(24));
            other = new Button(activity);
            other.setText("搜索结果");
            other.setFocusableInTouchMode(true);
            host.addView(other, new LinearLayout.LayoutParams(dp(180), dp(48)));
            row = new LinearLayout(activity);
            row.setPadding(0, dp(16), 0, dp(16));
            host.addView(row);
            activity.setContentView(host);
            other.requestFocus();
        });
        instrumentation.waitForIdleSync();
    }

    @After public void cleanup() {
        instrumentation.runOnMainSync(() -> { holders.forEach(BaseVodHolder::unbind); activity.finish(); });
    }

    @Test public void compactCardsKeepMetadataVisibleAndTitleTwoLinesAcrossDpadFocus() {
        Vod item = item("哈利·波特与阿兹卡班的囚徒", "雪花资源·高清线路", "更新至第十二集", "8.6");
        BaseVodHolder first = add(item, 110);
        BaseVodHolder second = add(item("很长的电影名称：导演特别剪辑修复版", "备用线路", "1080P 完整版", "9.1"), 110);
        add(item("短片名", "极速资源", "全24集", ""), 110);
        add(item("没有评分也没有更新状态的长片名", "", "", ""), 110);
        add(item("更窄卡片也能独立显示两行信息", "一个特别长的片源名称", "更新至100集·特别版", "9.2"), 94);
        NativeUiEvidence.capture("search-cards-unfocused");
        instrumentation.runOnMainSync(() -> {
            assertVisibleMetadata(first);
            TextView title = first.view.findViewById(R.id.name);
            assertEquals(2, title.getLayout().getLineCount());
            assertEquals(2, title.getMaxLines());
            assertEquals(0, title.getLayout().getEllipsisCount(1));
            assertTrue(title.getTop() >= first.view.findViewById(R.id.poster).getBottom());
            assertTrue(first.view.requestFocus());
        });
        instrumentation.waitForIdleSync();
        int height = first.view.getHeight();
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> {
            assertTrue("D-pad reaches the next card", second.view.hasFocus());
            assertVisibleMetadata(first);
            assertVisibleMetadata(second);
            assertEquals(height, first.view.getHeight());
            assertEquals(2, ((TextView) first.view.findViewById(R.id.name)).getMaxLines());
            assertFalse(first.view.findViewById(R.id.name).isFocusable());
            assertEquals(0, clicks.get());
        });
        NativeUiEvidence.capture("search-cards-focused");
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(1, clicks.get());
        instrumentation.runOnMainSync(() -> assertTrue(second.view.performLongClick()));
        assertEquals(1, longClicks.get());
    }

    @Test public void recycledCardsClearMissingMetadataAndGroupedPayloadUpdatesWithoutFocus() {
        Vod item = item("两行片名测试", "原始线路", "更新至12集", "8.6");
        BaseVodHolder holder = add(item, 110);
        instrumentation.runOnMainSync(() -> {
            item.setSourceCandidates(List.of(new Vod(), new Vod()));
            holder.updateSourceSummary(item);
            assertTrue(((TextView) holder.view.findViewById(R.id.remark)).getText().toString().contains("3"));
            assertTrue(holder.view.findViewById(R.id.metadata).isShown());
            holder.initView(new Vod());
        });
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> {
            for (int id : new int[]{R.id.info, R.id.remark, R.id.site, R.id.metadata}) {
                assertEquals("No stale badge after recycling", View.GONE, holder.view.findViewById(id).getVisibility());
            }
            assertEquals("", ((TextView) holder.view.findViewById(R.id.name)).getText().toString());
            holder.initView(item);
            assertVisibleMetadata(holder);
            assertFalse(holder.view.hasFocus());
        });
    }

    @Test public void scoreAndFollowingParcelFieldsSurviveWithSourceCandidates() {
        Vod item = item("标题", "线路", "更新", "8.6");
        item.setContentRating("PG-13");
        item.setBackdrop("fixture-backdrop");
        item.setPlayId("episode-12");
        item.setSourceCandidates(List.of(item("备选", "备选线路", "", "9.1")));
        Parcel parcel = Parcel.obtain();
        try {
            item.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            Vod restored = Vod.CREATOR.createFromParcel(parcel);
            assertEquals("8.6", restored.getScore());
            assertEquals("PG-13", restored.getContentRating());
            assertEquals("fixture-backdrop", restored.getBackdrop());
            assertEquals("episode-12", restored.getPlayId());
            assertEquals("9.1", restored.getSourceCandidates().get(0).getScore());
            assertEquals(parcel.dataSize(), parcel.dataPosition());
        } finally { parcel.recycle(); }
    }

    private BaseVodHolder add(Vod item, int width) {
        AtomicReference<BaseVodHolder> result = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            CinemaSearchPresenter presenter = new CinemaSearchPresenter(new VodPresenter.OnClickListener() {
                @Override public void onItemClick(Vod vod) { clicks.incrementAndGet(); }
                @Override public boolean onLongClick(Vod vod) { longClicks.incrementAndGet(); return true; }
            }, new int[]{dp(width), dp(Math.round(width / .75f))});
            BaseVodHolder holder = (BaseVodHolder) presenter.onCreateViewHolder(row);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.view.getLayoutParams();
            params.rightMargin = dp(16);
            presenter.onBindViewHolder(holder, item);
            row.addView(holder.view, params);
            holders.add(holder);
            result.set(holder);
        });
        instrumentation.waitForIdleSync();
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<Boolean> loaded = new AtomicReference<>(false);
            instrumentation.runOnMainSync(() -> loaded.set(((ImageView) result.get().view.findViewById(R.id.image))
                    .getDrawable() instanceof BitmapDrawable));
            if (loaded.get()) return result.get();
            SystemClock.sleep(50);
        }
        fail("Fixture artwork must load before checking poster overlays");
        return result.get();
    }

    private Vod item(String name, String source, String remark, String score) {
        Vod item = new Gson().fromJson("{\"vod_score\":\"" + score + "\"}", Vod.class);
        item.setName(name);
        item.setPic(poster);
        item.setRemarks(remark);
        if (!source.isEmpty()) item.setSite(new Gson().fromJson("{\"key\":\"fixture\",\"name\":\"" + source + "\"}", Site.class));
        return item;
    }

    private void assertVisibleMetadata(BaseVodHolder holder) {
        View info = holder.view.findViewById(R.id.info);
        View remark = holder.view.findViewById(R.id.remark);
        View source = holder.view.findViewById(R.id.site);
        assertTrue(info.isShown());
        assertTrue(remark.isShown());
        assertTrue(source.isShown());
        assertTrue("Metadata lines use separate vertical space", info.getBottom() <= remark.getTop());
        assertTrue("Source badge fits inside the poster", source.getRight() <= holder.view.getWidth() - dp(7));
        assertEquals("No side-by-side width competition", info.getWidth(), remark.getWidth());
    }

    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
}
