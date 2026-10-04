package emu.grasscutter.server.packet.recv;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public final class HandlerAvatarDieAnimationEndReqTest {
    @Test
    public void avatarGuidResolvesToSceneEntityId() {
        assertEquals(0x0100002aL, HandlerAvatarDieAnimationEndReq.resolveDeathEntityId(42L, 42L, 0x0100002a));
    }

    @Test
    public void sceneEntityIdPassesThrough() {
        assertEquals(
                0x0100002aL,
                HandlerAvatarDieAnimationEndReq.resolveDeathEntityId(
                        0x0100002aL, 42L, 0x0100002a));
    }
}
