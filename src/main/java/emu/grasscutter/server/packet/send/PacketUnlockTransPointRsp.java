package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.UnlockTransPointRspOuterClass.UnlockTransPointRsp;

public class PacketUnlockTransPointRsp extends BasePacket {
    public static final int DISABLED = 0;
    public static final int CANDIDATE_36641 = 36641;
    public static final int CANDIDATE_20290 = 20290;

    private static final String CMD_PROPERTY = "astaps.unlockTransPointRspCmd";
    private static final String CMD_ENV = "ASTAPS_UNLOCK_TRANS_POINT_RSP_CMD";
    private static final int SELECTED_CMD_ID = resolveSelectedCmdId();

    public static int getSelectedCmdId() {
        return SELECTED_CMD_ID;
    }

    public static boolean isDisabled() {
        return SELECTED_CMD_ID == DISABLED;
    }

    public PacketUnlockTransPointRsp(Retcode retcode) {
        this(0, retcode);
    }

    public PacketUnlockTransPointRsp(int clientSequenceId, Retcode retcode) {
        super(requireEnabledCmdId(), clientSequenceId);

        UnlockTransPointRsp proto =
                UnlockTransPointRsp.newBuilder().setRetcode(retcode.getNumber()).build();

        this.setData(proto);
    }

    private static int requireEnabledCmdId() {
        if (SELECTED_CMD_ID == DISABLED) {
            throw new IllegalStateException(
                    "UnlockTransPointRsp test probe is disabled; do not construct the response packet");
        }
        return SELECTED_CMD_ID;
    }

    private static int resolveSelectedCmdId() {
        String configured = System.getProperty(CMD_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(CMD_ENV);
        }

        if (configured == null || configured.isBlank()) {
            return CANDIDATE_36641;
        }

        configured = configured.trim();
        if (configured.equalsIgnoreCase("none") || configured.equals("0")) {
            return DISABLED;
        }

        final int cmdId;
        try {
            cmdId = Integer.parseInt(configured);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid UnlockTransPointRsp test CmdId '" + configured + "'", e);
        }

        if (cmdId != CANDIDATE_36641 && cmdId != CANDIDATE_20290) {
            throw new IllegalArgumentException(
                    "UnlockTransPointRsp test CmdId must be none/0, "
                            + CANDIDATE_36641
                            + " or "
                            + CANDIDATE_20290
                            + ", got "
                            + cmdId);
        }

        return cmdId;
    }
}
