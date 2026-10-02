package emu.grasscutter.game.drop;

import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.server.game.BaseGameSystem;
import emu.grasscutter.server.game.GameServer;

/** Temporary call-site shim while the legacy fallback wiring is removed. */
public final class DropSystemLegacy extends BaseGameSystem {
    public DropSystemLegacy(GameServer server) {
        super(server);
    }

    public void callDrop(EntityMonster monster) {}
}
