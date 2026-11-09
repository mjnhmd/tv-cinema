package com.fongmi.android.tv.update;

import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class CinemaUpdateTest {

    private static final String RELEASE = "https://github.com/mjnhmd/tv-cinema/releases/download/v0.2.0/";
    private static final String SHA = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

    @Test
    public void cinemaUsesOwnManifestAndOrderedMirrors() {
        String url = "https://github.com/mjnhmd/tv-cinema/releases/latest/download/leanback-coexist.json";
        assertEquals(List.of("https://gh-proxy.com/" + url, "https://ghfast.top/" + url, url),
                UpdateSources.manifests("leanback", UpdateSources.CINEMA_PACKAGE));
    }

    @Test
    public void cinemaManifestRequiresOwnImmutableAsset() throws Exception {
        String file = "leanback-armeabi_v7a-coexist.apk";
        JSONObject asset = new JSONObject().put("url", RELEASE + file).put("size", 100000).put("sha256", SHA);
        JSONObject manifest = new JSONObject().put("schema", 1).put("code", 10002).put("name", "0.2.0")
                .put("packageName", UpdateSources.CINEMA_PACKAGE).put("mode", "leanback")
                .put("assets", new JSONObject().put("armeabi_v7a", asset));
        assertEquals(RELEASE + file, UpdateManifest.parse(manifest.toString(), UpdateSources.CINEMA_PACKAGE,
                "leanback", "armeabi_v7a").asset.url);
        for (String wrong : new String[]{
                "https://github.com/wobuhui666/TV/releases/download/v0.2.0/" + file,
                "https://github.com/mjnhmd/tv-cinema/releases/latest/download/" + file,
                RELEASE + file + "?download=1", RELEASE + "leanback-arm64_v8a-coexist.apk"}) {
            asset.put("url", wrong);
            assertThrows(IOException.class, () -> UpdateManifest.parse(manifest.toString(),
                    UpdateSources.CINEMA_PACKAGE, "leanback", "armeabi_v7a"));
        }
    }
}
