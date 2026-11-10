package com.fongmi.android.tv.player.exo;

import android.app.Instrumentation;
import android.os.SystemClock;

import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.text.CueGroup;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.media.PlaySpec;
import com.github.catvod.utils.Prefers;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.junit.Assert.*;

/** Real decoded audio and external subtitle cues, with a stale enabled speech preference. */
@RunWith(AndroidJUnit4.class)
public final class CinemaSubtitleRemovalAndroidTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    @Test public void ordinarySubtitlesAndAudioSurviveRetiredSpeechSettings() throws Exception {
        play(false);
    }

    @Test public void audioEffectsAndOrdinarySubtitlesStillWorkTogether() throws Exception {
        play(true);
    }

    private void play(boolean effects) throws Exception {
        assertEquals("Disposable speech-removal validation app only", "com.fongmi.android.tv.speechprobe",
                instrumentation.getTargetContext().getPackageName());
        Map<String, Object> settings = Map.of("audio_effect_preset", effects ? 1 : 0,
                "audio_pass_through", false, "tunnel", false, "preload_next", false,
                "subtitle_libass", false, "ai_subtitle_enabled", true);
        Map<String, ?> saved = new HashMap<>(Prefers.getPrefers().getAll());
        File audio = File.createTempFile("cinema-audio-", ".wav", instrumentation.getTargetContext().getCacheDir());
        File subtitle = File.createTempFile("cinema-subtitle-", ".srt", instrumentation.getTargetContext().getCacheDir());
        writeWav(audio);
        try (FileOutputStream out = new FileOutputStream(subtitle)) {
            out.write("1\n00:00:00,000 --> 00:00:09,000\nOrdinary subtitle retained\n".getBytes(StandardCharsets.UTF_8));
        }
        AtomicReference<PlaybackException> failure = new AtomicReference<>();
        List<String> cues = new CopyOnWriteArrayList<>();
        ExoPlayerEngine[] engine = new ExoPlayerEngine[1];
        try {
            main(() -> {
                settings.forEach(Prefers::put);
                engine[0] = new ExoPlayerEngine(PlayerEngine.HARD, new Player.Listener() {
                    @Override public void onPlayerError(PlaybackException error) { failure.set(error); }
                    @Override public void onCues(CueGroup group) {
                        group.cues.forEach(cue -> { if (cue.text != null) cues.add(cue.text.toString()); });
                    }
                });
                Result result = new Result();
                result.setUrl(audio.toURI().toString());
                result.setFormat("audio/wav");
                result.setSubs(List.of(Sub.from(subtitle.toURI().toString())));
                engine[0].start(PlaySpec.from(result, "cinema-subtitle-removal", MediaMetadata.EMPTY), 0);
                engine[0].getPlayer().setVolume(0);
            });
            await(() -> failure.get() != null || engine[0].getPlayer().getCurrentPosition() > 500,
                    "Actual local audio must advance");
            assertNull(failure.get());
            await(() -> cues.contains("Ordinary subtitle retained"), "External SRT must produce real subtitle cues");
            ExoPlayer player = (ExoPlayer) engine[0].getPlayer();
            SourceAudioEffectProbe.Snapshot snapshot = read(() -> SourceAudioEffectProbe.snapshot(player))
                    .get(5, TimeUnit.SECONDS);
            assertEquals(effects, snapshot.installed());
            if (effects) assertTrue("Production DSP must process real audio", snapshot.processed());
            main(() -> { player.pause(); player.seekTo(2_000); });
            await(() -> player.getPlaybackState() == Player.STATE_READY, "Seek should return to READY");
            main(() -> { assertFalse(player.getPlayWhenReady()); player.play(); });
            await(() -> player.getCurrentPosition() > 2_250, "Audio must resume after seek");
            assertNull(failure.get());
        } finally {
            main(() -> {
                if (engine[0] != null) engine[0].release();
                for (String key : settings.keySet()) {
                    if (saved.containsKey(key)) Prefers.put(key, saved.get(key));
                    else Prefers.getPrefers().edit().remove(key).apply();
                }
            });
            audio.delete();
            subtitle.delete();
        }
    }

    private static void writeWav(File file) throws Exception {
        int samples = 160_000;
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + samples * 2);
        header.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1);
        header.putShort((short) 1).putInt(16_000).putInt(32_000).putShort((short) 2).putShort((short) 16);
        header.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(samples * 2);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(header.array());
            ByteBuffer pcm = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < samples; i++) pcm.putShort((short) (Math.sin(i * 2 * Math.PI * 440 / 16_000) * 2_000));
            out.write(pcm.array());
        }
    }

    private void await(BooleanSupplier condition, String message) {
        long deadline = SystemClock.elapsedRealtime() + 12_000;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (read(condition::getAsBoolean)) return;
            SystemClock.sleep(50);
        }
        throw new AssertionError(message);
    }

    private void main(Runnable task) { read(() -> { task.run(); return null; }); }

    private <T> T read(Supplier<T> task) {
        AtomicReference<T> result = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> result.set(task.get()));
        return result.get();
    }
}
