package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RewardConfigurationTest {
    @Test
    void rewardConfigurationReadsOnlyBundledResources() throws Exception {
        assertTrue(RewardConfiguration.readBundled("loot.yaml").contains("chest_artifact_mix_01:"));
        String css = RewardConfiguration.readBundledCss();
        assertTrue(css.contains("astaps.loot.chest_artifact_mix_01:"));
        assertTrue(css.contains("transpoint[type=\"waypoint\""));
        assertTrue(css.contains("blossom[type=\"wealth\""));
        assertTrue(css.contains("astaps.loot.world_artifact_5star:"));
    }

    @Test
    void cssDiscoveryIncludesNewFilesInFilenameOrder(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
        var directory = java.nio.file.Files.createDirectory(temp.resolve("rewards"));
        var marker = java.nio.file.Files.writeString(directory.resolve("loot.yaml"), "{}");
        java.nio.file.Files.writeString(directory.resolve("z-custom.css"), "/* last */");
        java.nio.file.Files.writeString(directory.resolve("a-custom.css"), "/* first */");
        java.nio.file.Files.writeString(directory.resolve("b-not-css.txt"), "ignored");
        var subdirectory = java.nio.file.Files.createDirectory(directory.resolve("nested"));
        java.nio.file.Files.writeString(subdirectory.resolve("inner.css"), "ignored");
        assertEquals("/* first */\n/* last */",
                RewardConfiguration.readCssDirectory(marker.toUri().toURL()));
    }

    @Test
    void cssDiscoveryWorksInsideJarWithoutDirectoryEntries(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
        var jar = temp.resolve("fixtures.jar");
        try (var output = new java.util.jar.JarOutputStream(
                java.nio.file.Files.newOutputStream(jar))) {
            for (var entry : java.util.Map.of(
                    "rewards/loot.yaml", "{}",
                    "rewards/z-pool.css", "/* last */",
                    "rewards/a-pool.css", "/* first */",
                    "other/extra.css", "ignored").entrySet()) {
                output.putNextEntry(new java.util.jar.JarEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        var marker = new java.net.URL("jar:" + jar.toUri().toURL() + "!/rewards/loot.yaml");
        assertEquals("/* first */\n/* last */",
                RewardConfiguration.readCssDirectory(marker));
    }

    @Test
    void emptyCssDirectoryIsRejected(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
        var directory = java.nio.file.Files.createDirectory(temp.resolve("rewards"));
        var marker = java.nio.file.Files.writeString(directory.resolve("loot.yaml"), "{}");
        assertThrows(java.io.IOException.class,
                () -> RewardConfiguration.readCssDirectory(marker.toUri().toURL()));
    }

    @Test
    void missingBundledResourcesAreReported() {
        assertThrows(java.io.IOException.class, () ->
                RewardConfiguration.readBundled("missing.yaml"));
    }
}
