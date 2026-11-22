package emu.grasscutter.game.player;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.avatar.AvatarCostumeData;
import emu.grasscutter.data.excels.avatar.AvatarReplaceCostumeData;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

/**
 * The four alternate outfits in {@code AvatarReplaceCostumeExcelConfigData} are flagged
 * {@code isDefaultUnlock} in the shipped 7.2 table and are referenced by no shop, reward or giving
 * row, so {@code addReplaceCostumes()} is the only path that can ever hand them to a player. These
 * tests pin the grant to that table instead of to a hardcoded list.
 */
@ResourceLock("GameData")
final class ReplaceCostumeGrantTest {
    private static final List<Integer> SHIPPED = List.of(200302, 202101, 204101, 204501);

    private static AvatarReplaceCostumeData replace(int avatarId, int costumeId) {
        return new Gson().fromJson(
                "{\"avatarId\":%d,\"replace_costume_id\":%d}".formatted(avatarId, costumeId),
                AvatarReplaceCostumeData.class);
    }

    private static AvatarCostumeData costume(int skinId) {
        return new Gson().fromJson(
                "{\"skinId\":%d,\"itemId\":%d,\"characterId\":10000003}"
                        .formatted(skinId, 340006),
                AvatarCostumeData.class);
    }

    /** Runs {@code body} with both costume tables replaced by the given contents. */
    private static void withTables(Set<Integer> replaceRows, Set<Integer> costumeRows, Runnable body) {
        var replaceMap = GameData.getAvatarReplaceCostumeDataMap();
        var costumeMap = GameData.getAvatarCostumeDataMap();
        var oldReplace = new java.util.HashMap<>(replaceMap);
        var oldCostume = new java.util.HashMap<>(costumeMap);
        replaceMap.clear();
        costumeMap.clear();
        try {
            for (int id : replaceRows) replaceMap.put(id, replace(id, id));
            for (int id : costumeRows) costumeMap.put(id, costume(id));
            body.run();
        } finally {
            replaceMap.clear();
            replaceMap.putAll(oldReplace);
            costumeMap.clear();
            costumeMap.putAll(oldCostume);
        }
    }

    @Test
    void grantCoversExactlyTheShippedReplaceCostumes() {
        withTables(Set.copyOf(SHIPPED), Set.copyOf(SHIPPED), () -> {
            assertEquals(SHIPPED, new ArrayList<>(new TreeSet<>(PlayerProgressManager.resolveReplaceCostumeIds())));
            var player = new RecordingPlayer();
            player.getProgressManager().addReplaceCostumes();
            assertEquals(new TreeSet<>(SHIPPED), new TreeSet<>(player.getCostumeList()));
        });
    }

    @Test
    void costumeRowWithoutABackingSkinIsSkipped() {
        // A collapsed/partial costume table must not put a nonexistent skin on the player.
        withTables(Set.of(200302), Set.of(999999), () -> {
            assertTrue(PlayerProgressManager.resolveReplaceCostumeIds().isEmpty());
            var player = new RecordingPlayer();
            player.getProgressManager().addReplaceCostumes();
            assertTrue(player.getCostumeList().isEmpty());
        });
    }

    @Test
    void grantIsIdempotentAcrossRepeatedLogins() {
        withTables(Set.copyOf(SHIPPED), Set.copyOf(SHIPPED), () -> {
            var player = new RecordingPlayer();
            player.getProgressManager().addReplaceCostumes();
            int firstGrants = player.granted.size();

            player.getProgressManager().addReplaceCostumes();
            player.getProgressManager().addReplaceCostumes();

            assertEquals(new TreeSet<>(SHIPPED), new TreeSet<>(player.getCostumeList()));
            // addCostume() emits AvatarGainCostumeNotify and saves; repeats must not redo either.
            assertEquals(firstGrants, player.granted.size());
            assertEquals(SHIPPED.size(), player.granted.size());
            assertEquals(1, player.saves);
        });
    }

    @Test
    void existingOwnershipIsNotReGrantedButMissingOnesStillAre() {
        withTables(Set.copyOf(SHIPPED), Set.copyOf(SHIPPED), () -> {
            var player = new RecordingPlayer();
            player.getCostumeList().add(200302);
            player.getProgressManager().addReplaceCostumes();

            assertEquals(new TreeSet<>(SHIPPED), new TreeSet<>(player.getCostumeList()));
            assertEquals(3, player.granted.size());
            assertFalse(player.granted.contains(200302));
        });
    }

    @Test
    void unknownReplaceCostumeTableGrantsNothing() {
        // The 7.2 regression being fixed: a table that binds no rows must be a no-op, not a crash.
        withTables(Set.of(), Set.copyOf(SHIPPED), () -> {
            var player = new RecordingPlayer();
            player.getProgressManager().addReplaceCostumes();
            assertTrue(player.getCostumeList().isEmpty());
            assertEquals(0, player.saves);
        });
    }

    /**
     * The defect this card closes was not a wrong grant but an unreachable one: the grant method had
     * zero call sites, so when the resource table finally bound its four rows nothing consumed them.
     * A behavioural test cannot catch a missing caller, so pin the caller at the bytecode level.
     */
    @Test
    void loginPathActuallyCallsTheGrant() throws Exception {
        assertEquals(
                Set.of("onPlayerLogin"),
                callersOf("addReplaceCostumes", PlayerProgressManager.class.getSimpleName()),
                "addReplaceCostumes() must be invoked from PlayerProgressManager, or the four "
                        + "replace costumes stay unreachable");
    }

    /** Finds the methods of {@code owner} whose bytecode invokes {@code targetMethod}. */
    private static Set<String> callersOf(String targetMethod, String owner) throws Exception {
        var resource = PlayerProgressManager.class.getResourceAsStream(owner + ".class");
        assertNotNull(resource, "class bytes for " + owner);
        var callers = new java.util.TreeSet<String>();
        try (var in = resource) {
            new org.objectweb.asm.ClassReader(in)
                    .accept(
                            new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                                @Override
                                public org.objectweb.asm.MethodVisitor visitMethod(
                                        int access, String name, String desc, String sig,
                                        String[] exceptions) {
                                    return new org.objectweb.asm.MethodVisitor(
                                            org.objectweb.asm.Opcodes.ASM9) {
                                        @Override
                                        public void visitMethodInsn(
                                                int opcode, String methodOwner, String methodName,
                                                String methodDesc, boolean isInterface) {
                                            if (targetMethod.equals(methodName)) {
                                                callers.add(name);
                                            }
                                        }
                                    };
                                }
                            },
                            0);
        }
        return callers;
    }

    /** Player without a session or a database: records the grant and save side effects instead. */
    private static final class RecordingPlayer extends Player {
        final List<Integer> granted = new ArrayList<>();
        int saves;

        @Override
        public void addCostume(int costumeId) {
            if (!getCostumeList().contains(costumeId)) granted.add(costumeId);
            super.addCostume(costumeId);
        }

        @Override
        public void sendPacket(emu.grasscutter.net.packet.BasePacket packet) {
            // No session in a unit test; addCostume()'s AvatarGainCostumeNotify is recorded above.
        }

        @Override
        public void save() {
            saves++;
        }
    }
}
