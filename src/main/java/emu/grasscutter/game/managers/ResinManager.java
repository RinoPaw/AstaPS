package emu.grasscutter.game.managers;

import static emu.grasscutter.config.Configuration.GAME;

import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.proto.RetcodeOuterClass;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.Utils;

public class ResinManager extends BasePlayerManager {
    public static final int MAX_RESIN_BUYING_COUNT = 15;
    /** Official client UI hardcodes a daily max of 6; clamp notify so the button stays usable. */
    public static final int CLIENT_RESIN_BUY_CAP = 6;
    public static final int AMOUNT_TO_ADD = 60;
    public static final int[] HCOIN_NUM_TO_BUY_RESIN =
            new int[] {50, 100, 100, 150, 200, 200, 200, 200, 200, 200, 200, 200, 200, 200, 200};

    public ResinManager(Player player) {
        super(player);
    }

    public synchronized boolean useResin(int amount) {
        if (!GAME.resin.resinUsage) return true;

        int currentResin = this.player.getProperty(PlayerProperty.PROP_PLAYER_RESIN);
        if (currentResin < amount) return false;

        int newResin = currentResin - amount;
        this.player.setProperty(PlayerProperty.PROP_PLAYER_RESIN, newResin);

        if (this.player.getNextResinRefresh() == 0 && newResin < GAME.resin.cap) {
            int currentTime = Utils.getCurrentSeconds();
            this.player.setNextResinRefresh(currentTime + GAME.resin.rechargeTime);
        }

        this.player.sendPacket(new PacketResinChangeNotify(this.player));
        this.player
                .getBattlePassManager()
                .triggerMission(WatcherTriggerType.TRIGGER_COST_MATERIAL, 106, amount);
        return true;
    }

    public synchronized boolean useCondensedResin(int amount) {
        if (!GAME.resin.resinUsage) return true;
        return this.player.getInventory().payItem(220007, amount);
    }

    public synchronized void addResin(int amount) {
        if (!GAME.resin.resinUsage) return;

        int currentResin = this.player.getProperty(PlayerProperty.PROP_PLAYER_RESIN);
        int newResin = currentResin + amount;
        this.player.setProperty(PlayerProperty.PROP_PLAYER_RESIN, newResin);
        if (newResin >= GAME.resin.cap) this.player.setNextResinRefresh(0);
        this.player.sendPacket(new PacketResinChangeNotify(this.player));
    }

    public synchronized void rechargeResin() {
        if (!GAME.resin.resinUsage) return;

        int currentResin = this.player.getProperty(PlayerProperty.PROP_PLAYER_RESIN);
        int currentTime = Utils.getCurrentSeconds();
        if (this.player.getNextResinRefresh() <= 0 || currentTime < this.player.getNextResinRefresh()) {
            return;
        }

        int recharge =
                1
                        + (int)
                                ((currentTime - this.player.getNextResinRefresh())
                                        / GAME.resin.rechargeTime);
        int newResin = Math.min(GAME.resin.cap, currentResin + recharge);
        int resinChange = newResin - currentResin;
        this.player.setProperty(PlayerProperty.PROP_PLAYER_RESIN, newResin);

        if (newResin >= GAME.resin.cap) {
            this.player.setNextResinRefresh(0);
        } else {
            int nextRecharge =
                    this.player.getNextResinRefresh() + resinChange * GAME.resin.rechargeTime;
            this.player.setNextResinRefresh(nextRecharge);
        }
        this.player.sendPacket(new PacketResinChangeNotify(this.player));
    }

    public synchronized void onPlayerLogin() {
        if (!GAME.resin.resinUsage) {
            this.player.setProperty(PlayerProperty.PROP_PLAYER_RESIN, GAME.resin.cap);
            this.player.setNextResinRefresh(0);
        }

        int currentResin = this.player.getProperty(PlayerProperty.PROP_PLAYER_RESIN);
        int currentTime = Utils.getCurrentSeconds();
        if (currentResin < GAME.resin.cap && this.player.getNextResinRefresh() == 0) {
            this.player.setNextResinRefresh(currentTime + GAME.resin.rechargeTime);
        }

        this.player.getOpenStates().put(45, 1);
        this.player.sendPacket(new PacketOpenStateChangeNotify(45, 1));
        this.player.sendPacket(new PacketResinChangeNotify(this.player));
    }

    public synchronized void refreshClientResinUi() {
        this.player.getOpenStates().put(45, 1);
        this.player.sendPacket(new PacketOpenStateChangeNotify(45, 1));
        this.player.sendPacket(new PacketResinChangeNotify(this.player));
        this.player.sendPacket(
                new PacketPlayerPropNotify(this.player, PlayerProperty.PROP_PLAYER_RESIN));
    }

    public int buy() {
        if (this.player.getResinBuyCount() >= MAX_RESIN_BUYING_COUNT) {
            return RetcodeOuterClass.Retcode.RET_RESIN_BOUGHT_COUNT_EXCEEDED_VALUE;
        }

        var res =
                this.player
                        .getInventory()
                        .payItem(201, HCOIN_NUM_TO_BUY_RESIN[this.player.getResinBuyCount()]);
        if (!res) return RetcodeOuterClass.Retcode.RET_HCOIN_NOT_ENOUGH_VALUE;

        this.player.setResinBuyCount(this.player.getResinBuyCount() + 1);
        this.player.setProperty(PlayerProperty.PROP_PLAYER_WAIT_SUB_HCOIN, 0);
        this.addResin(AMOUNT_TO_ADD);
        this.player.sendPacket(
                new PacketItemAddHintNotify(new GameItem(106, AMOUNT_TO_ADD), ActionReason.BuyResin));
        return 0;
    }

    public synchronized boolean payHcoinRewardClaim() {
        if (!GAME.resin.resinUsage) return true;
        int used = this.player.getResinBuyCount();
        if (used >= MAX_RESIN_BUYING_COUNT) return false;
        int cost = HCOIN_NUM_TO_BUY_RESIN[used];
        if (!this.player.getInventory().payItem(201, cost)) return false;
        this.player.setResinBuyCount(used + 1);
        this.player.setProperty(PlayerProperty.PROP_PLAYER_WAIT_SUB_HCOIN, 0);
        this.player.sendPacket(new PacketResinChangeNotify(this.player));
        return true;
    }

    public int getClientResinBuyCount() {
        int actual = this.player.getResinBuyCount();
        if (actual >= MAX_RESIN_BUYING_COUNT) return CLIENT_RESIN_BUY_CAP;
        return Math.min(actual, CLIENT_RESIN_BUY_CAP - 1);
    }
}
