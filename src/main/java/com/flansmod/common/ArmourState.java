package com.flansmod.common;

import com.flansmod.common.types.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Per-stack mask mode, retaining the original FMUM NBT key. */
public final class ArmourState {
    private ArmourState() { }
    public static boolean switched(ItemStack stack) {
        var data=stack.get(DataComponents.CUSTOM_DATA);
        return data!=null&&data.copyTag().getBoolean("mask");
    }
    public static boolean toggle(ItemStack stack,String expectedName,boolean expectedState) {
        if(!(stack.getItem() instanceof ItemFlanArmour item)||!item.armour.hasMask
                ||!item.armour.shortName.equals(expectedName)||switched(stack)!=expectedState)return false;
        CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->tag.putBoolean("mask",!expectedState));return true;
    }
    public static float proof(ArmourType type,ItemStack stack,int part) {
        if(part<0||part>3)return 0F;
        int index=part==0&&type.hasMask&&switched(stack)?4:part;
        float level=type.proofLevel[index];int maximum=stack.getMaxDamage();
        float remaining=maximum>0?Math.clamp((maximum-stack.getDamageValue())/(float)maximum,0F,1F):1F;
        return Float.isFinite(level)?Math.max(0F,level)*remaining:0F;
    }
    public static String overlay(ArmourType type,ItemStack stack) {
        String name=switched(stack)?type.overlaySecondary:type.overlay;
        return name==null||name.isBlank()||name.equalsIgnoreCase("None")?null:name;
    }
}
