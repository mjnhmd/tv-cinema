package com.fongmi.android.tv.update;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class UpdateApkValidatorTest {

    private static final String TV_ABI = "armeabi-v7a";
    private static final String PHONE_ABI = "arm64-v8a";

    @Test
    @SuppressWarnings("deprecation")
    public void signatureFlagsIncludeLegacyFlagOnApi28AndAbove() {
        assertEquals(PackageManager.GET_SIGNATURES, UpdateApkValidator.signatureFlags(24));
        assertEquals(PackageManager.GET_SIGNATURES, UpdateApkValidator.signatureFlags(27));
        int api28 = UpdateApkValidator.signatureFlags(28);
        assertEquals(PackageManager.GET_SIGNING_CERTIFICATES | PackageManager.GET_SIGNATURES, api28);
        assertEquals(api28, UpdateApkValidator.signatureFlags(36));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void signersFallsBackToLegacySignaturesWhenSigningInfoMissing() {
        assertTrue(UpdateApkValidator.signers(null, 28).isEmpty());
        PackageInfo empty = new PackageInfo();
        assertTrue(UpdateApkValidator.signers(empty, 28).isEmpty());
        PackageInfo legacyOnly = new PackageInfo();
        Signature cert = new Signature(new byte[]{1, 2, 3, 4});
        legacyOnly.signatures = new Signature[]{cert};
        Set<Signature> result = UpdateApkValidator.signers(legacyOnly, 28);
        assertEquals(1, result.size());
        assertSame(cert, result.iterator().next());
    }

    @Test
    public void acceptsLeanbackMarkerInEitherStringPoolEncoding() {
        assertTrue(UpdateApkValidator.declaresLeanbackLauncher(manifest(StandardCharsets.UTF_16LE, "leanback")));
        assertTrue(UpdateApkValidator.declaresLeanbackLauncher(manifest(StandardCharsets.UTF_8, "leanback")));
    }

    @Test
    public void rejectsArchiveWithoutLeanbackMarker() {
        assertFalse(UpdateApkValidator.declaresLeanbackLauncher(manifest(StandardCharsets.UTF_16LE, "phone")));
        assertFalse(UpdateApkValidator.declaresLeanbackLauncher(new byte[0]));
    }

    @Test
    public void readsNativeAbiAndManifestFromArchive() throws Exception {
        byte[] expected = manifest(StandardCharsets.UTF_16LE, "leanback");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("AndroidManifest.xml", expected);
        entries.put("classes.dex", new byte[]{0x64, 0x65, 0x78});
        entries.put("lib/" + TV_ABI + "/libffmpeg.so", new byte[]{0x7f, 0x45, 0x4c, 0x46});
        try (ZipFile archive = new ZipFile(archive(entries).toFile())) {
            UpdateApkValidator.Layout layout = UpdateApkValidator.readLayout(archive);
            assertTrue(layout.hasNativeAbi(TV_ABI));
            assertFalse(layout.hasNativeAbi(PHONE_ABI));
            assertFalse(layout.hasForeignAbi(TV_ABI));
            assertTrue(layout.hasDex());
            assertArrayEquals(expected, layout.manifest());
        }
    }

    @Test
    public void rejectsArchiveWhoseOnlyNativeCodeIsForeign() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("AndroidManifest.xml", manifest(StandardCharsets.UTF_16LE, "leanback"));
        entries.put("classes.dex", new byte[]{1});
        entries.put("lib/" + PHONE_ABI + "/libffmpeg.so", new byte[]{1});
        try (ZipFile archive = new ZipFile(archive(entries).toFile())) {
            UpdateApkValidator.Layout layout = UpdateApkValidator.readLayout(archive);
            assertFalse(layout.hasNativeAbi(TV_ABI));
            assertTrue(layout.hasForeignAbi(TV_ABI));
        }
    }

    @Test
    public void rejectsArchiveBundlingBothAbis() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("AndroidManifest.xml", manifest(StandardCharsets.UTF_16LE, "leanback"));
        entries.put("classes.dex", new byte[]{1});
        entries.put("lib/" + TV_ABI + "/libffmpeg.so", new byte[]{1});
        entries.put("lib/" + PHONE_ABI + "/libffmpeg.so", new byte[]{1});
        try (ZipFile archive = new ZipFile(archive(entries).toFile())) {
            UpdateApkValidator.Layout layout = UpdateApkValidator.readLayout(archive);
            assertTrue(layout.hasNativeAbi(TV_ABI));
            assertTrue(layout.hasForeignAbi(TV_ABI));
        }
    }

    @Test
    public void flagsForeignLibraryPathWithoutNativeCode() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("AndroidManifest.xml", manifest(StandardCharsets.UTF_16LE, "leanback"));
        entries.put("classes.dex", new byte[]{1});
        entries.put("lib/" + TV_ABI + "/libffmpeg.so", new byte[]{1});
        entries.put("lib/" + PHONE_ABI + "/notes.txt", new byte[]{1});
        try (ZipFile archive = new ZipFile(archive(entries).toFile())) {
            UpdateApkValidator.Layout layout = UpdateApkValidator.readLayout(archive);
            assertTrue(layout.hasForeignAbi(TV_ABI));
        }
    }

    @Test
    public void ignoresBareLibraryDirectoryEntry() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("AndroidManifest.xml", manifest(StandardCharsets.UTF_16LE, "leanback"));
        entries.put("classes.dex", new byte[]{1});
        entries.put("lib/", new byte[0]);
        try (ZipFile archive = new ZipFile(archive(entries).toFile())) {
            UpdateApkValidator.Layout layout = UpdateApkValidator.readLayout(archive);
            assertFalse(layout.hasNativeAbi(TV_ABI));
            assertFalse(layout.hasForeignAbi(TV_ABI));
        }
    }

    @Test
    public void reportsMissingDex() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("AndroidManifest.xml", manifest(StandardCharsets.UTF_16LE, "leanback"));
        entries.put("lib/" + TV_ABI + "/libffmpeg.so", new byte[]{1});
        try (ZipFile archive = new ZipFile(archive(entries).toFile())) {
            UpdateApkValidator.Layout layout = UpdateApkValidator.readLayout(archive);
            assertFalse(layout.hasDex());
            assertTrue(layout.hasNativeAbi(TV_ABI));
        }
    }

    private static byte[] manifest(java.nio.charset.Charset charset, String edition) {
        String category = "leanback".equals(edition)
                ? UpdateApkValidator.LEANBACK_LAUNCHER : "android.intent.category.LAUNCHER";
        return ("\u0003\u0000\u0008\u0000<manifest><activity><intent-filter>"
                + "<category android:name=\"" + category + "\"/></intent-filter></activity></manifest>")
                .getBytes(charset);
    }

    private static Path archive(Map<String, byte[]> entries) throws IOException {
        Path directory = Files.createTempDirectory("update-apk-validator");
        directory.toFile().deleteOnExit();
        Path file = directory.resolve("update.apk");
        try (OutputStream output = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        file.toFile().deleteOnExit();
        return file;
    }
}
