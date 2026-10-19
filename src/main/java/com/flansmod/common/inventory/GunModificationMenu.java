package com.flansmod.common.inventory;

import com.flansmod.common.*;
import com.flansmod.common.network.GunModificationPayload;
import com.flansmod.common.types.GunType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Read-only inventory mirror. Only validated modification requests can move parts. */
public final class GunModificationMenu extends AbstractContainerMenu {
    private static final List<String> STATUS=List.of("saved","invalid","stale","incompatible","position","full","limit","grip","inventory_full","busy");
    private final Inventory inventory;
    private final int gunSlot;
    private final ItemStack openedStack;
    private final DataSlot revision=DataSlot.standalone(), status=DataSlot.standalone(), response=DataSlot.standalone();
    public GunModificationMenu(int id,Inventory inventory,int gunSlot) {
        super(GunModificationMenus.TYPE.get(),id);
        this.inventory=inventory;this.gunSlot=gunSlot>=0 && gunSlot<9?gunSlot:0;
        this.openedStack=inventory.getItem(this.gunSlot);
        for(int i=0;i<36;i++) addSlot(new Slot(inventory,i,-10000,-10000) {
            @Override public boolean mayPlace(ItemStack stack) {return false;}
            @Override public boolean mayPickup(Player player) {return false;}
        });
        addDataSlot(revision);addDataSlot(status);addDataSlot(response);revision.set(GunAttachments.revision(gunStack()));
    }
    public static void open(ServerPlayer player) {
        var gun=GunActions.firearm(player.getMainHandItem());
        if(gun==null || player.isSpectator() || !player.isAlive() || player.containerMenu!=player.inventoryMenu) return;
        var data=PlayerHandler.getPlayerData(player);
        if(data.isReloading() || data.gunReload.active() || data.compressionHeld || data.shootTimeRight>0F) {
            player.displayClientMessage(Component.translatable("gunsmith.flansmod.status.busy"),true);return;
        }
        data.isShootingRight=data.pendingGunTrigger=data.pendingGunReload=false;data.burstRoundsRemainingRight=0;data.gunAimDown=false;
        int slot=player.getInventory().selected;
        player.openMenu(new SimpleMenuProvider((id,inventory,p)->new GunModificationMenu(id,inventory,slot),
                Component.translatable("gunsmith.flansmod.title")),buf->buf.writeVarInt(slot));
    }
    public ItemStack gunStack() {return inventory.getItem(gunSlot);}
    public GunType gunType() {return GunActions.firearm(gunStack());}
    public int gunSlot() {return gunSlot;}
    public int revision() {return revision.get();}
    public int status() {return status.get();}
    public int response() {return response.get();}
    public Component statusMessage() {return Component.translatable("gunsmith.flansmod.status."+STATUS.get(Math.clamp(status.get(),0,STATUS.size()-1)));}
    public void modify(ServerPlayer player,GunModificationPayload request) {
        if(!stillValid(player) || player.containerMenu!=this || request.containerId()!=containerId) return;
        var data=PlayerHandler.getPlayerData(player);
        String reason=data.isReloading() || data.gunReload.active() || data.compressionHeld || data.shootTimeRight>0F
                ?"busy":GunAttachmentTransaction.apply(inventory,gunSlot,request.revision(),request.operation(),request.path(),
                request.slot(),request.inventorySlot(),request.step(),request.offset(),player.registryAccess());
        status.set(Math.max(0,STATUS.indexOf(reason==null?"saved":reason)));
        response.set((response.get()+1)&0x7fff);
        if(reason==null) {
            data.actionGun=null;data.gunWallRay=null;data.isShootingRight=data.pendingGunTrigger=false;
        }
        broadcastChanges();
    }
    @Override public void broadcastChanges() {revision.set(GunAttachments.revision(gunStack()));super.broadcastChanges();}
    @Override public boolean stillValid(Player player) {
        return player.isAlive() && !player.isSpectator() && inventory.selected==gunSlot && gunType()!=null
                && (player.level().isClientSide || gunStack()==openedStack);
    }
    @Override public ItemStack quickMoveStack(Player player,int index) {return ItemStack.EMPTY;}
    @Override public void clicked(int slot,int button,ClickType type,Player player) { }
}
