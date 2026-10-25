package com.flansmod.common.network;

import com.flansmod.common.FlansMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** A server-confirmed surface hit; never used to request damage or block changes. */
public record BlockImpactPayload(BlockPos block,Vec3 point,Direction face,int state,long seed) implements CustomPacketPayload {
    public static final Type<BlockImpactPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(FlansMod.MODID,"block_impact"));
    public static final StreamCodec<RegistryFriendlyByteBuf,BlockImpactPayload> STREAM_CODEC=StreamCodec.of(
            (b,p)->{b.writeBlockPos(p.block);b.writeDouble(p.point.x);b.writeDouble(p.point.y);b.writeDouble(p.point.z);b.writeEnum(p.face);b.writeVarInt(p.state);b.writeLong(p.seed);},
            b->new BlockImpactPayload(b.readBlockPos(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readEnum(Direction.class),b.readVarInt(),b.readLong()));
    public boolean valid() {
        return state>=0 && Double.isFinite(point.x)&&Double.isFinite(point.y)&&Double.isFinite(point.z)
                && point.x>=block.getX()-.001&&point.x<=block.getX()+1.001
                && point.y>=block.getY()-.001&&point.y<=block.getY()+1.001
                && point.z>=block.getZ()-.001&&point.z<=block.getZ()+1.001;
    }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
