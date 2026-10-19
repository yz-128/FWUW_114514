package com.flansmod.common.inventory;

import com.flansmod.common.FlansMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class GunModificationMenus {
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,FlansMod.MODID);
    public static final DeferredHolder<MenuType<?>,MenuType<GunModificationMenu>> TYPE=MENUS.register("gun_modification",
            ()->IMenuTypeExtension.create((id,inventory,data)->new GunModificationMenu(id,inventory,data.readVarInt())));
    public static void register(IEventBus bus) {MENUS.register(bus);}
    private GunModificationMenus() { }
}
