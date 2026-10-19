package com.flansmod.common.network;

import com.flansmod.common.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A request for the currently equipped helmet, with stale-state rejection. */
public record ArmourMaskPayload(String helmet,boolean expectedState) implements CustomPacketPayload {
    public static final Type<ArmourMaskPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(FlansMod.MODID,"armour_mask"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ArmourMaskPayload> STREAM_CODEC=StreamCodec.of(
            (buf,p)->{buf.writeUtf(p.helmet,128);buf.writeBoolean(p.expectedState);},
            buf->new ArmourMaskPayload(buf.readUtf(128),buf.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public void apply(net.minecraft.world.entity.player.Player player) {
        if(player.level().isClientSide||player.isSpectator())return;
        long now=player.level().getGameTime(),last=player.getPersistentData().getLong("flansmodMaskTick");
        if(player.getPersistentData().contains("flansmodMaskTick")&&now>=last&&now-last<6)return;
        var stack=player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        if(!ArmourState.toggle(stack,helmet,expectedState))return;
        player.getPersistentData().putLong("flansmodMaskTick",now);
        var type=((com.flansmod.common.types.ItemFlanArmour)stack.getItem()).armour;
        FmumSounds.play(player,type.maskSwitchSound[ArmourState.switched(stack)?0:1],type.maskSwitchSoundRange);
    }
}
