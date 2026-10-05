package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.CreateVehicleRspOuterClass.CreateVehicleRsp;
import emu.grasscutter.net.proto.VehicleInteractTypeOuterClass;
import emu.grasscutter.net.proto.VehicleMemberOuterClass.VehicleMember;
import java.util.List;

public class PacketCreateVehicleRsp extends BasePacket {

    public PacketCreateVehicleRsp(
            Player player, int vehicleId, int pointId, Position pos, Position rot) {
        super(PacketOpcodes.CreateVehicleRsp);
        CreateVehicleRsp.Builder proto = CreateVehicleRsp.newBuilder();

        // Eject vehicle members and Kill previous vehicles if there are any
        List<EntityVehicle> previousVehicles =
                player.getScene().getEntities().values().stream()
                        .filter(EntityVehicle.class::isInstance)
                        .map(EntityVehicle.class::cast)
                        .filter(
                                vehicle ->
                                        vehicle.getGadgetId() == vehicleId
                                                && vehicle.getOwner().equals(player))
                        .toList();

        previousVehicles.stream()
                .forEach(
                        vehicle -> {
                            List<VehicleMember> vehicleMembers =
                                    vehicle.getVehicleMembers().stream().toList();

                            vehicleMembers.stream()
                                    .forEach(
                                            vehicleMember -> {
                                                player
                                                        .getScene()
                                                        .broadcastPacket(
                                                                new PacketVehicleInteractRsp(
                                                                        vehicle,
                                                                        vehicleMember,
                                                                        VehicleInteractTypeOuterClass.VehicleInteractType
                                                                                .VehicleInteractType_VEHICLE_INTERACT_OUT));
                                            });

                            player.getScene().killEntity(vehicle, 0);
                        });

        EntityVehicle vehicle =
                new EntityVehicle(player.getScene(), player, vehicleId, pointId, pos, rot);
        player.getScene().addEntity(vehicle);

        proto.setVehicleId(vehicleId);
        proto.setEntityId(vehicle.getId());

        this.setData(proto.build());
    }
}
