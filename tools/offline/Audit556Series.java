import com.flansmod.common.*;
import com.flansmod.common.types.*;
import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.google.gson.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.player.Inventory;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Read-only audit of installed 5.56 families, using final-JAR readers and actual ItemStack operations. */
public final class Audit556Series {
    static final List<OfflinePack> packs=new ArrayList<>();
    static final Map<InfoType,Path> sources=new IdentityHashMap<>();
    static final Map<InfoType,Map<String,List<String>>> raw=new IdentityHashMap<>();
    static final Map<InfoType,List<String>> declared=new IdentityHashMap<>();
    static final Map<String,InfoType> winners=new LinkedHashMap<>();
    static final List<String> issues=new ArrayList<>(),assets=new ArrayList<>(),loads=new ArrayList<>(),magRows=new ArrayList<>(),compatibility=new ArrayList<>(),duplicates=new ArrayList<>();
    static final List<String> metadata=new ArrayList<>(),followers=new ArrayList<>(),feeding=new ArrayList<>(),broader=new ArrayList<>();
    static final Map<GunType,ItemStack> filled=new IdentityHashMap<>();
    static int compressions,shots,roundTrips,feedCycles,feedRounds;
    static String owner(InfoType t){return t.contentPack+"/"+t.shortName;}
    static boolean family(String s){return s!=null && s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "").contains("556x45");}
    static boolean named556(String s){return family(s)||(s!=null&&s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "").contains("556mm"));}
    static boolean has(ResourceLocation id){return id!=null && packs.stream().anyMatch(p->p.has(id));}
    static boolean matches(BulletType b,String name){return name.equalsIgnoreCase(b.shortName)||name.equalsIgnoreCase(b.ammoType);}
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static List<String> field(InfoType t,String key){return raw.get(t).getOrDefault(key,List.of());}
    static boolean registered(InfoType t){return winners.get(ItemAssets.registryPathOf(t))==t;}
    static void write(Path out,String name,List<String> rows)throws Exception{Files.write(out.resolve(name),rows,StandardCharsets.UTF_8);}
    static void relatedNodes(List<ModifiableType.DefaultAttachment> nodes,Set<InfoType> related,int depth){
        if(depth>8)return;
        for(var node:nodes){var at=AttachmentType.getAttachment(node.name);if(at!=null)related.add(at);relatedNodes(node.children,related,depth+1);}
    }
    static void load(Path root)throws Exception{
        try(var dirs=Files.newDirectoryStream(root)){for(var dir:dirs)if(Files.isDirectory(dir)&&!dir.getFileName().toString().startsWith("_")&&!dir.getFileName().toString().startsWith(".")){
            var pack=new OfflinePack(dir);packs.add(pack);FlansMod.contentPacks.put(dir.getFileName().toString(),dir.toAbsolutePath());
        }}
        // Match the live loader's EnumType, pack, and indexed definition ordering.
        for(var kind:EnumType.values())for(var pack:packs){
            var files=pack.files.entrySet().stream().filter(e->e.getKey().startsWith(kind.folderName+"/")&&!e.getKey().substring(kind.folderName.length()+1).contains("/")&&e.getKey().matches(".*\\.(txt|df)"))
                .sorted(Map.Entry.comparingByKey()).toList();
            for(var entry:files){var path=entry.getValue();var n=path.getFileName().toString();var tf=new TypeFile(kind,n.substring(0,n.lastIndexOf('.')),false);
                tf.contentPack=pack.root.getFileName().toString();tf.lines.addAll(TextDecoding.readLines(path));
                var type=kind.getTypeClass().getConstructor(TypeFile.class).newInstance(tf);type.read(tf);sources.put(type,path);pack.types.add(type);
                var fields=new LinkedHashMap<String,List<String>>();
                var tokens=new ArrayList<String>();for(var line:tf.lines){var split=line.trim().split("\\s+");if(split.length<2||split[0].startsWith("//"))continue;
                    fields.put(split[0],List.of(Arrays.copyOfRange(split,1,split.length)));
                    if(split[0].equals("Ammo"))for(int i=1;i<split.length;i++)if(!split[i].equalsIgnoreCase("next"))tokens.add(split[i]);}
                raw.put(type,fields);
                declared.put(type,List.copyOf(tokens));
                var first=winners.putIfAbsent(ItemAssets.registryPathOf(type),type);
                if(first!=null && (family(type.shortName)||type.shortName.equals("MAG5_60Rounds")))duplicates.add(owner(first)+"\t"+owner(type)+"\t"+first.modelString+"\t"+type.modelString);
            }
        }
        for(var type:InfoType.infoTypes)if(type instanceof ModifiableType m)m.resolveDefaultAttachments();
        ((net.minecraft.core.MappedRegistry<?>)BuiltInRegistries.ITEM).unfreeze();
        for(var type:winners.values())if(type instanceof GunType || type instanceof BulletType)
            Registry.register(BuiltInRegistries.ITEM,ResourceLocation.fromNamespaceAndPath("flansmod",ItemAssets.registryPathOf(type)),new ItemFlan(type,new Item.Properties().stacksTo(type instanceof BulletType b?Math.max(1,Math.min(99,b.maxStackSize)):1)));
        for(var type:InfoType.infoTypes)if(type instanceof GunType g){
            for(String name:g.ammoNames){var bullet=BulletType.getBullet(name);if(bullet!=null&&!g.ammo.contains(bullet))g.ammo.add(bullet);}
            if(g.itsType!=GunType.GunItsType.MAG)for(var t:InfoType.infoTypes)if(t instanceof GunType mag&&GunMagazine.acceptsMagazine(g,mag))
                for(var b:BulletType.bullets)if(mag.ammoNames.stream().anyMatch(n->matches(b,n))&&!g.ammo.contains(b))g.ammo.add(b);
        }
    }
    static void assets(InfoType t){
        String icon=t.iconPath==null?t.shortName:t.iconPath;
        var iconId=ResourceLocation.fromNamespaceAndPath("flansmod",("textures/"+(icon.contains("/")?icon:"items/"+icon)+".png").toLowerCase(Locale.ROOT));
        boolean iconOk=has(iconId)||Audit556Series.class.getResource("/assets/"+iconId.getNamespace()+"/"+iconId.getPath())!=null;
        if(!iconOk)issues.add("MISSING_ICON\t"+owner(t)+"\t"+iconId);
        String reference=t instanceof BulletType b?b.bulletModel:t.modelString;
        var rig=reference==null?null:ModelCache.rigForReference(reference,t instanceof BulletType b?b.bulletScale:t.modelScale);
        String texture=t instanceof BulletType b?b.bulletTexture:t.texture;
        var skin=Textures.of(texture,false,Audit556Series::has);boolean modelOk=rig!=null&&!rig.quads.isEmpty(),skinOk=has(skin);
        if(reference!=null&&!modelOk)issues.add("MISSING_MODEL\t"+owner(t)+"\t"+reference);
        if(reference!=null&&!skinOk)issues.add("MISSING_SKIN\t"+owner(t)+"\t"+skin);
        int seats=0;if(t instanceof GunType mag&&mag.itsType==GunType.GunItsType.MAG&&rig!=null)seats=FmumBulletPose.seats(rig.animation,mag.ammoCapacity).size();
        assets.add(owner(t)+"\t"+registered(t)+"\t"+iconOk+"\t"+modelOk+"\t"+skinOk+"\t"+seats+"\t"+reference+"\t"+skin);
        var bulletModel=field(t,"BulletModel");
        if(t instanceof BulletType bullet && !bulletModel.isEmpty()) {
            String referenceRaw=bulletModel.getFirst(),textureRaw=bulletModel.size()>1?bulletModel.get(1):null;
            float scaleRaw=bulletModel.size()>2?Float.parseFloat(bulletModel.get(2)):1F;
            var expected=ModelCache.rigForReference(referenceRaw,scaleRaw);
            boolean rawModelOk=expected!=null&&!expected.quads.isEmpty(),rawSkinOk=has(Textures.of(textureRaw,false,Audit556Series::has));
            boolean kept=referenceRaw.equals(bullet.bulletModel);
            metadata.add(owner(t)+"\tBulletModel\t"+referenceRaw+"\t"+bullet.bulletModel+"\t"+rawModelOk+"\t"+rawSkinOk);
            if(!kept)issues.add("DECLARED_MODEL_NOT_PARSED\t"+owner(t)+"\t"+referenceRaw+"; original mesh="+rawModelOk+", original skin="+rawSkinOk);
        }
        if(!field(t,"RecipeOutput").isEmpty()) {
            int wanted=Integer.parseInt(field(t,"RecipeOutput").getFirst());
            metadata.add(owner(t)+"\tRecipeOutput\t"+wanted+"\t"+t.recipeOutput+"\tn/a\tn/a");
            if(wanted!=t.recipeOutput)issues.add("RECIPE_OUTPUT_NOT_PARSED\t"+owner(t)+"\t"+wanted+" -> "+t.recipeOutput);
        }
        if(!field(t,"Icon").isEmpty()&&!Objects.equals(field(t,"Icon").getFirst(),t.iconPath))
            metadata.add(owner(t)+"\tIcon\t"+field(t,"Icon").getFirst()+"\t"+t.iconPath+"\tpath fallback found="+iconOk+"\tn/a");
        if(t instanceof GunType mag && mag.itsType==GunType.GunItsType.MAG && !field(t,"MagFollowerModel").isEmpty()) {
            var row=field(t,"MagFollowerModel");float scale=row.size()>2?Float.parseFloat(row.get(2)):1F;
            var follower=ModelCache.rigForReference(row.getFirst(),scale);
            var vectors=rig==null?Map.<String,float[][]>of():rig.animation.fmumVectorArrays;
            int positions=vectors.getOrDefault("magFollowerAttachPos",new float[0][3]).length;
            int rotations=vectors.getOrDefault("magFollowerAttachRot",new float[0][3]).length;
            boolean mesh=follower!=null&&!follower.quads.isEmpty();
            // The reference core treats None as the magazine skin, not a texture named None.
            var followerSkin=Textures.of(row.size()>1&&!row.get(1).equals("None")?row.get(1):mag.texture,false,Audit556Series::has);
            followers.add(owner(t)+"\t"+row.getFirst()+"\t"+scale+"\t"+mag.magFollowerTexture+"\t"+mesh+"\t"+has(followerSkin)+"\t"+positions+"\t"+rotations);
        }
    }
    static void magazine(GunType mag,List<BulletType> rounds)throws Exception{
        var wanted=declared.getOrDefault(mag,List.of());
        var missing=wanted.stream().filter(n->mag.ammoNames.stream().noneMatch(n::equalsIgnoreCase)).toList();
        if(!missing.isEmpty())issues.add("AMMO_TOKENS_DROPPED\t"+owner(mag)+"\t"+missing);
        magRows.add(owner(mag)+"\t"+(mag.item!=null)+"\t"+mag.ammoCapacity+"\t"+mag.magazineNames+"\t"+wanted+"\t"+mag.ammoNames+"\t"+missing);
        if(mag.item==null)return;
        for(var bullet:rounds) {
            boolean requested=wanted.stream().anyMatch(n->matches(bullet,n));
            if(!requested && !family(bullet.shortName)&&!family(bullet.ammoType))continue;
            int supplied=Math.min(bullet.item.getDefaultMaxStackSize(),mag.ammoCapacity+2);
            var inventory=new Inventory(null);inventory.setItem(0,new ItemStack(bullet.item,supplied));
            var stack=new ItemStack(mag.item);int loaded=0;
            while(loaded<mag.ammoCapacity && GunMagazine.loadOneBullet(stack,mag,inventory)){loaded++;compressions++;}
            boolean accepts=loaded>0;
            check(loaded+inventory.getItem(0).getCount()==supplied,"compression conservation "+owner(mag));
            if(accepts)check(loaded==Math.min(supplied,mag.ammoCapacity),"declared capacity "+owner(mag));
            if(requested&&!accepts)issues.add("DECLARED_AMMO_REJECTED\t"+owner(mag)+"\t"+owner(bullet));
            if(accepts&&!requested)issues.add("UNDECLARED_AMMO_ACCEPTED\t"+owner(mag)+"\t"+owner(bullet));
            loads.add(owner(mag)+"\t"+owner(bullet)+"\t"+bullet.isCase+"\t"+requested+"\t"+accepts+"\t"+loaded+"\t"+mag.ammoCapacity);
            if(loaded>0){
                if(!bullet.isCase)filled.putIfAbsent(mag,stack.copy());
                var saved=ItemStack.parseOptional(net.minecraft.core.RegistryAccess.EMPTY,(net.minecraft.nbt.CompoundTag)stack.save(net.minecraft.core.RegistryAccess.EMPTY));
                if(GunMagazine.roundsRemaining(saved)!=loaded)throw new AssertionError("saved count "+owner(mag));roundTrips++;
                while(GunMagazine.consumeRound(saved))shots++;
                if(GunMagazine.roundsRemaining(saved)!=0)throw new AssertionError("empty count "+owner(mag));
                check(!GunMagazine.consumeRound(saved),"empty magazine cannot produce rounds "+owner(mag));
            }
        }
    }
    static void feed(GunType gun,GunType mag) {
        if(gun.item==null||mag.item==null||!GunMagazine.acceptsMagazine(gun,mag)||!filled.containsKey(mag))return;
        if(!GunAttachments.requirements(gun,new ItemStack(gun.item),0)||!GunAttachments.requirements(gun,new ItemStack(gun.item),1)) {
            feeding.add(owner(gun)+"\t"+owner(mag)+"\tSKIPPED: incomplete base assembly\t0\t0");return;
        }
        var stock=filled.get(mag).copy();int before=GunMagazine.roundsRemaining(stock);
        var inventory=new Inventory(null);inventory.setItem(0,stock);
        var stack=new ItemStack(gun.item);var returned=new ArrayList<ItemStack>();
        check(GunReloading.install(stack,gun,inventory,0,stock.copy(),returned::add),"install "+owner(gun));
        check(inventory.getItem(0).isEmpty()&&returned.isEmpty(),"one actual magazine consumed "+owner(gun));
        check(GunMagazine.getChamberedRound(stack).isEmpty(),"advanced install does not silently charge");
        GunReloading.charge(stack,gun,returned::add);
        int fed=0;while(!GunMagazine.spendChamberedRound(stack,gun).isEmpty()) {
            if(++fed>before)throw new AssertionError("manufactured chamber round "+owner(gun));
        }
        check(fed==before,"complete feed conservation "+owner(gun)+" "+fed+"/"+before);
        check(GunMagazine.magazineType(stack,gun)==mag,"empty installed magazine identity");
        check(GunMagazine.roundsRemaining(GunMagazine.get(stack,gun,0))==0&&GunMagazine.getChamberedRound(stack).isEmpty(),"empty magazine and chamber");
        feedCycles++;feedRounds+=fed;feeding.add(owner(gun)+"\t"+owner(mag)+"\tPASS\t"+before+"\t"+fed);
    }
    public static void main(String[] args)throws Exception{
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();load(Path.of(args[0]));Path out=Path.of(args[1]);Files.createDirectories(out);
        var mags=InfoType.infoTypes.stream().filter(t->t instanceof GunType).map(t->(GunType)t)
            .filter(g->g.itsType==GunType.GunItsType.MAG&&(g.magazineNames.stream().anyMatch(n->n.toLowerCase(Locale.ROOT).contains("556"))||declared.getOrDefault(g,List.of()).stream().anyMatch(Audit556Series::family))).toList();
        var rounds=BulletType.bullets.stream().filter(b->b.item!=null&&(family(b.shortName)||family(b.ammoType))).toList();
        var guns=InfoType.infoTypes.stream().filter(t->t instanceof GunType).map(t->(GunType)t).filter(g->g.itsType!=GunType.GunItsType.MAG&&g.magazineNames.stream().anyMatch(n->n.toLowerCase(Locale.ROOT).contains("556"))).toList();
        assets.add("Item\tRegistered\tIcon\tModel\tSkin\tVisibleSeatedRounds\tModelReference\tSkinResource");
        loads.add("Magazine\tRound\tIsCase\tDeclared\tActuallyAccepted\tLoaded\tCapacity");
        magRows.add("Magazine\tRegistered\tCapacity\tMagTypes\tDeclaredAmmo\tParsedAmmo\tMissingTokens");
        compatibility.add("Gun\tMagazine\tCompatible");duplicates.add(0,"First\tSkipped\tFirstModel\tSkippedModel");
        metadata.add("Item\tField\tDeclared\tParsed\tRawModelFound\tRawSkinFound");
        followers.add("Magazine\tDeclaredModel\tDeclaredScale\tParsedTexture\tModelFound\tReferenceSkinFound\tFollowerPositions\tFollowerRotations");
        feeding.add("Gun\tMagazine\tStatus\tRoundsBefore\tRoundsAfterFeeding");
        broader.add("Item\tCategory\tDetails");
        var related=Collections.newSetFromMap(new IdentityHashMap<InfoType,Boolean>());related.addAll(mags);related.addAll(rounds);related.addAll(guns);
        for(var t:InfoType.infoTypes)if(named556(t.shortName)||named556(t.name))related.add(t);
        for(var g:guns){relatedNodes(g.defaultAttachments.stream().flatMap(List::stream).toList(),related,0);for(var m:mags)compatibility.add(owner(g)+"\t"+owner(m)+"\t"+GunMagazine.acceptsMagazine(g,m));}
        for(var mag:mags)magazine(mag,rounds);
        related.stream().sorted(Comparator.comparing(Audit556Series::owner)).forEach(Audit556Series::assets);
        for(var g:guns)for(var m:mags)feed(g,m);
        for(var t:InfoType.infoTypes) {
            if(t instanceof GunType g) {
                var lost=declared.get(t).stream().filter(n->g.ammoNames.stream().noneMatch(n::equalsIgnoreCase)).toList();
                if(!lost.isEmpty())broader.add(owner(t)+"\tMULTI_AMMO_LOST\t"+lost);
            }
            if(!field(t,"ShapelessRecipe").isEmpty()) {
                var fields=raw.get(t);
                if(t instanceof BulletType b&&!field(t,"BulletModel").isEmpty()&&!Objects.equals(field(t,"BulletModel").getFirst(),b.bulletModel))
                    broader.add(owner(t)+"\tSHAPELESS_MODEL_LOST\t"+field(t,"BulletModel"));
                if(fields.containsKey("RecipeOutput")&&!Objects.equals(fields.get("RecipeOutput").getFirst(),Integer.toString(t.recipeOutput)))
                    broader.add(owner(t)+"\tSHAPELESS_OUTPUT_LOST\t"+fields.get("RecipeOutput")+" -> "+t.recipeOutput);
            }
        }
        write(out,"magazines.tsv",magRows);write(out,"ammo-loading.tsv",loads);write(out,"gun-magazine-compatibility.tsv",compatibility);write(out,"related-assets.tsv",assets);write(out,"duplicates.tsv",duplicates);write(out,"findings.tsv",issues);
        write(out,"parsed-metadata.tsv",metadata);write(out,"magazine-followers.tsv",followers);write(out,"gun-feeding.tsv",feeding);write(out,"shared-parser-impact.tsv",broader);
        var definitions=new ArrayList<String>();definitions.add("Item\tType\tRegisteredWinner\tDefinitionFile");
        related.stream().sorted(Comparator.comparing(Audit556Series::owner)).forEach(t->definitions.add(owner(t)+"\t"+t.enumType+"\t"+registered(t)+"\t"+sources.get(t).toAbsolutePath()));
        write(out,"definitions.tsv",definitions);
        var summary=new JsonObject();summary.addProperty("jar",Path.of(GunMagazine.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getFileName().toString());summary.addProperty("magazineDefinitions",mags.size());summary.addProperty("registeredMagazines",mags.stream().filter(m->m.item!=null).count());
        summary.addProperty("registeredRoundsAndCases",rounds.size());summary.addProperty("firearmDefinitions",guns.size());summary.addProperty("registeredFirearms",guns.stream().filter(g->g.item!=null).count());
        summary.addProperty("relatedDefinitions",related.size());summary.addProperty("compressionStrokes",compressions);summary.addProperty("consumedRounds",shots);summary.addProperty("nbtRoundTrips",roundTrips);
        summary.addProperty("completeGunFeedCycles",feedCycles);summary.addProperty("completeGunFeedRounds",feedRounds);summary.addProperty("findingRowsNotDistinctBugs",issues.size());
        Files.writeString(out.resolve("summary.json"),new GsonBuilder().setPrettyPrinting().create().toJson(summary),StandardCharsets.UTF_8);
        System.out.println(summary);
        for(var issue:issues)System.out.println(issue);
    }
}
