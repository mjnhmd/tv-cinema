package com.fongmi.android.tv.update;

import androidx.media3.decoder.ffmpeg.FfmpegLibrary;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.fongmi.chaquo.Loader;
import com.github.catvod.utils.Js;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

/** Loads actual stripped JNI and trimmed Python archives without external source traffic. */
@RunWith(AndroidJUnit4.class)
public final class CinemaSlimRuntimeAndroidTest {
    @Test public void trimmedPythonKeepsNativeDependenciesAndExecutesWasm() throws Exception {
        assertEquals("com.fongmi.android.tv.speechprobe", InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName());
        new Loader();
        Class<?> python = Class.forName("com.chaquo.python.Python");
        Object instance = python.getMethod("getInstance").invoke(null);
        Object builtins = python.getMethod("getModule", String.class).invoke(instance, "builtins");
        Method call = Class.forName("com.chaquo.python.PyObject").getMethod("callAttr", String.class, Object[].class);
        Object globals = call.invoke(builtins, "dict", new Object[0]);
        call.invoke(builtins, "exec", new Object[]{"""
                import sys, ssl, sqlite3, ujson, requests, numpy, pywasm, io, tempfile, os
                from lxml import etree
                from Crypto.Cipher import AES
                from importlib.metadata import version
                assert sys.version_info[:2] == (3,10)
                assert numpy.__version__ == '1.26.2' and version('pywasm') == '1.0.8'
                assert numpy.dot(numpy.array([1,2]), numpy.array([3,4])) == 11
                assert numpy.linalg.det(numpy.eye(2)) == 1
                assert ssl.create_default_context().get_ciphers()
                assert sqlite3.connect(':memory:').execute('select 42').fetchone()[0] == 42
                assert ujson.loads(ujson.dumps({'text':'字幕'}))['text'] == '字幕'
                assert etree.fromstring(b'<root>42</root>').text == '42'
                assert AES.new(bytes(16), AES.MODE_ECB).decrypt(AES.new(bytes(16), AES.MODE_ECB).encrypt(bytes(16))) == bytes(16)
                payload = bytes.fromhex('0061736d010000000105016000017f030201000707010372756e00000a06010400412a0b')
                module = pywasm.binary.Module.from_reader(io.BytesIO(payload))
                assert pywasm.Runtime(module).exec('run', []) == 42
                """, globals});
    }

    @Test public void strippedNativeLibrariesKeepFfmpegAndQuickJsBudgetInterfaces() throws Exception {
        assertTrue("FFmpeg JNI loads after stripping", FfmpegLibrary.isAvailable());
        assertNotNull(FfmpegLibrary.getVersion());
        System.loadLibrary("mpv");
        System.loadLibrary("player");
        try (Js js = new Js()) {
            assertEquals("42", js.eval("6 * 7"));
            assertThrows(java.util.concurrent.TimeoutException.class, () -> js.eval("for (;;) {}", "{\"timeout\":100}"));
            assertEquals("2", js.eval("1 + 1"));
        }
    }
}
