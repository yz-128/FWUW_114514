import com.flansmod.common.*;
import com.flansmod.common.types.*;
import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.Textures;
import com.google.gson.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.zip.ZipFile;

/** Reads every enabled pack using production definition/model/resource readers. No game process. */
public final class CrossPackAudit {
 static final class Pack {
  Path root; String name;
  Map<String,Path> files=new LinkedHashMap<>();
  List<InfoType> types=new ArrayList<>();
  JsonObject sounds=new JsonObject();
  Pack(Path path)throws Exception {
   name=path.getFileName().toString();root=PackLayout.findPackRoot(path);
   try(var walk=Files.walk(root)){for(var file:walk.filter(Files::isRegularFile).toList())
    files.put(root.relativize(file).toString().replace('\\','/').toLowerCase(Locale.ROOT),file);}
  }
 }
 static void write(Path out,String name,List<String> lines)throws Exception {
  Files.write(out.resolve(name),lines,StandardCharsets.UTF_8);
 }
 static String hash(Path p)throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
 static String owner(InfoType t){return t.contentPack+"/"+t.type().folderName+"/"+t.shortName;}
 static List<String> strings(Object value){var list=new ArrayList<String>();
  if(value instanceof String s)list.add(s);else if(value instanceof String[] a)list.addAll(Arrays.asList(a));
  else if(value instanceof List<?> a)for(var v:a)if(v instanceof String s)list.add(s);return list;}
 static String normalSound(String s){if(s==null || s.isBlank() || s.equalsIgnoreCase("none"))return null;
  s=s.toLowerCase(Locale.ROOT);if(s.endsWith(".ogg"))s=s.substring(0,s.length()-4);return s.contains(":")?s:"flansmod:"+s;}
 static void attachments(ModifiableType.DefaultAttachment node,InfoType owner,List<String> rows){
  var target=AttachmentType.getAttachment(node.name);
  rows.add(owner(owner)+"\t"+node.name+"\t"+(target==null?"MISSING":target.contentPack));
  for(var child:node.children)attachments(child,owner,rows);
 }
 public static void main(String[] args)throws Exception {
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  Path parent=Path.of(args[0]).toAbsolutePath(),out=Path.of(args[1]);Files.createDirectories(out);
  var packs=new ArrayList<Pack>();var disabled=new ArrayList<String>();
  try(var dir=Files.newDirectoryStream(parent)){for(var p:dir){String n=p.getFileName().toString();
   if(n.startsWith("_") || n.startsWith(".")){disabled.add(n);continue;}
   if(!Files.isDirectory(p))throw new IllegalArgumentException("Enabled archive needs separate audit: "+p);
   var pack=new Pack(p);packs.add(pack);FlansMod.contentPacks.put(n,p);
  }}
  // The game collects all packs first, then parses in EnumType order, with filesystem order retained.
  var source=new IdentityHashMap<InfoType,Path>();
  for(var kind:EnumType.values())for(var pack:packs){Path dir=pack.root.resolve(kind.folderName);if(!Files.isDirectory(dir))continue;
   try(var files=Files.newDirectoryStream(dir)){for(var p:files){String n=p.getFileName().toString();
    if(!Files.isRegularFile(p) || !n.toLowerCase(Locale.ROOT).matches(".*\\.(txt|df)"))continue;
    var tf=new TypeFile(kind,n.substring(0,n.lastIndexOf('.')));tf.contentPack=pack.name;tf.lines.addAll(TextDecoding.readLines(p));
    var type=kind.getTypeClass().getConstructor(TypeFile.class).newInstance(tf);type.read(tf);pack.types.add(type);source.put(type,p);
   }}
  }
  var summaries=new JsonArray();var resources=new LinkedHashMap<String,Path>();var resourcePacks=new LinkedHashMap<String,String>();
  var soundOwners=new TreeMap<String,List<String>>();var assetCollisions=new ArrayList<String>();
  assetCollisions.add("Resource\tFirst pack\tLater pack\tSame SHA256");
  var rewriter=Class.forName("com.flansmod.common.SoundJsonRewriter").getDeclaredMethod("rewrite",byte[].class);rewriter.setAccessible(true);
  var allEvents=new TreeSet<String>();var children=new ArrayList<PackResources>();
  for(var pack:packs){
   var summary=new JsonObject();summary.addProperty("pack",pack.name);summary.addProperty("files",pack.files.size());summary.addProperty("definitions",pack.types.size());
   summary.addProperty("models",pack.files.keySet().stream().filter(n->n.endsWith(".class")).count());summary.addProperty("ogg",pack.files.keySet().stream().filter(n->n.endsWith(".ogg")).count());
   var categories=new JsonObject();for(var kind:EnumType.values()){long count=pack.types.stream().filter(t->t.type()==kind).count();if(count>0)categories.addProperty(kind.folderName,count);}summary.add("categories",categories);
   var info=new PackLocationInfo(pack.name,Component.literal(pack.name),net.minecraft.server.packs.repository.PackSource.BUILT_IN,Optional.empty());
   children.add(new FolderPackResources(info,pack.root));
   Path soundFile=pack.files.get("assets/flansmod/sounds.json");
   if(soundFile!=null){byte[] fixed=(byte[])rewriter.invoke(null,(Object)Files.readAllBytes(soundFile));
    pack.sounds=JsonParser.parseString(new String(fixed,StandardCharsets.UTF_8)).getAsJsonObject();
    for(var event:pack.sounds.keySet()){String id="flansmod:"+event;allEvents.add(id);soundOwners.computeIfAbsent(id,k->new ArrayList<>()).add(pack.name);}}
   summary.addProperty("soundEvents",pack.sounds.size());summaries.add(summary);
   for(var e:pack.files.entrySet())if(e.getKey().startsWith("assets/")){
    Path earlier=resources.putIfAbsent(e.getKey(),e.getValue());
    if(earlier==null)resourcePacks.put(e.getKey(),pack.name);
    else assetCollisions.add(e.getKey()+"\t"+resourcePacks.get(e.getKey())+"\t"+pack.name+"\t"+hash(earlier).equals(hash(e.getValue())));
   }
  }
  var compositeClass=Class.forName("com.flansmod.common.ContentPackResources$CompositePackResources");
  var ctor=compositeClass.getDeclaredConstructor(PackLocationInfo.class,List.class);ctor.setAccessible(true);
  var bundle=(PackResources)ctor.newInstance(new PackLocationInfo("audit",Component.literal("audit"),net.minecraft.server.packs.repository.PackSource.BUILT_IN,Optional.empty()),children);
  var served=bundle.getResource(PackType.CLIENT_RESOURCES,ResourceLocation.parse("flansmod:sounds.json"));
  JsonObject servedEvents;try(var in=served.get()){servedEvents=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();}
  var hidden=new TreeSet<>(allEvents);for(var name:servedEvents.keySet())hidden.remove("flansmod:"+name);
  write(out,"hidden_sound_events.tsv",hidden.stream().map(n->n+"\t"+soundOwners.get(n)).toList());
  var duplicates=new ArrayList<String>();duplicates.add("Registry path\tFirst definition\tSkipped definition\tFirst source\tSkipped source");
  var winners=new LinkedHashMap<String,InfoType>();
  for(var type:InfoType.infoTypes){String id=ItemAssets.registryPathOf(type);var first=winners.putIfAbsent(id,type);
   if(first!=null)duplicates.add(id+"\t"+owner(first)+"\t"+owner(type)+"\t"+source.get(first)+"\t"+source.get(type));}
  var caseRows=new ArrayList<String>();caseRows.add("Round\tRequested case\tResolved case pack\tCase model\tCase texture\tModel loaded\tTexture exists");
  int eftCases=0,eftResolved=0;
  for(var type:InfoType.infoTypes)if(type instanceof BulletType round && !round.isCase && round.dropItemOnShoot!=null){
   var casing=BulletType.getBullet(round.dropItemOnShoot);String model=casing==null?null:casing.bulletModel;
   var texture=casing==null?null:Textures.of(casing.bulletTexture,false,id->resources.containsKey("assets/"+id.getNamespace()+"/"+id.getPath()));
   boolean modelOk=model!=null&&ModelCache.load(model)!=null,textureOk=texture!=null&&resources.containsKey("assets/"+texture.getNamespace()+"/"+texture.getPath());
   caseRows.add(owner(round)+"\t"+round.dropItemOnShoot+"\t"+(casing==null?"MISSING":casing.contentPack)+"\t"+model+"\t"+texture+"\t"+modelOk+"\t"+textureOk);
   if(round.contentPack.equals("EscapeFromTarcraftReforged_1.5.3")){eftCases++;if(casing!=null&&casing.isCase&&modelOk&&textureOk)eftResolved++;}
  }
  var models=new ArrayList<String>();models.add("Definition\tField\tReference\tLoaded");
  var modelResults=new HashMap<String,Boolean>();int modelRefs=0,missingModels=0;
  for(var type:InfoType.infoTypes)for(var field:type.getClass().getFields()){
   if(Modifier.isStatic(field.getModifiers()) || !(field.getName().toLowerCase(Locale.ROOT).contains("model")))continue;
   for(var ref:strings(field.get(type)))if(ref!=null&&!ref.isBlank()){
    boolean loaded=modelResults.computeIfAbsent(ref,n->ModelCache.load(n)!=null);modelRefs++;if(!loaded)missingModels++;
    models.add(owner(type)+"\t"+field.getName()+"\t"+ref+"\t"+loaded);
   }
  }
  var audio=new ArrayList<String>();audio.add("Pack\tEvent\tReference\tExists globally\tProviding pack");int audioRefs=0,missingAudio=0,invalidOgg=0;
  for(var pack:packs){for(var entry:pack.sounds.entrySet())for(var ref:entry.getValue().getAsJsonObject().getAsJsonArray("sounds")){
   if(ref.isJsonObject() && ref.getAsJsonObject().has("type") && ref.getAsJsonObject().get("type").getAsString().equals("event"))continue;
   String id=normalSound(ref.isJsonPrimitive()?ref.getAsString():ref.getAsJsonObject().get("name").getAsString());
   var loc=ResourceLocation.parse(id);String key="assets/"+loc.getNamespace()+"/sounds/"+loc.getPath()+".ogg";
   boolean exists=resources.containsKey(key);audioRefs++;if(!exists)missingAudio++;
   audio.add(pack.name+"\t"+entry.getKey()+"\t"+id+"\t"+exists+"\t"+resourcePacks.getOrDefault(key,"MISSING"));
  }
   for(var file:pack.files.entrySet())if(file.getKey().endsWith(".ogg")){
    byte[] bytes=Files.readAllBytes(file.getValue());if(bytes.length<64 || bytes[0]!='O' || bytes[1]!='g' || bytes[2]!='g' || bytes[3]!='S' || !new String(bytes,StandardCharsets.ISO_8859_1).contains("vorbis"))invalidOgg++;
   }
  }
  var soundRefs=new ArrayList<String>();soundRefs.add("Definition\tField\tEvent\tDeclared in enabled packs\tServed by bundle");int typeSoundRefs=0,missingSoundRefs=0;
  for(var type:InfoType.infoTypes)for(var field:type.getClass().getFields()){
   if(Modifier.isStatic(field.getModifiers()) || !field.getName().toLowerCase(Locale.ROOT).contains("sound"))continue;
   var names=strings(field.get(type));
   if(type instanceof ArmourType armour && field.getName().equals("hitSounds")){names.clear();for(int i=0;i<armour.hitSounds.length;i++)if(armour.hitSounds[i]!=null)for(int j=0;j<armour.numHitSounds[i];j++)names.add(armour.hitSounds[i]+"_"+j);}
   for(var name:names){String id=normalSound(name);if(id==null)continue;var loc=ResourceLocation.parse(id);if(!loc.getNamespace().equals("flansmod"))continue;
    boolean exists=allEvents.contains(id);typeSoundRefs++;if(!exists)missingSoundRefs++;
    soundRefs.add(owner(type)+"\t"+field.getName()+"\t"+id+"\t"+exists+"\t"+servedEvents.has(loc.getPath()));
   }
  }
  var nodes=new ArrayList<String>();nodes.add("Definition\tDefault attachment\tResolved pack");
  for(var type:InfoType.infoTypes)if(type instanceof ModifiableType mod)for(var group:mod.defaultAttachments)for(var node:group)attachments(node,type,nodes);
  write(out,"duplicates.tsv",duplicates);write(out,"asset_collisions.tsv",assetCollisions);write(out,"cases.tsv",caseRows);write(out,"models.tsv",models);
  write(out,"audio.tsv",audio);write(out,"sound_references.tsv",soundRefs);write(out,"default_attachments.tsv",nodes);
  var stats=new JsonObject();stats.add("packs",summaries);stats.add("disabled",new Gson().toJsonTree(disabled));
  stats.addProperty("definitions",InfoType.infoTypes.size());stats.addProperty("uniqueItems",winners.size());stats.addProperty("duplicateItems",duplicates.size()-1);
  var groups=new TreeMap<String,Integer>();
  for(var t:winners.values()){
   boolean magazine=t instanceof GunType g && g.itsType==GunType.GunItsType.MAG;
   String group=magazine||t.type()==EnumType.bullet?"ammo":t.type()==EnumType.attachment?"attachments":
    t.type()==EnumType.gun||t.type()==EnumType.grenade||t.type()==EnumType.aa?"guns":t.type()==EnumType.armour?"teams":
    t.type()==EnumType.part||t.type()==EnumType.tool||t.type()==EnumType.box?"parts":t.type().folderName;
   groups.merge(group,1,Integer::sum);
  }
  stats.add("registeredItemGroups",new Gson().toJsonTree(groups));
  stats.addProperty("unionSoundEvents",allEvents.size());stats.addProperty("servedSoundEvents",servedEvents.size());stats.addProperty("hiddenSoundEvents",hidden.size());
  stats.addProperty("audioReferences",audioRefs);stats.addProperty("missingAudioReferences",missingAudio);stats.addProperty("invalidOggHeaders",invalidOgg);
  stats.addProperty("typeSoundReferences",typeSoundRefs);stats.addProperty("missingTypeSoundReferences",missingSoundRefs);
  stats.addProperty("modelReferences",modelRefs);stats.addProperty("missingModelReferences",missingModels);
  stats.addProperty("eftCasingReferences",eftCases);stats.addProperty("eftResolvedCasings",eftResolved);
  var disabledArchives=new JsonArray();
  Path disabledDir=parent.resolve("_disabled");if(Files.isDirectory(disabledDir))try(var stream=Files.list(disabledDir)){
   for(var path:stream.filter(p->p.getFileName().toString().endsWith(".zip")).toList()){
    ZipFile opened;try{opened=new ZipFile(path.toFile());}catch(java.io.IOException error){opened=new ZipFile(path.toFile(),java.nio.charset.Charset.forName("GBK"));}
    try(var zip=opened){
     String prefix=PackLayout.findArchiveRoot(zip);var entries=zip.stream().filter(e->!e.isDirectory()&&e.getName().startsWith(prefix)).toList();
     var item=new JsonObject();item.addProperty("archive",path.getFileName().toString());item.addProperty("files",entries.size());item.addProperty("root",prefix);
     item.addProperty("models",entries.stream().filter(e->e.getName().endsWith(".class")).count());item.addProperty("ogg",entries.stream().filter(e->e.getName().endsWith(".ogg")).count());
     Pack matching=packs.stream().filter(p->prefix.equals(p.name+"/")).findFirst().orElse(null);
     if(matching!=null){int same=0;
      for(var entry:entries){Path file=matching.files.get(entry.getName().substring(prefix.length()).toLowerCase(Locale.ROOT));
       if(file!=null)try(var in=zip.getInputStream(entry)){if(Arrays.equals(Files.readAllBytes(file),in.readAllBytes()))same++;}}
      item.addProperty("identicalFilesInEnabledFolder",same);item.addProperty("sameAsEnabledFolder",same==entries.size()&&same==matching.files.size());
     }
     disabledArchives.add(item);
    }
   }
  }
  stats.add("disabledArchives",disabledArchives);
  Files.writeString(out.resolve("summary.json"),new GsonBuilder().setPrettyPrinting().create().toJson(stats),StandardCharsets.UTF_8);
  System.out.println(new Gson().toJson(stats));
 }
}
