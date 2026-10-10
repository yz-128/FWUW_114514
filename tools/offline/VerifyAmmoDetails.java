import com.flansmod.client.AmmoDetails;
import com.flansmod.client.GunDetails;
import com.flansmod.common.TextDecoding;
import com.flansmod.common.types.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.core.component.DataComponents;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Verifies all EFT ammo definitions against their original lines and the real tooltip entry. */
public final class VerifyAmmoDetails {
    static void check(boolean value,String text){if(!value)throw new AssertionError(text);}
    static void close(float a,float b,String text){check(Math.abs(a-b)<.00001F,text+": "+a+" vs "+b);}
    static TranslatableContents row(List<Component> lines,String key){return lines.stream().map(Component::getContents).filter(c->c instanceof TranslatableContents t && t.getKey().equals("stats.flansmod.ammo."+key)).map(c->(TranslatableContents)c).findFirst().orElseThrow();}
    static Map<String,String[]> raw(Path file)throws Exception {
        var values=new HashMap<String,String[]>();
        for(String line:TextDecoding.readLines(file)) {
            String text=line.trim();if(text.isEmpty() || text.startsWith("//"))continue;
            String[] tokens=text.split("\\s+");values.put(tokens[0],Arrays.copyOfRange(tokens,1,tokens.length));
        }
        return values;
    }
    public static void main(String[] args)throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        ((net.minecraft.core.MappedRegistry<?>)net.minecraft.core.registries.BuiltInRegistries.ITEM).unfreeze();
        var root=Path.of(args[0]);var pack=new OfflinePack(root);pack.readTypes();
        var files=new HashMap<String,Path>();
        try(var paths=Files.list(root.resolve("bullets"))) {
            for(Path path:paths.filter(Files::isRegularFile).toList()) {
                var values=raw(path);if(values.containsKey("ShortName"))files.put(values.get("ShortName")[0],path);
            }
        }
        var langs=new ArrayList<JsonObject>();
        for(String name:List.of("zh_cn","en_us"))langs.add(JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/flansmod/lang/"+name+".json"))).getAsJsonObject());
        int all=0,live=0,cases=0,fractional=0;var families=new HashSet<String>();
        var table=new StringBuilder("# EFT 弹药数值清单\n\n全部采用内容包定义；伤害为每颗弹丸的基础值。穿甲参数的显示不代表复杂护甲算法已完成移植。\n\n|弹药|口径组|生物 / 玩家伤害|弹丸数|穿甲等级|破甲强度|腰射 / ADS 倍率|\n|---|---|---:|---:|---:|---:|---:|\n");
        for(var type:pack.types)if(type instanceof BulletType bullet) {
            all++;var values=raw(files.get(bullet.shortName));
            if(values.containsKey("DamageVsLiving")) {
                float expected=Float.parseFloat(values.get("DamageVsLiving")[0]);
                close(bullet.damageVsLiving,expected,"exact raw living damage "+bullet.shortName);
                if(expected!=(int)expected)fractional++;
            }
            if(values.containsKey("DamageVsPlayer"))close(bullet.damageVsPlayer,Float.parseFloat(values.get("DamageVsPlayer")[0]),"raw player damage "+bullet.shortName);
            else close(bullet.damageVsPlayer,bullet.damageVsLiving,"player damage fallback "+bullet.shortName);
            if(values.containsKey("PenetrationLevel")) {
                float a=Float.parseFloat(values.get("PenetrationLevel")[0]);
                float b=values.get("PenetrationLevel").length>1?Float.parseFloat(values.get("PenetrationLevel")[1]):a;
                close(bullet.minPenetrationLevel,Math.min(a,b),"raw penetration minimum "+bullet.shortName);
                close(bullet.maxPenetrationLevel,Math.max(a,b),"raw penetration maximum "+bullet.shortName);
            }
            if(values.containsKey("ArmorBreakPower"))close(bullet.armorBreakPower,Float.parseFloat(values.get("ArmorBreakPower")[0]),"raw armor break "+bullet.shortName);
            var collapsed=new ArrayList<Component>();AmmoDetails.append(collapsed,bullet,false);
            var expanded=new ArrayList<Component>();AmmoDetails.append(expanded,bullet,true);
            if(bullet.isCase) {
                cases++;row(collapsed,"case");
                check(collapsed.stream().noneMatch(c->c.getContents() instanceof TranslatableContents t && t.getKey().equals("stats.flansmod.ammo.damage")),"casing has no fabricated projectile stats");
            } else {
                live++;families.add(bullet.ammoType);
                check(expanded.size()>collapsed.size(),"Shift expands actual ammo "+bullet.shortName);
                var damage=row(collapsed,"damage").getArgs();
                check(damage[0].equals(GunDetails.number(bullet.damageVsLiving)) && damage[1].equals(bullet.numBullets),"collapsed damage/pellet values "+bullet.shortName);
                if(bullet.maxPenetrationLevel>0 || bullet.minPenetrationLevel>0)row(collapsed,"penetration");
                if(bullet.armorBreakPower>0)row(collapsed,"armor");
                var spread=row(expanded,"spread").getArgs();
                check(spread[0].equals(GunDetails.number(bullet.bulletSpreadMultiplier)) && spread[1].equals(GunDetails.number(bullet.aimingBulletSpreadMultiplier)),"hip/ADS multiplier ordering "+bullet.shortName);
                table.append(String.format(Locale.ROOT,"|%s|%s|%s / %s|%d|%s～%s|%s|%s / %s|%n",bullet.shortName,bullet.ammoType,GunDetails.number(bullet.damageVsLiving),GunDetails.number(bullet.damageVsPlayer),bullet.numBullets,GunDetails.number(bullet.minPenetrationLevel),GunDetails.number(bullet.maxPenetrationLevel),GunDetails.number(bullet.armorBreakPower),GunDetails.number(bullet.bulletSpreadMultiplier),GunDetails.number(bullet.aimingBulletSpreadMultiplier)));
            }
            for(Component line:expanded)if(line.getContents() instanceof TranslatableContents t)
                for(var lang:langs)check(lang.has(t.getKey()),"localized tooltip "+t.getKey());
        }
        check(all==75 && live==73 && cases==2 && families.size()==14,"all EFT ammo/casings/calibers covered");
        check(fractional>0,"fractional damage regression exercised");
        var buck=(BulletType)pack.types.stream().filter(t->"12G7mm8.5Magnum".equals(t.shortName)).findFirst().orElseThrow();
        close(buck.damageVsLiving,1.75F,"buckshot damage never truncates to 1");
        check(buck.numBullets==9,"buckshot is 1.75 per projectile times 9");
        Item item=VerifyMagazineData.register(buck,"ammo_tooltip_fixture");var stack=new ItemStack(item,7);
        var tag=new net.minecraft.nbt.CompoundTag();tag.putString("fixture","unchanged");stack.set(DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));
        var before=stack.copy();var event=new net.neoforged.neoforge.event.entity.player.ItemTooltipEvent(stack,null,new ArrayList<>(),TooltipFlag.NORMAL,Item.TooltipContext.EMPTY);
        // Exercise the production dispatcher with the real event's data; this JVM has no window.
        GunDetails.append(event.getToolTip(),InfoType.getType(event.getItemStack()),event.getItemStack(),false);row(event.getToolTip(),"damage");
        check(ItemStack.matches(before,stack),"real ItemTooltipEvent is read-only");
        consumerBytecode();
        Files.writeString(Path.of("reports/eft/弹药数值清单.md"),table.toString());
        System.out.printf("PASS: %d definitions (%d live, %d casings), %d calibers; %d fractional damage definitions; raw fields match, immediate armor/penetration/damage, Shift details, localization, native item tooltip routing and unchanged ItemStack, compiled damage consumer retains float.%n",all,live,cases,families.size(),fractional);
    }
    static void consumerBytecode()throws Exception {
        var entity=new ClassNode();new ClassReader(Files.readAllBytes(Path.of("build/classes/java/main/com/flansmod/common/entity/EntityBullet.class"))).accept(entity,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        int found=0;
        for(var method:entity.methods)for(var instruction:method.instructions)
            if(instruction instanceof FieldInsnNode field && field.name.equals("damageVsLiving")) {check(field.desc.equals("F"),"actual hit-damage consumer uses float");found++;}
        check(found>0,"real hit-damage consumer covered");
    }
}
