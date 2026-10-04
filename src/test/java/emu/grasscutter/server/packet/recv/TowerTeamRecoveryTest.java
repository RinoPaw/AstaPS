package emu.grasscutter.server.packet.recv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.net.proto.TowerTeamOuterClass.TowerTeam;
import emu.grasscutter.net.proto.TowerTeamSelectReqOuterClass.TowerTeamSelectReq;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins TowerTeamSelectReq's field numbers to what the 7.1 client writes.
 *
 * <p>A dump that reads fields out of the client's parse routine alone loses this message's team
 * list - the client only ever sends it - and the abyss then silently gets no team. In the 7.1 dump
 * tower_team_list is field 6 and floor_id field 3 (7.0 had them at 8 and 9).
 *
 * <p>These cases build the payload by hand at those numbers rather than through the generated
 * builder, so a regeneration that dropped tower_team_list again would fail here instead of in game.
 */
public class TowerTeamRecoveryTest {

    private static final long AMBER = 4294967297L;
    private static final long KAEYA = 4294967298L;
    private static final long LISA = 4294967299L;
    private static final long BARBARA = 4294967300L;

    private static byte[] team(int teamId, long... guids) {
        var builder = TowerTeam.newBuilder().setTowerTeamId(teamId);
        for (long guid : guids) builder.addAvatarGuidList(guid);
        return builder.build().toByteArray();
    }

    /** The request as the 7.1 client lays it out: teams at field 6, floor at field 3. */
    private static TowerTeamSelectReq onTheWire(int floorId, byte[]... teams) throws Exception {
        var bytes = new ByteArrayOutputStream();
        var output = CodedOutputStream.newInstance(bytes);
        for (byte[] team : teams) output.writeByteArray(6, team);
        output.writeUInt32(3, floorId);
        output.flush();
        return TowerTeamSelectReq.parseFrom(bytes.toByteArray());
    }

    @Test
    @DisplayName("reads the 7.1 floor and both teams off the wire")
    public void readsTwoTeams() throws Exception {
        var req = onTheWire(1024, team(1, AMBER, KAEYA), team(2, LISA, BARBARA));

        assertEquals(1024, req.getFloorId());
        assertTrue(
                req.getUnknownFields().asMap().isEmpty(), "nothing left unparsed in the request");
        assertEquals(2, req.getTowerTeamListCount());
        assertEquals(List.of(AMBER, KAEYA), req.getTowerTeamList(0).getAvatarGuidListList());
        assertEquals(List.of(LISA, BARBARA), req.getTowerTeamList(1).getAvatarGuidListList());
        assertEquals(1, req.getTowerTeamList(0).getTowerTeamId());
        assertEquals(2, req.getTowerTeamList(1).getTowerTeamId());
    }

    @Test
    @DisplayName("the mid-chamber team-change notify still loads")
    public void middleLevelChangeTeamNotifyLoads() {
        // 7.0 does not name this message, so it keeps its 6.7 class - and a restored 6.7 class is
        // exactly where a corrupt embedded descriptor bites, at first load rather than at build.
        // Its body is empty by design; only the CmdId carries meaning, and that is still unknown.
        var proto =
                emu.grasscutter.net.proto.TowerMiddleLevelChangeTeamNotifyOuterClass
                        .TowerMiddleLevelChangeTeamNotify.newBuilder()
                        .build();
        assertEquals(0, proto.toByteArray().length);
    }
}
