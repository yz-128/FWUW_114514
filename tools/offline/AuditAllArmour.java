import com.flansmod.common.*;
import com.flansmod.common.types.*;
import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.registries.*;
import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.resources.*;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.*;

/** All enabled armour definitions and actual item factory, resource and worn-geometry audit. */
public final class AuditAllArmour {
    static final List<OfflinePack> packs=new ArrayList<>();
    static final Map<ArmourType,Path> sources=new IdentityHashMap<>();
    static final Map<ArmourType,List<String>> raw=new IdentityHashMap<>();
    static boolean has(ResourceLocation id){return id!=null&&packs.stream().anyMatch(p->p.has(id));}
    static void load(Path root)throws Exception{
        try(var dirs=Files.newDirectoryStream(root)){for(var path:dirs)if(Files.isDirectory(path)&&!path.getFileName().toString().matches("^[_.].*")){
            var pack=new OfflinePack(path);packs.add(pack);FlansMod.contentPacks.put(path.getFileName().toString(),path.toAbsolutePath());
        }}
        for(var pack:packs)for(var entry:pack.files.entrySet())if(entry.getKey().matches("armorfiles/[^/]+\\.(txt|df)")){
            var f=new TypeFile(EnumType.armour,entry.getValue().getFileName().toString(),false);f.contentPack=pack.root.getFileName().toString();
            f.lines.addAll(TextDecoding.readLines(entry.getValue()));var type=new ArmourType(f);type.read(f);sources.put(type,entry.getValue());raw.put(type,List.copyOf(f.lines));
        }
        ((MappedRegistry<?>)BuiltInRegistries.ITEM).unfreeze();
        var constructor=RegisterEvent.class.getDeclaredConstructor(ResourceKey.class,Registry.class);constructor.setAccessible(true);
        ContentRegistry.onRegister(constructor.newInstance(Registries.ITEM,BuiltInRegistries.ITEM));
    }
    public static void main(String[] args)throws Exception{
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();load(Path.of(args[0]));Path out=Path.of(args[1]);Files.createDirectories(out);
        var rows=new ArrayList<String>();rows.add("Item\tRegistered\tSlot\tDurability\tDefence\tProof\tMask\tInvisible\tModel\tTexture\tIcon\tFaces\tProblems\tSource");
        var findings=new ArrayList<String>();findings.add("Item\tProblem\tDetail");var counts=new TreeMap<String,Integer>();int registered=0,faces=0,mask=0;
        var pose=VerifyArmourRendering.body();pose.young=false;
        for(var a:ArmourType.armours){String owner=a.contentPack+"/"+a.shortName;var issues=new ArrayList<String>();
            var source=ArmourResources.source(a);var texture=source.map(ArmourResources.Skin::original).orElse(null);boolean skin=source.isPresent();
            var rig=skin?ModelCache.rigForArmour(a,texture):null;
            String iconName=a.iconPath==null?a.shortName:a.iconPath;
            var icon=ResourceLocation.fromNamespaceAndPath("flansmod",("textures/"+(iconName.contains("/")?iconName:"items/"+iconName)+".png").toLowerCase(Locale.ROOT));
            boolean iconOk=has(icon)||AuditAllArmour.class.getResource("/assets/"+icon.getNamespace()+"/"+icon.getPath())!=null;
            if(a.item!=null){registered++;if(!(a.item instanceof ItemFlanArmour))issues.add("NOT_EQUIPABLE");}
            if(a.type<0||a.type>3)issues.add("INVALID_SLOT");if(!skin)issues.add("MISSING_TEXTURE");if(!iconOk)issues.add("MISSING_ICON");
            if(!ModelCache.hasReference(a.modelString)||(rig!=null&&rig.quads.isEmpty()))issues.add("MISSING_MODEL");
            if(rig!=null&&!rig.quads.isEmpty()){
                faces+=rig.quads.size();for(String group:rig.quadsByPart.keySet())if(LegacyArmourModel.joint(pose,group)==null)issues.add("UNSUPPORTED_GROUP:"+group);
                var sink=new VerifyArmourRendering.Recorder();new LegacyArmourModel(rig,pose).renderToBuffer(new com.mojang.blaze3d.vertex.PoseStack(),sink,0xF000F0,0,-1);
                if(sink.positions.isEmpty())issues.add("NO_WORN_VERTICES");
            }
            if(a.hasMask)mask++;counts.merge(a.contentPack,1,Integer::sum);
            if(a.item!=null&&raw.get(a).stream().anyMatch(s->s.trim().matches("(?i)BreakableArmor\\s+false"))&&new ItemStack(a.item).isDamageableItem())issues.add("NONBREAKABLE_IGNORED");
            for(String issue:issues)findings.add(owner+"\t"+issue+"\t"+(issue.contains("TEXTURE")?texture:issue.contains("MODEL")?a.modelString:issue.contains("ICON")?icon:""));
            rows.add(owner+"\t"+(a.item!=null)+"\t"+a.type+"\t"+a.durability+"\t"+a.defence+"\t"+Arrays.toString(a.proofLevel)+"\t"+a.hasMask+"\t"+a.invisible+"\t"+a.modelString+"\t"+texture+"\t"+icon+"\t"+(rig==null?0:rig.quads.size())+"\t"+issues+"\t"+sources.get(a).toAbsolutePath());
        }
        Files.write(out.resolve("armour.tsv"),rows,StandardCharsets.UTF_8);Files.write(out.resolve("findings.tsv"),findings,StandardCharsets.UTF_8);
        var summary=new JsonObject();summary.addProperty("definitions",ArmourType.armours.size());summary.addProperty("registered",registered);summary.addProperty("duplicateDefinitions",ArmourType.armours.size()-registered);
        summary.addProperty("faces",faces);summary.addProperty("maskDefinitions",mask);summary.addProperty("findingRows",findings.size()-1);summary.add("packs",new Gson().toJsonTree(counts));
        Files.writeString(out.resolve("summary.json"),new GsonBuilder().setPrettyPrinting().create().toJson(summary));System.out.println(summary);
        for(int i=1;i<findings.size();i++)System.out.println(findings.get(i));
    }
}
