package com.flansmod.common.network;

import com.flansmod.common.FlansMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A finite sight operation against the currently held stack, never client-supplied node/NBT data. */
public record GunSightPayload(int operation,int slot,int attachmentRevision,int sightRevision) implements CustomPacketPayload {
    public static final Type<GunSightPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(FlansMod.MODID,"gun_sight"));
    public static final StreamCodec<RegistryFriendlyByteBuf,GunSightPayload> STREAM_CODEC=StreamCodec.of(
            (buf,p)->{buf.writeVarInt(p.operation);buf.writeVarInt(p.slot);buf.writeVarInt(p.attachmentRevision);buf.writeVarInt(p.sightRevision);},
            buf->new GunSightPayload(buf.readVarInt(),buf.readVarInt(),buf.readVarInt(),buf.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() {return TYPE;}
}
