package com.flansmod.common.network;

import com.flansmod.common.FlansMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Menu-bound edit; bounded integer paths address nested slots without accepting client NBT. */
public record GunModificationPayload(int containerId,int revision,int operation,int[] path,int slot,int inventorySlot,int step,int offset) implements CustomPacketPayload {
    public static final int OPEN=0,INSTALL=1,REMOVE=2,MOVE=3;
    public static final Type<GunModificationPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(FlansMod.MODID,"gun_modification"));
    public static final StreamCodec<RegistryFriendlyByteBuf,GunModificationPayload> STREAM_CODEC=StreamCodec.of(
            (buf,p)->{buf.writeVarInt(p.containerId);buf.writeVarInt(p.revision);buf.writeVarInt(p.operation);buf.writeVarIntArray(p.path);
                buf.writeVarInt(p.slot);buf.writeVarInt(p.inventorySlot);buf.writeVarInt(p.step);buf.writeVarInt(p.offset);},
            buf->new GunModificationPayload(buf.readVarInt(),buf.readVarInt(),buf.readVarInt(),buf.readVarIntArray(16),
                    buf.readVarInt(),buf.readVarInt(),buf.readVarInt(),buf.readVarInt()));
    public static GunModificationPayload open() {return new GunModificationPayload(-1,0,OPEN,new int[0],0,0,0,0);}
    @Override public Type<? extends CustomPacketPayload> type() {return TYPE;}
}
