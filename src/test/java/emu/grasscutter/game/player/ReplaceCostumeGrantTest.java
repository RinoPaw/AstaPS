package emu.grasscutter.game.player;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.avatar.AvatarCostumeData;
import emu.grasscutter.data.excels.avatar.AvatarReplaceCostumeData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

/**
 * The four free alternate outfits (Jean, Amber, Mona, Rosaria) are the rows of
 * {@code AvatarCostumeExcelConfigData} flagged {@code isDefaultUnlock=true}. They are referenced by
 * no shop, reward or giving row, so {@code addReplaceCostumes()} is the only path that can ever hand
 * them to a player.
 *
 * <p>The grant keys off that flag, not off {@code AvatarReplaceCostumeExcelConfigData} membership.
 * The two sets hold the same four ids in the shipped 7.2 tree, but the replace table has no
 * ownership column — it says "this outfit stands in for the default look", a different question.
 * These tests pin the grant to ownership so that a future tree listing a <em>purchasable</em>
 * outfit there cannot silently hand it out for free.
 */
@ResourceLock("GameData")
final class ReplaceCostumeGrantTest {
    private static final List<Integer> SHIPPED = List.of(200302, 202101, 204101, 204501);

    private static AvatarReplaceCostumeData replace(int avatarId, int costumeId) {
        return new Gson().fromJson(
                "{\"avatarId\":%d,\"replace_costume_id\":%d}".formatted(avatarId, costumeId),
                AvatarReplaceCostumeData.class);
    }

    private static AvatarCostumeData costume(int skinId, boolean isDefaultUnlock) {
        return new Gson().fromJson(
                "{\"skinId\":%d,\"itemId\":%d,\"characterId\":10000003,\"isDefaultUnlock\":%b}"
                        .formatted(skinId, 340006, isDefaultUnlock),
                AvatarCostumeData.class);
    }

    /** Runs {@code body} with both costume tables replaced by the given contents. */
    private static void withTables(
            Set<Integer> replaceRows, Map<Integer, Boolean> costumeRows, Runnable body) {
        var replaceMap = GameData.getAvatarReplaceCostumeDataMap();
        var costumeMap = GameData.getAvatarCostumeDataMap();
        var oldReplace = new java.util.HashMap<>(replaceMap);
        var oldCostume = new java.util.HashMap<>(costumeMap);
        replaceMap.clear();
        costumeMap.clear();
        try {
            for (int id : replaceRows) replaceMap.put(id, replace(id, id));
            costumeRows.forEach((id, flagged) -> costumeMap.put(id, costume(id, flagged)));
            body.run();
        } finally {
            replaceMap.clear();
            replaceMap.putAll(oldReplace);
            costumeMap.clear();
            costumeMap.putAll(oldCostume);
        }
    }

    /** The shipped 7.2 shape: all four outfits are replace rows and all four are flagged. */
    private static void withShippedShape(Runnable body) {
        withTables(
                Set.copyOf(SHIPPED),
                Map.of(200302, true, 202101, true, 204101, true, 204501, true),
                body);
    }

    @Test
    void grantCoversExactlyTheShippedReplaceCostumes() {
        withShippedShape(
                () -> {
                    assertEquals(
                            SHIPPED,
                            new ArrayList<>(
                                    new TreeSet<>(
                                            PlayerProgressManager
                                                    .resolveDefaultUnlockCostumeIds())));
                    var player = new RecordingPlayer();
                    player.getProgressManager().addReplaceCostumes();
                    assertEquals(new TreeSet<>(SHIPPED), new TreeSet<>(player.getCostumeList()));
                });
    }

    /**
     * The coupling this card exists to pin. Ownership is the flag; replace-table membership is a
     * different concept that only coincides with it today. If the two ever diverge the grant must
     * follow the flag, both ways round.
     */
    @Test
    void grantFollowsTheOwnershipFlagNotTheReplaceTable() {
        withTables(
                // 900001 is a replace row that is NOT flagged: a purchasable outfit.
                // 900002 is flagged but not a replace row: free regardless.
                Set.of(900001),
                Map.of(900001, false, 900002, true),
                () -> {
                    assertEquals(
                            Set.of(900002),
                            PlayerProgressManager.resolveDefaultUnlockCostumeIds());
                    var player = new RecordingPlayer();
                    player.getProgressManager().addReplaceCostumes();
                    assertEquals(Set.of(900002), new TreeSet<>(player.getCostumeList()));
                    assertFalse(
                            player.getCostumeList().contains(900001),
                            "a replace-table row that is not flagged must not be granted for free");
                });
    }

    @Test
    void costumeRowWithoutABackingSkinIsSkipped() {
        // A collapsed or partial costume table must not put a nonexistent skin on the player: the
        // flag can only be read from a row that actually loaded, so nothing is flagged here.
        withTables(Set.of(200302), Map.of(999999, false), () -> {
            assertTrue(PlayerProgressManager.resolveDefaultUnlockCostumeIds().isEmpty());
            var player = new RecordingPlayer();
            player.getProgressManager().addReplaceCostumes();
            assertTrue(player.getCostumeList().isEmpty());
        });
    }

    @Test
    void grantIsIdempotentAcrossRepeatedLogins() {
        withShippedShape(
                () -> {
                    var player = new RecordingPlayer();
                    player.getProgressManager().addReplaceCostumes();
                    int firstGrants = player.granted.size();

                    player.getProgressManager().addReplaceCostumes();
                    player.getProgressManager().addReplaceCostumes();

                    assertEquals(new TreeSet<>(SHIPPED), new TreeSet<>(player.getCostumeList()));
                    // addCostume() emits AvatarGainCostumeNotify and saves; repeats must not redo
                    // either.
                    assertEquals(firstGrants, player.granted.size());
                    assertEquals(SHIPPED.size(), player.granted.size());
                    assertEquals(1, player.saves);
                });
    }

    @Test
    void existingOwnershipIsNotReGrantedButMissingOnesStillAre() {
        withShippedShape(
                () -> {
                    var player = new RecordingPlayer();
                    player.getCostumeList().add(200302);
                    player.getProgressManager().addReplaceCostumes();

                    assertEquals(new TreeSet<>(SHIPPED), new TreeSet<>(player.getCostumeList()));
                    assertEquals(3, player.granted.size());
                    assertFalse(player.granted.contains(200302));
                });
    }

    @Test
    void emptyCostumeTableGrantsNothing() {
        // The 7.2 regression being fixed: a table that binds no rows must be a no-op, not a crash.
        withTables(Set.of(), Map.of(), () -> {
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
