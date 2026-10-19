package com.flansmod.client;

import com.flansmod.common.*;
import com.flansmod.common.types.*;
import com.flansmod.common.network.ArmourMaskPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

/** Helmet mode and the original first-person overlay; no user options are changed. */
@EventBusSubscriber(modid=FlansMod.MODID,value=Dist.CLIENT)
public final class ArmourControls {
    private ArmourControls() { }
    public static final KeyMapping MASK=new KeyMapping("key.flansmod.armour_mask",KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,InputConstants.KEY_K,"key.categories.flansmod");
    @SubscribeEvent public static void register(RegisterKeyMappingsEvent event){event.register(MASK);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        while(MASK.consumeClick())if(mc.player!=null&&mc.screen==null&&MASK.isConflictContextAndModifierActive()){
            var stack=mc.player.getItemBySlot(EquipmentSlot.HEAD);
            if(stack.getItem() instanceof ItemFlanArmour item&&item.armour.hasMask)
                PacketDistributor.sendToServer(new ArmourMaskPayload(item.armour.shortName,ArmourState.switched(stack)));
        }
    }
    public static ResourceLocation overlay(ArmourType type,net.minecraft.world.item.ItemStack stack){
        String name=ArmourState.overlay(type,stack);if(name==null)return null;
        return ResourceLocation.tryParse(name.contains(":")?name.toLowerCase(java.util.Locale.ROOT):
                "flansmod:gui/"+name.toLowerCase(java.util.Locale.ROOT).replaceFirst("\\.png$","")+".png");
    }
    @SubscribeEvent public static void overlay(RenderGuiLayerEvent.Post event){
        if(!event.getName().equals(VanillaGuiLayers.CAMERA_OVERLAYS))return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui||!mc.options.getCameraType().isFirstPerson()||mc.player.isSpectator())return;
        var stack=mc.player.getItemBySlot(EquipmentSlot.HEAD);
        if(!(stack.getItem() instanceof ItemFlanArmour item))return;
        var texture=overlay(item.armour,stack);
        if(texture==null||mc.getResourceManager().getResource(texture).isEmpty())return;
        var graphics=event.getGuiGraphics();int height=graphics.guiHeight(),width=height*4;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        graphics.blit(texture,(graphics.guiWidth()-width)/2,0,0,0,width,height,width,height);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }
}
