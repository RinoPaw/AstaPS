package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketCombineRsp;

import java.util.List;
import java.util.stream.Collectors;

@Opcodes(PacketOpcodes.CombineReq)
public class HandlerCombineReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {

        CombineReqOuterClass.CombineReq req = CombineReqOuterClass.CombineReq.parseFrom(payload);
        int count = req.getCombineCount();
        if (count <= 0) {
            session.send(
                    new PacketCombineRsp(
                            RetcodeOuterClass.Retcode.RET_COMBINE_COUNT_TOO_LARGE_VALUE));
            return;
        }
        Grasscutter.getLogger()
                .info(
                        "Received CombineReq: combineId={}, count={}, avatarGuid={}",
                        req.getCombineId(),
                        req.getCombineCount(),
                        req.getAvatarGuid());

        var result =
                session.getServer()
                        .getCombineSystem()
                        .combineItem(session.getPlayer(), req.getCombineId(), count);

        if (result == null) {
            return;
        }

        session.send(
                new PacketCombineRsp(
                        req,
                        toItemParamList(result.getMaterial()),
                        // 客户端按合成次数计算总产量，这里只传单次产量。
                        result.getResult().stream()
                                .map(
                                        item ->
                                                ItemParamOuterClass.ItemParam.newBuilder()
                                                        .setItemId(item.getId())
                                                        .setCount(item.getCount() / count)
                                                        .build())
                                .toList(),
                        List.of(),
                        toItemParamList(result.getBack()),
                        toItemParamList(result.getExtra())));
    }

    private List<ItemParamOuterClass.ItemParam> toItemParamList(List<ItemParamData> list) {
        return list.stream()
                .map(
                        item ->
                                ItemParamOuterClass.ItemParam.newBuilder()
                                        .setItemId(item.getId())
                                        .setCount(item.getCount())
                                        .build())
                .collect(Collectors.toList());
    }
}
