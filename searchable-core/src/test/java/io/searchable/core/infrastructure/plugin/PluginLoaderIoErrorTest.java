package io.searchable.core.infrastructure.plugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class PluginLoaderIoErrorTest {

    @TempDir Path tempDir;

    @Test
    void closeSwallowsIoExceptionFromExternalClassLoader() throws Exception {
        // Replace the lazily-built external classloader with a mock that
        // throws on close() so PluginLoader.close() exercises the catch arm.
        final PluginLoader loader = new PluginLoader(tempDir);
        final var field = PluginLoader.class.getDeclaredField("externalClassLoader");
        field.setAccessible(true);
        final URLClassLoader broken = mock(URLClassLoader.class);
        doThrow(new IOException("ucl-boom")).when(broken).close();
        field.set(loader, broken);

        loader.close(); // must not propagate
    }
}
