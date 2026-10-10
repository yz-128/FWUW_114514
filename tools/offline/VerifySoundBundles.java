package com.flansmod.common;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.resources.sounds.SoundEventRegistration;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.resources.*;
import net.minecraft.server.packs.repository.PackSource;

/** Compare merged registrations with Minecraft's own sequential sound-registration behavior. */
public final class VerifySoundBundles {
 static void check(boolean value,String detail){if(!value)throw new AssertionError(detail);}
 static byte[] bytes(String value){return value.getBytes(StandardCharsets.UTF_8);}
 static final class MemoryPack extends AbstractPackResources {
  final String json;
  MemoryPack(String name,String json){super(new PackLocationInfo(name,Component.literal(name),PackSource.BUILT_IN,Optional.empty()));this.json=json;}
  public IoSupplier<InputStream> getRootResource(String... path){return null;}
  public IoSupplier<InputStream> getResource(PackType type,ResourceLocation id){return ()->new ByteArrayInputStream(bytes(id.getPath().equals("sounds.json")?json:"audio fixture"));}
  public void listResources(PackType type,String namespace,String path,ResourceOutput output){}
  public Set<String> getNamespaces(PackType type){return Set.of("flansmod");}
  public void close(){}
 }
 static Map<ResourceLocation,WeighedSoundEvents> nativeEvents(List<String> documents)throws Exception {
  var clazz=Class.forName("net.minecraft.client.sounds.SoundManager$Preparations");var ctor=clazz.getDeclaredConstructor();ctor.setAccessible(true);Object preparation=ctor.newInstance();
  var cacheField=clazz.getDeclaredField("soundCache");cacheField.setAccessible(true);var cache=new HashMap<ResourceLocation,Resource>();
  var audio=new MemoryPack("native-fixture","{}");
  for(String name:List.of("outside","a","b","c","d"))cache.put(ResourceLocation.parse("flansmod:sounds/"+name+".ogg"),new Resource(audio,()->new ByteArrayInputStream(bytes("fixture"))));
  cacheField.set(preparation,cache);
  var gsonField=Class.forName("net.minecraft.client.sounds.SoundManager").getDeclaredField("GSON");gsonField.setAccessible(true);var gson=(Gson)gsonField.get(null);
  var handle=clazz.getDeclaredMethod("handleRegistration",ResourceLocation.class,SoundEventRegistration.class);handle.setAccessible(true);
  for(String document:documents){Map<String,SoundEventRegistration> registrations=gson.fromJson(document,new TypeToken<Map<String,SoundEventRegistration>>(){}.getType());
   for(var e:registrations.entrySet())handle.invoke(preparation,ResourceLocation.parse("flansmod:"+e.getKey()),e.getValue());}
  var registry=clazz.getDeclaredField("registry");registry.setAccessible(true);
  @SuppressWarnings("unchecked") var result=(Map<ResourceLocation,WeighedSoundEvents>)registry.get(preparation);return result;
 }
 public static void main(String[] args)throws Exception {
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  String outside="{\"x\":{\"subtitle\":\"outside\",\"sounds\":[\"flansmod:outside\"]}}";
  String first="{\"X\":{\"subtitle\":\"first\",\"sounds\":[\"flansmod:A.ogg\"]},\"FirstOnly\":{\"sounds\":[\"flansmod:A\"]}}";
  String append="{\"x\":{\"subtitle\":\"later\",\"sounds\":[{\"name\":\"flansmod:B\",\"weight\":3}]},\"SecondOnly\":{\"sounds\":[\"flansmod:B\"]}}";
  String replace="{\"x\":{\"replace\":true,\"subtitle\":\"replacement\",\"sounds\":[\"flansmod:C\"]}}";
  String last="{\"x\":{\"sounds\":[{\"name\":\"flansmod:D\",\"weight\":2}]}}";
  for(var inputs:List.of(List.of(first,append),List.of(first,replace,last),List.of(first,append,replace,last))){
   var merged=new JsonObject();for(String doc:inputs)SoundJsonRewriter.mergeInto(merged,bytes(doc));
   var sequence=new ArrayList<String>();sequence.add(outside);for(String doc:inputs)sequence.add(new String(SoundJsonRewriter.rewrite(bytes(doc)),StandardCharsets.UTF_8));
   var original=nativeEvents(sequence);var combined=nativeEvents(List.of(outside,merged.toString()));check(original.keySet().equals(combined.keySet()),"all event names survive merge");
   for(var key:original.keySet()){check(original.get(key).getWeight()==combined.get(key).getWeight(),"native variant weights/replace: "+key);
    check(Objects.equals(original.get(key).getSubtitle(),combined.get(key).getSubtitle()),"native subtitle and external replace behavior: "+key);}
  }
  var children=List.<PackResources>of(new MemoryPack("first",first),new MemoryPack("second",append));
  var bundle=new ContentPackResources.CompositePackResources(new PackLocationInfo("bundle",Component.literal("bundle"),PackSource.BUILT_IN,Optional.empty()),children);
  var supplier=bundle.getResource(PackType.CLIENT_RESOURCES,ResourceLocation.parse("flansmod:sounds.json"));
  try(var in=supplier.get()){var json=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
   check(json.has("firstonly")&&json.has("secondonly")&&json.getAsJsonObject("x").getAsJsonArray("sounds").size()==2,"real combined pack exposes both files, not only the first");}
  try(var in=bundle.getResource(PackType.CLIENT_RESOURCES,ResourceLocation.parse("flansmod:skins/fixture.png")).get()){
   check(new String(in.readAllBytes(),StandardCharsets.UTF_8).equals("audio fixture"),"ordinary resource lookup remains usable");}
  System.out.println("PASS: production composite includes every child; actual vanilla sequential vs merged registration weights, replace, outside registrations, subtitle, mixed-case and .ogg normalization match.");
 }
}
