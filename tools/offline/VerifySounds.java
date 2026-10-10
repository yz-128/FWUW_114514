import com.flansmod.common.*;
import com.flansmod.common.types.*;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import io.netty.buffer.Unpooled;

/** Pack audio/event references plus actual modern packet encoding of unregistered sound events. */
public final class VerifySounds {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static final Set<String> warned=new TreeSet<>();
 public static void main(String[] args)throws Exception {
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  var pack=new OfflinePack(Path.of(args[0]));pack.readTypes();
  var rewriter=Class.forName("com.flansmod.common.SoundJsonRewriter").getDeclaredMethod("rewrite",byte[].class);rewriter.setAccessible(true);
  byte[] json=(byte[])rewriter.invoke(null,(Object)Files.readAllBytes(pack.root.resolve("assets/flansmod/sounds.json")));
  var sounds=JsonParser.parseString(new String(json,StandardCharsets.UTF_8)).getAsJsonObject();
  int variants=0;var missing=new TreeSet<String>();var audio=new TreeSet<Path>();
  for(var entry:sounds.entrySet()) {
   check(ResourceLocation.tryParse("flansmod:"+entry.getKey())!=null,"normalised sound event identifier");
   for(var ref:entry.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
    String path=ref.isJsonPrimitive()?ref.getAsString():ref.getAsJsonObject().get("name").getAsString();
    if(ref.isJsonObject() && ref.getAsJsonObject().has("type") && ref.getAsJsonObject().get("type").getAsString().equals("event"))continue;
    var id=FmumSounds.id(path);check(id!=null,"normalised audio path");var file=pack.path(ResourceLocation.fromNamespaceAndPath(id.getNamespace(),"sounds/"+id.getPath()+".ogg"));
    variants++;if(file==null){missing.add(entry.getKey()+" -> "+path);continue;}audio.add(file);
   }
  }
  var allAudio=new TreeSet<Path>();
  for(var entry:pack.files.entrySet())if(entry.getKey().startsWith("assets/flansmod/sounds/") && entry.getKey().endsWith(".ogg"))allAudio.add(entry.getValue());
  for(var file:allAudio) {
   byte[] bytes=Files.readAllBytes(file);check(bytes.length>64 && bytes[0]=='O' && bytes[1]=='g' && bytes[2]=='g' && bytes[3]=='S',"Ogg audio header: "+file);
   check(new String(bytes,StandardCharsets.ISO_8859_1).contains("vorbis"),"Vorbis stream: "+file);
  }
  int references=0,guns=0;
  for(var t:pack.types)for(var field:t.getClass().getFields()) {
   if(!field.getName().toLowerCase(Locale.ROOT).contains("sound"))continue;
   Object value=field.get(t);var names=new ArrayList<String>();
   if(value instanceof String s)names.add(s);else if(value instanceof String[] list)names.addAll(Arrays.asList(list));else if(value instanceof List<?> list)for(var item:list)if(item instanceof String s)names.add(s);
   if(t instanceof ArmourType armour && field.getName().equals("hitSounds")) {
    names.clear();for(int i=0;i<armour.hitSounds.length;i++)if(armour.hitSounds[i]!=null)for(int j=0;j<armour.numHitSounds[i];j++)names.add(armour.hitSounds[i]+"_"+j);
   }
   for(var name:names)if(FmumSounds.id(name)!=null) {
    references++;var id=FmumSounds.id(name);if(id.getNamespace().equals("flansmod") && !sounds.has(id.getPath()))warned.add(t.shortName+"."+field.getName()+" -> "+name);
   }
  }
  for(var t:pack.types)if(t instanceof GunType g && !g.magazineNames.isEmpty() && g.itsType!=GunType.GunItsType.MAG) {
   guns++;check(!g.shootSounds.isEmpty(),"all gun shot variants parsed: "+g.shortName);
   var shot=FmumSounds.shot(g);check(sounds.has(FmumSounds.id(shot.name()).getPath()),"effective default-loadout shot event: "+g.shortName);
   check(shot.range()>0,"effective sound radius: "+g.shortName);
   if(g.itsType==GunType.GunItsType.BOLT_GUN)check(g.chargeSound[0]!=null && sounds.has(FmumSounds.id(g.chargeSound[0]).getPath()) && g.chargeSoundR[0]==8,"single-token bolt sound and original default range: "+g.shortName);
   System.out.printf("%s shot=%s range=%.2f boltSound=%s reload=%s%n",g.shortName,shot.name(),shot.range(),g.chargeSound[0],g.reloadSounds);
  }
  check(guns==31,"31 guns checked");
  check(FmumSounds.id("None")==null && FmumSounds.id("")==null && FmumSounds.id("AK74Shot.ogg").toString().equals("flansmod:ak74shot"),"silent sentinel/mixed-case/suffix handling");
  for(var name:List.of("AK74Shot","MosinBolt","km","ammo_load","bullet")) {
   var sound=FmumSounds.event(name,8F);check(sound!=null && sound.getRange(10F)==8F,"unregistered sound remains playable with exact range");
   var packet=new ClientboundSoundPacket(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),SoundSource.PLAYERS,1,2,3,10F,1F,91L);
   var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
   try{ClientboundSoundPacket.STREAM_CODEC.encode(buffer,packet);var decoded=ClientboundSoundPacket.STREAM_CODEC.decode(buffer);
    check(decoded.getSound().value().getLocation().equals(sound.getLocation()) && decoded.getVolume()==10F && decoded.getSound().value().getRange(10F)==8F && !buffer.isReadable(),"real vanilla sound packet carries direct pack event without registry registration: "+name);
   }finally{buffer.release();}
  }
  for(var m:missing)System.out.println("PACK MISSING AUDIO (left as supplied): "+m);
  for(var m:warned)System.out.println("PACK UNRESOLVED EVENT: "+m);
  System.out.printf("PASS: %d sound events, %d audio references (%d existing linked files), %d total Ogg/Vorbis streams, %d type sound references, 31 effective firearm sound profiles, single-token bolt sound/default ranges, actual direct-event vanilla packet round trips. Pack issues: %d missing audio variants, %d unresolved type references.%n",sounds.size(),variants,audio.size(),allAudio.size(),references,missing.size(),warned.size());
 }
}
