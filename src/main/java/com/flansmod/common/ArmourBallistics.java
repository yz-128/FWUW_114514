package com.flansmod.common;

import com.flansmod.common.types.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.DoubleSupplier;

/** FMUM proof/penetration and durability decisions, independently testable without a world. */
public final class ArmourBallistics {
    private ArmourBallistics() { }
    public record Impact(ItemStack stack,int wear,int soundPart,boolean stopped) { }
    public record Result(float damage,boolean stopped,List<Impact> impacts) { }

    /** Current-pose humanoid hit region; full historical player snapshot rewind is separate. */
    public static int part(Vec3 relative,float bodyYaw,float eyeHeight,float width) {
        if(relative.y>=eyeHeight-.1F)return 0;
        double yaw=Math.toRadians(bodyYaw),side=relative.x*Math.cos(yaw)+relative.z*Math.sin(yaw);
        return relative.y>eyeHeight*.4 && Math.abs(side)>width/3D ? side>0?2:3 : 1;
    }
    public static boolean blocks(float proof,float minimum,float maximum,double roll) {
        return proof>0F&&(proof>=maximum||(proof>minimum&&roll<(proof-minimum)/(maximum-minimum)));
    }
    public static int wear(float value,double roll) {
        if(!Float.isFinite(value)||value<=0F)return 0;
        int whole=(int)Math.min(Integer.MAX_VALUE-1D,Math.floor(value));
        return whole+(roll<value-whole?1:0);
    }
    public static Result resolve(List<ItemStack> worn,BulletType bullet,int part,float damage,boolean player,DoubleSupplier random) {
        if(!Float.isFinite(damage)||damage<=0)return new Result(0,false,List.of());
        if(bullet==null)return new Result(damage,false,List.of());
        var impacts=new ArrayList<Impact>();float remaining=damage;
        for(var stack:worn)if(stack.getItem() instanceof ItemFlanArmour item) {
            var armour=item.armour;float proof=ArmourState.proof(armour,stack,part);if(proof<=0)continue;
            boolean stopped=blocks(proof,bullet.minPenetrationLevel,bullet.maxPenetrationLevel,random.getAsDouble());
            float multiplier=stopped?armour.bluntDurabilityMult:armour.penetratedDurabilityMult;
            int wear=armour.breakable?wear(bullet.armorBreakPower*multiplier,random.getAsDouble()):0;
            impacts.add(new Impact(stack,wear,stopped?part:part+4,stopped));
            if(stopped) {
                // The actual EFT player core uses one point; mobs use the authored blunt multipliers.
                float blunt=player?Math.min(remaining,1F):remaining*armour.bluntDamageMult*bullet.bluntDamageMult;
                return new Result(Math.max(0,blunt),true,List.copyOf(impacts));
            }
            remaining*=armour.penetratedDamageMult;
        }
        return new Result(Math.max(0,remaining),false,List.copyOf(impacts));
    }
    /** Exhausted plates remain worn at zero condition, as the original EFT core did. */
    public static void applyWear(Impact impact) {
        var stack=impact.stack();if(stack.getMaxDamage()<=0||impact.wear()<=0)return;
        stack.setDamageValue((int)Math.min(stack.getMaxDamage(),(long)stack.getDamageValue()+impact.wear()));
    }
}
