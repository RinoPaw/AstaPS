package emu.grasscutter.net.proto;

import com.google.protobuf.InvalidProtocolBufferException;

/** Reads the avatar GUID from the current ascension request schema. */
public final class AvatarPromoteReqParser {
    private AvatarPromoteReqParser() {}

    /** The avatar guid carried by a valid ascension request, or 0 when absent or malformed. */
    public static long parseGuid(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return 0;
        }
        try {
            return AvatarPromoteReqOuterClass.AvatarPromoteReq.parseFrom(payload).getGuid();
        } catch (InvalidProtocolBufferException ignored) {
            return 0;
        }
    }
}
