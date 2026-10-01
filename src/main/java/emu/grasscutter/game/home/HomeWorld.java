package emu.grasscutter.game.home;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.World;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.ChatInfoOuterClass;
import emu.grasscutter.net.proto.SystemHintOuterClass;
import emu.grasscutter.net.proto.SystemHintTypeOuterClass;
import emu.grasscutter.server.born.BornIntroGate;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.packet.send.PacketDelTeamEntityNotify;
import emu.grasscutter.server.packet.send.PacketPlayerChatNotify;
import emu.grasscutter.server.packet.send.PacketPlayerGameTimeNotify;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import lombok.Getter;

@Getter
public class HomeWorld extends World {
    private final GameHome home;
    private HomeModuleManager moduleManager;

    public HomeWorld(GameServer server, Player owner) {
        super(server, owner);

        GameHome ownerHome = owner.getHome();
        boolean freshBorn = BornIntroGate.isAwaiting(owner.getSession());

        // Fresh 7.1 character creation has a native intro deadline. A missing Home record is the
        // expected state on a brand-new database, so do not put a synchronous database miss and
        // default-home save on the first scene-entry critical path. Existing players still load
        // their persisted Home normally.
        this.home =
                ownerHome != null
                        ? ownerHome
                        : freshBorn
                                ? GameHome.create(owner.getUid())
                                : GameHome.getByUid(owner.getUid());

        // A fresh player has no realm selected yet. Building the module manager would create Home
        // scenes that cannot be entered and only delays the first open-world handoff. The manager
        // is refreshed when a valid realm is selected later.
        if (!freshBorn || owner.getCurrentRealmId() > 0) {
            this.refreshModuleManager();
        }
    }

    @Override
    public boolean onTick() {
        if (this.getPlayerCount() == 0) {
            // Empty home worlds are not useful tick targets. Returning true lets GameServer
            // remove the world and its separate home-world cache entry.
            return true;
        }
        if (this.moduleManager == null) {
            return false;
        }
        this.moduleManager.tick();

        if (this.getTickCount() % 10 == 0) {
            this.getPlayers().forEach(p -> p.sendPacket(new PacketPlayerGameTimeNotify(p)));
        }

        if (this.isInHome(this.getHost()) && this.getTickCount() % 60 == 0) {
            this.getHost().updatePlayerGameTime(this.getCurrentWorldTime());
        }

        this.tickCount++;
        return false;
    }

    public void refreshModuleManager() {
        if (this.moduleManager != null) {
            this.moduleManager.onRemovedModule();
        }

        this.moduleManager = new HomeModuleManager(this);
        this.moduleManager.onSetModule();
    }

    public int getActiveOutdoorSceneId() {
        return this.getHost().getCurrentRealmId() + 2000;
    }

    public int getActiveIndoorSceneId() {
        return this.isRealmIdValid()
                ? this.getSceneById(this.getActiveOutdoorSceneId()).getSceneItem().getRoomSceneId()
                : -1;
    }

    public boolean isRealmIdValid() {
        return this.getSceneById(this.getHost().getCurrentRealmId() + 2000) != null;
    }

    @Override
    public synchronized void addPlayer(Player player) {
        // Check if player already in
        if (this.getPlayers().contains(player)) {
            return;
        }

        // Remove player from prev world
        if (player.getWorld() != null) {
            player.getWorld().removePlayer(player);
        }

        // Register
        player.setWorld(this);
        this.getPlayers().add(player);

        // Set player variables
        if (this.getHost().equals(player)) {
            player.setPeerId(1);
            this.getGuests().forEach(player1 -> player1.setPeerId(player1.getPeerId() + 1));
        } else {
            player.setPeerId(this.getNextPeerId());
        }

        player.getTeamManager().setEntity(new EntityTeam(player));

        // Copy main team to multiplayer team
        if (this.isMultiplayer()) {
            player
                    .getTeamManager()
                    .getMpTeam()
                    .copyFrom(
                            player.getTeamManager().getCurrentSinglePlayerTeamInfo(),
                            player.getTeamManager().getMaxTeamSize());
            player.getTeamManager().setCurrentCharacterIndex(0);

            if (!player.equals(this.getHost())) {
                this.broadcastPacket(
                        new PacketPlayerChatNotify(
                                player,
                                0,
                                SystemHintOuterClass.SystemHint.newBuilder()
                                        .setType(
                                                SystemHintTypeOuterClass.SystemHintType.SYSTEM_HINT_TYPE_CHAT_ENTER_WORLD
                                                        .getNumber())
                                        .build()));
            }
        }

        // Add to scene
        var scene = this.getSceneById(player.getSceneId());
        scene.addPlayer(player);

        // Info packet for other players
        if (this.getPlayers().size() > 1) {
            this.updatePlayerInfos(player);
        }
    }

    @Override
    public synchronized void removePlayer(Player player) {
        // Remove team entities
        this.broadcastPacket(
                new PacketDelTeamEntityNotify(
                        player.getSceneId(),
                        this.getPlayers().stream()
                                .map(
                                        p ->
                                                p.getTeamManager().getEntity() == null
                                                        ? 0
                                                        : p.getTeamManager().getEntity().getId())
                                .toList()));

        // Deregister
        this.getPlayers().remove(player);
        player.setWorld(null);

        // Remove from scene
        var scene = this.getSceneById(player.getSceneId());
        if (scene != null) {
            scene.removePlayer(player);
        }

        // Info packet for other players
        if (!this.getPlayers().isEmpty()) {
            this.updatePlayerInfos(player);
        }

        this.broadcastPacket(
                new PacketPlayerChatNotify(
                        player,
                        0,
                        SystemHintOuterClass.SystemHint.newBuilder()
                                .setType(
                                        SystemHintTypeOuterClass.SystemHintType.SYSTEM_HINT_TYPE_CHAT_LEAVE_WORLD
                                                .getNumber())
                                .build()));
    }

    @Override
    @Nullable public HomeScene getSceneById(int sceneId) {
        var scene = this.getScenes().get(sceneId);
        if (scene instanceof HomeScene homeScene) {
            return homeScene;
        }

        var sceneData = GameData.getSceneDataMap().get(sceneId);
        if (sceneData != null) {
            scene = new HomeScene(this, sceneData);
            this.registerScene(scene);
            return (HomeScene) scene;
        }

        return null;
    }

    @Override
    public int getNextPeerId() {
        return this.getPlayers().size() + 1;
    }

    @Override
    public synchronized void setHost(Player host) {
        super.setHost(host);
    }

    @Override
    public final boolean isMultiplayer() {
        return true;
    }

    @Override
    public final boolean isPaused() {
        return false;
    }

    @Override
    public final boolean isTimeLocked() {
        return false;
    }

    public int getOwnerUid() {
        return this.getHost().getUid();
    }

    public List<Player> getGuests() {
        return this.getPlayers().stream().filter(player -> !player.equals(this.getHost())).toList();
    }

    public boolean isInHome(Player player) {
        return this.getPlayers().contains(player);
    }

    public void ifHost(Player hostOrGuest, Consumer<Player> ifHost) {
        if (this.getHost().equals(hostOrGuest)) {
            ifHost.accept(hostOrGuest);
        }
    }

    public void sendPacketToHostIfOnline(BasePacket basePacket) {
        if (this.getHost().isOnline()) {
            this.getHost().sendPacket(basePacket);
        }
    }
}
