package com.flansmod.client;

import com.flansmod.common.ArmourProtection;
import com.flansmod.common.FlansMod;
import com.flansmod.common.types.ItemFlanArmour;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Restores ISpecialArmor's visual armour bar without adding a second damage reduction. */
@EventBusSubscriber(modid=FlansMod.MODID,value=Dist.CLIENT)
public final class LegacyArmourHud {
    private LegacyArmourHud() { }
    private static final ResourceLocation FULL=ResourceLocation.withDefaultNamespace("hud/armor_full");
    private static final ResourceLocation HALF=ResourceLocation.withDefaultNamespace("hud/armor_half");
    private static final ResourceLocation EMPTY=ResourceLocation.withDefaultNamespace("hud/armor_empty");

    @SubscribeEvent(priority=EventPriority.LOW)
    public static void armour(RenderGuiLayerEvent.Pre event) {
        if(!event.getName().equals(VanillaGuiLayers.ARMOR_LEVEL)) return;
        var mc=Minecraft.getInstance();
        if(!(mc.getCameraEntity() instanceof Player player)) return;
        int legacy=0;
        for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET}) {
            var stack=player.getItemBySlot(slot);
            if(stack.getItem() instanceof ItemFlanArmour item && item.getEquipmentSlot()==slot)
                legacy+=ArmourProtection.displayPoints(item.armour.defence);
        }
        if(legacy<=0) return;
        event.setCanceled(true);
        var graphics=event.getGuiGraphics();
        int points=Math.clamp(player.getArmorValue()+legacy,0,20);
        int x=graphics.guiWidth()/2-91,y=graphics.guiHeight()-mc.gui.leftHeight;
        RenderSystem.enableBlend();
        for(int i=0;i<10;i++) graphics.blitSprite(i*2+1<points?FULL:i*2+1==points?HALF:EMPTY,x+i*8,y,9,9);
        RenderSystem.disableBlend();
        mc.gui.leftHeight+=10;
    }
}
