package io.github.rygel.sparkle4j.apply;

import static org.junit.jupiter.api.Assertions.*;

import io.github.rygel.sparkle4j.Sparkle4jConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;

class UpdateApplierTest {

    private static Object invokeWalkUp(Path start) {
        try {
            Method method = UpdateApplier.class.getDeclaredMethod("walkUpToAppBundle", Path.class);
            method.setAccessible(true);
            return method.invoke(null, start);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("walkUpToAppBundle finds .app in path")
    void findsAppBundle() {
        var result = invokeWalkUp(Path.of("/Applications/MyApp.app/Contents/MacOS/java"));
        assertEquals(Path.of("/Applications/MyApp.app"), result);
    }

    @Test
    @DisplayName("walkUpToAppBundle returns null when no .app found")
    void returnsNullWhenNoApp() {
        var result = invokeWalkUp(Path.of("/usr/bin/java"));
        assertNull(result);
    }

    @Test
    @DisplayName("walkUpToAppBundle finds .app at top level")
    void findsAppAtTopLevel() {
        var result = invokeWalkUp(Path.of("/MyApp.app"));
        assertEquals(Path.of("/MyApp.app"), result);
    }

    @Test
    @DisplayName("walkUpToAppBundle returns deepest .app")
    void findsDeepestApp() {
        var result =
                invokeWalkUp(Path.of("/Applications/MyApp.app/Contents/Frameworks/Helper.app/bin"));
        assertEquals(Path.of("/Applications/MyApp.app/Contents/Frameworks/Helper.app"), result);
    }

    @Test
    @DisplayName("resolveCurrentAppPath returns configured macosAppPath when set")
    void resolveUsesConfiguredPath() throws Exception {
        var configPath = Path.of("/Applications/TestApp.app");
        var config =
                new Sparkle4jConfig(
                        "https://example.com/appcast.xml",
                        "1.0.0",
                        null,
                        24,
                        null,
                        "TestApp",
                        null,
                        configPath);
        var applier = new UpdateApplier(config);

        Method method = UpdateApplier.class.getDeclaredMethod("resolveCurrentAppPath");
        method.setAccessible(true);
        var result = (Path) method.invoke(applier);

        assertEquals(configPath, result);
    }

    @Test
    @DisplayName("resolveCurrentAppPath returns fallback when macosAppPath is null")
    void resolveFallbackWhenNull() throws Exception {
        var config =
                new Sparkle4jConfig(
                        "https://example.com/appcast.xml",
                        "1.0.0",
                        null,
                        24,
                        null,
                        "TestApp",
                        null,
                        null);
        var applier = new UpdateApplier(config);

        Method method = UpdateApplier.class.getDeclaredMethod("resolveCurrentAppPath");
        method.setAccessible(true);
        var result = (Path) method.invoke(applier);

        assertNotNull(result);
    }
}
