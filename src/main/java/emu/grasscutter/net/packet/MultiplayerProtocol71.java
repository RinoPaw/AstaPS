package emu.grasscutter.net.packet;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.WireFormat;
import java.io.IOException;

/** Current Genshin 7.1 multiplayer wire layout confirmed from live traffic. */
public final class MultiplayerProtocol71 {
    public static final int GET_ONLINE_PLAYER_LIST_REQ = 27872;
    public static final int GET_RECENT_MP_PLAYER_LIST_REQ = 1757;
    public static final int PLAYER_APPLY_ENTER_MP_REQ = 29174;
    public static final int PLAYER_APPLY_ENTER_MP_RESULT_REQ = 28491;
    public static final int BACK_MY_WORLD_REQ = 24714;

    private static final int APPLY_TARGET_UID_FIELD = 3;
    private static final int APPLY_RESULT_IS_AGREED_FIELD = 9;
    private static final int APPLY_RESULT_UID_FIELD = 12;

    private MultiplayerProtocol71() {}

    public record ApplyEnterMpRequest(int targetUid) {}

    public record ApplyEnterMpResultRequest(int applyUid, boolean isAgreed) {}

    public static ApplyEnterMpRequest decodeApplyEnterMpRequest(byte[] payload) throws IOException {
        CodedInputStream input = CodedInputStream.newInstance(payload);
        int targetUid = 0;

        while (!input.isAtEnd()) {
            int tag = input.readTag();
            if (tag == 0) break;

            int field = WireFormat.getTagFieldNumber(tag);
            if (field == APPLY_TARGET_UID_FIELD) {
                requireVarint(tag, "PlayerApplyEnterMpReq.target_uid");
                targetUid = input.readUInt32();
            } else {
                input.skipField(tag);
            }
        }

        return new ApplyEnterMpRequest(targetUid);
    }

    public static ApplyEnterMpResultRequest decodeApplyEnterMpResultRequest(byte[] payload)
            throws IOException {
        CodedInputStream input = CodedInputStream.newInstance(payload);
        int applyUid = 0;
        boolean isAgreed = false;

        while (!input.isAtEnd()) {
            int tag = input.readTag();
            if (tag == 0) break;

            int field = WireFormat.getTagFieldNumber(tag);
            if (field == APPLY_RESULT_IS_AGREED_FIELD) {
                requireVarint(tag, "PlayerApplyEnterMpResultReq.is_agreed");
                isAgreed = input.readBool();
            } else if (field == APPLY_RESULT_UID_FIELD) {
                requireVarint(tag, "PlayerApplyEnterMpResultReq.apply_uid");
                applyUid = input.readUInt32();
            } else {
                input.skipField(tag);
            }
        }

        return new ApplyEnterMpResultRequest(applyUid, isAgreed);
    }

    private static void requireVarint(int tag, String field) throws InvalidProtocolBufferException {
        if (WireFormat.getTagWireType(tag) != WireFormat.WIRETYPE_VARINT) {
            throw new InvalidProtocolBufferException(field + " must use varint wire type");
        }
    }
}
