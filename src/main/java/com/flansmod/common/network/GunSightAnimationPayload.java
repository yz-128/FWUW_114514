package com.flansmod.common.network;

import com.flansmod.common.FlansMod;
import com.flansmod.common.GunAttachments;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Server-confirmed visual transition. No packet from the client can start a fold animation. */
public record GunSightAnimationPayload(int entity,int slot,String gun,int structureRevision,int sightRevision,int fromGroup,long sequence,List<Part> parts)
        implements CustomPacketPayload {
    public record Part(int[] path,String from,String to) {
        public Part {path=path.clone();}
    }
    public GunSightAnimationPayload {parts=List.copyOf(parts);}
    public static final Type<GunSightAnimationPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(FlansMod.MODID,"gun_sight_animation"));
    public static final StreamCodec<RegistryFriendlyByteBuf,GunSightAnimationPayload> STREAM_CODEC=StreamCodec.of((buf,p)->{
        buf.writeVarInt(p.entity);buf.writeVarInt(p.slot);buf.writeUtf(p.gun,128);
        buf.writeVarInt(p.structureRevision);buf.writeVarInt(p.sightRevision);buf.writeVarInt(p.fromGroup);buf.writeVarLong(p.sequence);buf.writeVarInt(p.parts.size());
        for(var part:p.parts) {
            buf.writeVarInt(part.path.length);for(int element:part.path)buf.writeVarInt(element);
            buf.writeUtf(part.from,128);buf.writeUtf(part.to,128);
        }
    },buf->{
        int entity=buf.readVarInt(),slot=buf.readVarInt();String gun=buf.readUtf(128);
        int structure=buf.readVarInt(),sight=buf.readVarInt(),group=buf.readVarInt();long sequence=buf.readVarLong();int count=buf.readVarInt();
        if(count<1 || count>GunAttachments.MAX_NODES)throw new io.netty.handler.codec.DecoderException("Invalid fold part count");
        var parts=new ArrayList<Part>();
        for(int i=0;i<count;i++) {
            int length=buf.readVarInt();
            if(length<2 || length>GunAttachments.MAX_DEPTH*2 || (length&1)!=0)
                throw new io.netty.handler.codec.DecoderException("Invalid fold path length");
            int[] path=new int[length];for(int j=0;j<length;j++)path[j]=buf.readVarInt();
            if(!GunAttachments.validPath(path))throw new io.netty.handler.codec.DecoderException("Invalid fold path");
            parts.add(new Part(path,buf.readUtf(128),buf.readUtf(128)));
        }
        return new GunSightAnimationPayload(entity,slot,gun,structure,sight,group,sequence,parts);
    });
    @Override public Type<? extends CustomPacketPayload> type() {return TYPE;}
}
