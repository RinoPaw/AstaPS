package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RewardConfigurationTest {
    @Test
    void rewardConfigurationReadsOnlyBundledResources() throws Exception {
        assertTrue(RewardConfiguration.readBundled("loot.yaml").contains("chest_artifact_mix_01:"));
        String css = RewardConfiguration.readBundledLootweave();
        assertTrue(css.contains("astaps.loot.chest_artifact_mix_01:"));
        assertTrue(css.contains("transpoint[type=\"waypoint\""));
        assertTrue(css.contains("blossom[type=\"wealth\""));
        assertTrue(css.contains("astaps.loot.world_artifact_5star:"));
    }

    @Test
    void lootweaveDiscoveryIncludesNewFilesInFilenameOrder(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
        var directory = java.nio.file.Files.createDirectory(temp.resolve("rewards"));
        var marker = java.nio.file.Files.writeString(directory.resolve("loot.yaml"), "{}");
        java.nio.file.Files.writeString(directory.resolve("z-custom.lw"), "/* last */");
        java.nio.file.Files.writeString(directory.resolve("a-custom.lw"), "/* first */");
        java.nio.file.Files.writeString(directory.resolve("b-not-css.txt"), "ignored");
        var subdirectory = java.nio.file.Files.createDirectory(directory.resolve("nested"));
        java.nio.file.Files.writeString(subdirectory.resolve("inner.lw"), "ignored");
        assertEquals("/* first */\n/* last */",
                RewardConfiguration.readLootweaveDirectory(marker.toUri().toURL()));
    }

    @Test
    void lootweaveDiscoveryWorksInsideJarWithoutDirectoryEntries(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
        var jar = temp.resolve("fixtures.jar");
        try (var output = new java.util.jar.JarOutputStream(
                java.nio.file.Files.newOutputStream(jar))) {
            for (var entry : java.util.Map.of(
                    "rewards/loot.yaml", "{}",
                    "rewards/z-pool.lw", "/* last */",
                    "rewards/a-pool.lw", "/* first */",
                    "other/extra.lw", "ignored").entrySet()) {
                output.putNextEntry(new java.util.jar.JarEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        var marker = new java.net.URL("jar:" + jar.toUri().toURL() + "!/rewards/loot.yaml");
        assertEquals("/* first */\n/* last */",
                RewardConfiguration.readLootweaveDirectory(marker));
    }

    @Test
    void emptyLootweaveDirectoryIsRejected(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
        var directory = java.nio.file.Files.createDirectory(temp.resolve("rewards"));
        var marker = java.nio.file.Files.writeString(directory.resolve("loot.yaml"), "{}");
        assertThrows(java.io.IOException.class,
                () -> RewardConfiguration.readLootweaveDirectory(marker.toUri().toURL()));
    }

    @Test
    void missingBundledResourcesAreReported() {
        assertThrows(java.io.IOException.class, () ->
                RewardConfiguration.readBundled("missing.yaml"));
    }
}
