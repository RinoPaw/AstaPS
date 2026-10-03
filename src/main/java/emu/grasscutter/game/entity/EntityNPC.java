package emu.grasscutter.game.entity;

import emu.grasscutter.game.props.EntityIdType;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.scripts.data.SceneNPC;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import lombok.Getter;

public class EntityNPC extends GameEntity {
    @Getter(onMethod_ = @Override)
    private final Position position;

    @Getter(onMethod_ = @Override)
    private final Position rotation;

    private final int npcId;
    private final int roomId;
    private final int parentQuestId;
    @Getter private final int suiteId;

    public EntityNPC(Scene scene, SceneNPC metaNPC, int blockId, int suiteId) {
        super(scene);
        this.id = getScene().getWorld().getNextEntityId(EntityIdType.NPC);
        setConfigId(metaNPC.config_id);
        setGroupId(metaNPC.group.id);
        setBlockId(blockId);
        this.suiteId = suiteId;
        this.position = metaNPC.pos.clone();
        this.rotation = metaNPC.rot.clone();
        this.npcId = metaNPC.npc_id;
        this.roomId = 0;
        this.parentQuestId = 0;
    }

    /** Creates a client-requested quest NPC which has no SceneNPC/group metadata. */
    public EntityNPC(
            Scene scene,
            int npcId,
            Position position,
            Position rotation,
            int roomId,
            int parentQuestId) {
        super(scene);
        this.id = getScene().getWorld().getNextEntityId(EntityIdType.NPC);
        this.npcId = npcId;
        this.position = position.clone();
        this.rotation = rotation.clone();
        this.roomId = roomId;
        this.parentQuestId = parentQuestId;
        this.suiteId = 0;
    }

    @Override
    public int getEntityTypeId() {
        return this.npcId;
    }

    public boolean belongsToQuest(int parentQuestId, int npcId) {
        return this.parentQuestId != 0
                && this.parentQuestId == parentQuestId
                && this.npcId == npcId;
    }

    /**
     * Nothing fights this entity, but an ability attached to one reads the whole FightProperty set
     * off its owner, and a null threw right through the action instead of reading zeroes.
     */
    @Override
    public Int2FloatMap getFightProperties() {
        return this.fightProperties;
    }

    private final Int2FloatMap fightProperties = new Int2FloatOpenHashMap();

    @Override
    public SceneEntityInfoOuterClass.SceneEntityInfo toProto() {

        EntityAuthorityInfoOuterClass.EntityAuthorityInfo authority =
                EntityAuthorityInfoOuterClass.EntityAuthorityInfo.newBuilder()
                        .setAbilityInfo(AbilitySyncStateInfoOuterClass.AbilitySyncStateInfo.newBuilder())
                        .setRendererChangedInfo(
                                EntityRendererChangedInfoOuterClass.EntityRendererChangedInfo.newBuilder())
                        .setAiInfo(
                                SceneEntityAiInfoOuterClass.SceneEntityAiInfo.newBuilder()
                                        .setIsEnteredCombat(true))
                        .setBornPos(getPosition().toProto())
                        .build();

        SceneEntityInfoOuterClass.SceneEntityInfo.Builder entityInfo =
                SceneEntityInfoOuterClass.SceneEntityInfo.newBuilder()
                        .setEntityId(getId())
                        .setEntityType(ProtEntityTypeOuterClass.ProtEntityType.ProtEntityType_PROT_ENTITY_NPC)
                        .setMotionInfo(
                                MotionInfoOuterClass.MotionInfo.newBuilder()
                                        .setPos(getPosition().toProto())
                                        .setRot(getRotation().toProto())
                                        .setSpeed(VectorOuterClass.Vector.newBuilder()))
                        .addAnimatorParaList(
                                AnimatorParameterValueInfoPairOuterClass.AnimatorParameterValueInfoPair
                                        .newBuilder())
                        .setEntityClientData(EntityClientDataOuterClass.EntityClientData.newBuilder())
                        .setEntityAuthorityInfo(authority)
                        .setLifeState(1);

        this.injectIntMotionInfo(entityInfo);

        entityInfo.setNpc(
                SceneNpcInfoOuterClass.SceneNpcInfo.newBuilder()
                        .setNpcId(this.npcId)
                        .setRoomId(this.roomId)
                        .setParentQuestId(this.parentQuestId)
                        .setBlockId(getBlockId())
                        .build());

        return entityInfo.build();
    }

    @Override
    public void initAbilities() {
        // NPCs do not carry server-side abilities of their own.
    }
}
