import com.flansmod.common.*;
import com.flansmod.common.types.*;
import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.Textures;
import dev.packarms.core.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.network.chat.Component;

/** Ordered real-pack differential checks plus production lazy-cache/reload contracts; no client. */
public final class VerifyPackIntegration {
    static int checks;
    static void check(boolean value,String message) { checks++; if(!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Path directory=Path.of(args[0]);
        List<Path> sources=new ArrayList<>();
        try(var list=Files.newDirectoryStream(directory)) { for(var source:list) {
            if(disabled(source.getFileName().toString())) continue;
            String lower=source.getFileName().toString().toLowerCase(Locale.ROOT);
            if(Files.isDirectory(source)||lower.endsWith(".jar")||lower.endsWith(".zip")) sources.add(source);
        } }
        FlansMod.contentPacks.clear();
        for(var source:sources) FlansMod.contentPacks.put(source.getFileName().toString(),source);
        var index=ContentPackIndex.current();
        check(index.packs().size()==sources.size(),"same enabled packs");
        int definitions=0,resources=0;
        var winner=new LinkedHashMap<String,PackEntry>();
        for(var pack:index.packs()) {
            List<String> old=legacyDefinitions(pack.source()),now=new ArrayList<>();
            for(var kind:EnumType.values()) for(var entry:pack.under(kind.folderName+"/")) {
                String rest=entry.path().substring(kind.folderName.length()+1);
                if(!rest.contains("/")&&definition(rest)) now.add(entry.path());
            }
            check(old.equals(now),"definition enumeration order: "+pack.name()); definitions+=now.size();
            for(String name:now) check(Arrays.equals(legacyBytes(pack.source(),name),pack.exact(name).orElseThrow().readAllBytes()),"definition bytes: "+name);
            for(var entry:pack.entries()) if(entry.path().startsWith("assets/")) {
                winner.putIfAbsent(entry.path().toLowerCase(Locale.ROOT),entry);
            }
        }
        for(var entry:winner.entrySet()) {
            var found=index.find(entry.getKey(),true).orElseThrow();
            check(found.source().equals(entry.getValue().source()),"resource winner: "+entry.getKey()); resources++;
        }
        // Parse definitions independently of Minecraft construction, using the production type classes.
        for(var pack:index.packs()) for(var kind:EnumType.values()) for(var entry:pack.under(kind.folderName+"/")) {
            String rest=entry.path().substring(kind.folderName.length()+1);
            if(rest.contains("/")||!definition(rest)) continue;
            TypeFile file=new TypeFile(kind,rest.substring(0,rest.lastIndexOf('.'))); file.contentPack=pack.name();
            file.lines.addAll(TextDecoding.decode(entry.readAllBytes()).lines().toList());
            InfoType type=kind.getTypeClass().getConstructor(TypeFile.class).newInstance(file); type.contentPack=pack.name(); type.read(file);
        }
        check(ModelCache.geometryStats().loads()==0,"definition parsing and presence indexing do not load geometry");
        var generated=GeneratedAssets.build();
        check(ModelCache.geometryStats().loads()==0&&ModelCache.loaded().isEmpty(),"generated JSON does not parse/bake the full pack");
        var firstGun=InfoType.infoTypes.stream().filter(type -> type instanceof GunType gun && gun.itsType!=GunType.GunItsType.MAG
                &&ModelCache.hasReference(type.modelString)).findFirst().orElseThrow();
        String ref=firstGun.modelString;
        var model=ModelCache.load(ref); check(model!=null,"sync offline geometry");
        long loads=ModelCache.geometryStats().loads();
        for(int repeat=0;repeat<100;repeat++) check(ModelCache.load(ref)==model,"successful geometry identity");
        check(ModelCache.geometryStats().loads()==loads,"one successful parse");
        var rig=ModelCache.rigFor(firstGun,firstGun.modelScale);
        check(rig!=null&&!rig.quads.isEmpty(),"offline bake");
        check(ModelCache.rigFor(firstGun,firstGun.modelScale)==rig,"final rig identity");
        check(ModelCache.geometryStats().loads()==loads,"rig shares the same geometry parse");
        check(geometryDigest(BakedRig.bake(legacyModel(ref,sources),firstGun.modelScale)).equals(geometryDigest(rig)),"legacy vertices/UV/normals unchanged");
        String missing="PackIndexVerification.DoesNotExist";
        for(int repeat=0;repeat<100;repeat++) check(ModelCache.rigForReference(missing,1F)==null,"missing model result");
        check(ModelCache.geometryStats().loads()==loads+1&&ModelCache.geometryStats().misses()==1,"negative geometry lookup executes once");
        long generation=index.generation();
        ContentPackIndex.reload(); ModelCache.clear();
        check(ModelCache.indexGeneration()!=generation,"index generation changes");
        check(ModelCache.loaded().isEmpty()&&ModelCache.geometryStats().loads()==0,"reload resets geometry and final rigs");
        var newModel=ModelCache.load(ref); check(newModel!=null&&newModel!=model,"reload reparses new-generation geometry");
        check(geometryDigest(ModelCache.rigFor(firstGun,firstGun.modelScale)).equals(geometryDigest(rig)),"reload preserves visual geometry");
        check(ModelCache.load(missing)==null&&ModelCache.geometryStats().loads()==2,"reload retries previously missing class once");
        Path scratch=Files.createTempDirectory("flans-index-integration-");
        try {
            var invalid=scratch.resolve("com/flansmod/client/model/Invalid/ModelBroken.class"); Files.createDirectories(invalid.getParent());
            Files.writeString(invalid,"invalid class bytes",StandardCharsets.UTF_8);
            FlansMod.contentPacks.put("invalid-fixture",scratch);
            ModelCache.clear();
            for(int repeat=0;repeat<100;repeat++) check(ModelCache.load("Invalid.Broken")==null,"failed model cached");
            check(ModelCache.geometryStats().loads()==1&&ModelCache.geometryStats().failures()==1,"one failed parse, distinct from missing");
            // Supply a resource, retain its supplier, then rebuild index and reopen that supplier.
            var asset=scratch.resolve("assets/flansmod/skins/Test.PNG"); Files.createDirectories(asset.getParent()); Files.write(asset,new byte[]{1,2,3});
            ContentPackIndex.reload();
            var info=new PackLocationInfo("verification",Component.literal("verification"),PackSource.BUILT_IN,Optional.empty());
            var folder=new FolderPackResources(info,scratch);
            var supplier=folder.getResource(PackType.CLIENT_RESOURCES,ResourceLocation.fromNamespaceAndPath("flansmod","skins/test.png"));
            check(supplier!=null,"case-folded folder adapter");
            ContentPackIndex.reload();
            try(var stream=supplier.get()) {check(Arrays.equals(stream.readAllBytes(),new byte[]{1,2,3}),"retained supplier reopens current generation");}
            Path archive=scratch.resolve("lease.zip");
            try(var zip=new ZipOutputStream(Files.newOutputStream(archive))) {
                zip.putNextEntry(new ZipEntry("assets/flansmod/skins/Test.PNG")); zip.write(new byte[]{4,5,6}); zip.closeEntry();
                zip.putNextEntry(new ZipEntry("assets/flansmod/sounds.json"));
                zip.write("{\"Shot\":{\"sounds\":[\"flansmod:SHOT.ogg\"]}}".getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            }
            FlansMod.contentPacks.put("archive-fixture",archive); ContentPackIndex.reload();
            var archived=ZipPackResources.open(info,archive); check(archived!=null,"archive adapter opens indexed assets");
            var archivedSupplier=archived.getResource(PackType.CLIENT_RESOURCES,ResourceLocation.fromNamespaceAndPath("flansmod","skins/test.png"));
            check(archivedSupplier!=null,"case-folded archive adapter");
            try(var retainedStream=archivedSupplier.get()) {
                ContentPackIndex.reload();
                check(Arrays.equals(retainedStream.readAllBytes(),new byte[]{4,5,6}),"opened archive lease survives reload");
                try(var currentStream=archivedSupplier.get()) {
                    check(Arrays.equals(currentStream.readAllBytes(),new byte[]{4,5,6}),"retained archive supplier opens new generation");
                }
            }
            try(var sounds=archived.getResource(PackType.CLIENT_RESOURCES,ResourceLocation.fromNamespaceAndPath("flansmod","sounds.json")).get()) {
                check(new String(sounds.readAllBytes(),StandardCharsets.UTF_8).contains("flansmod:shot"),"sound rewrite uses current indexed archive stream");
            }
        } finally {
            FlansMod.contentPacks.remove("invalid-fixture"); FlansMod.contentPacks.remove("archive-fixture"); ContentPackIndex.reload(); ModelCache.clear();
            // Verify the resolved recursive-delete target is this tool's temp-directory child.
            check(scratch.toAbsolutePath().normalize().getParent().equals(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize())
                    && scratch.getFileName().toString().startsWith("flans-index-integration-"),"scratch delete stays in named temp directory");
            try(var walk=Files.walk(scratch)) {for(var file:walk.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);}
        }
        check(ModelCache.geometryStats().loads()==0,"no retired cache result enters active generation");
        prewarm(ref);
        System.out.printf("PASS: %d ordered packs, %d definitions, %d resource winners, %d assertions; generated JSON lazy, successful/missing/failed geometry cached, reload isolation, bounded CPU prewarm.%n",sources.size(),definitions,resources,checks);
    }
    static void prewarm(String reference) throws Exception {
        ModelCache.clear();
        for(float scale:new float[]{1F,.5F,.25F}) ModelCache.prewarmReference(reference,scale,null,true);
        long deadline=System.nanoTime()+10_000_000_000L;
        while(!ModelCache.geometryResolved(reference)&&System.nanoTime()<deadline) java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
        check(ModelCache.geometryResolved(reference),"background read/ASM completes");
        check(ModelCache.geometryStats().loads()==1,"prewarm deduplicates model reference across scales");
        check(ModelCache.bakePending(2)==2&&ModelCache.loaded().size()==2,"client tick bake limit");
        check(ModelCache.bakePending(2)==1&&ModelCache.loaded().size()==3,"remaining bake deferred to next tick");
    }
    static boolean disabled(String name) { return name.startsWith("_")||name.startsWith("."); }
    static boolean definition(String name) {
        String lower=name.toLowerCase(Locale.ROOT);return lower.endsWith(".txt")||lower.endsWith(".df");
    }
    static ZipFile openZip(Path source) throws Exception {
        try {return new ZipFile(source.toFile(),StandardCharsets.UTF_8);}
        catch(ZipException failure) {return new ZipFile(source.toFile(),java.nio.charset.Charset.forName("GBK"));}
    }
    static List<String> legacyDefinitions(Path source) throws Exception {
        List<String> out=new ArrayList<>();
        if(Files.isDirectory(source)) {
            Path root=PackLayout.findPackRoot(source);
            for(var kind:EnumType.values()) {
                Path dir=root.resolve(kind.folderName); if(!Files.isDirectory(dir)) continue;
                try(var stream=Files.newDirectoryStream(dir)) {for(var file:stream) if(Files.isRegularFile(file)&&definition(file.getFileName().toString())) out.add(kind.folderName+"/"+file.getFileName());}
            }
        } else try(ZipFile zip=openZip(source)) {
            String root=PackLayout.findArchiveRoot(zip);
            for(var kind:EnumType.values()) {String prefix=root+kind.folderName+"/";
                for(var entry:Collections.list(zip.entries())) if(!entry.isDirectory()&&entry.getName().startsWith(prefix)) {
                    String rest=entry.getName().substring(prefix.length()); if(!rest.contains("/")&&definition(rest)) out.add(kind.folderName+"/"+rest);
                }
            }
        }
        return out;
    }
    static byte[] legacyBytes(Path source,String path) throws Exception {
        if(Files.isDirectory(source)) return Files.readAllBytes(PackLayout.findPackRoot(source).resolve(path));
        try(ZipFile zip=openZip(source);var stream=zip.getInputStream(zip.getEntry(PackLayout.findArchiveRoot(zip)+path))) {return stream.readAllBytes();}
    }
    static ModelGeometry.Model legacyModel(String ref,List<Path> sources) throws Exception {
        int dot=ref.lastIndexOf('.'); String pkg=dot>0?ref.substring(0,dot):"",simple=dot>0?ref.substring(dot+1):ref;
        String path="com/flansmod/client/model/"+(pkg.isEmpty()?"":pkg.replace('.','/')+"/")+(simple.startsWith("Model")?simple:"Model"+simple)+".class";
        for(var source:sources) {
            for(String candidate:simple.startsWith("Model")?List.of(path,path.substring(0,path.lastIndexOf('/')+1)+"Model"+path.substring(path.lastIndexOf('/')+1)):List.of(path)) {
                if(Files.isDirectory(source)) {Path file=PackLayout.findPackRoot(source).resolve(candidate);if(Files.isRegularFile(file)) return BytecodeModel.fromFile(file);}
                else try(ZipFile zip=openZip(source)) {
                    var entry=zip.getEntry(PackLayout.findArchiveRoot(zip)+candidate);
                    if(entry==null) entry=zip.stream().filter(e -> !e.isDirectory()&&e.getName().endsWith(candidate)).findFirst().orElse(null);
                    if(entry!=null) try(var stream=zip.getInputStream(entry)) {return BytecodeModel.fromStream(stream);}
                }
            }
        }
        throw new AssertionError("Legacy model not found: "+ref);
    }
    static String geometryDigest(BakedRig rig) throws Exception {
        var digest=java.security.MessageDigest.getInstance("SHA-256");
        var memory=java.nio.ByteBuffer.allocate(4);
        for(var quad:rig.quads) {
            for(var point:quad.positions()) for(float value:point) {memory.clear();memory.putFloat(value);digest.update(memory.array());}
            for(var uv:quad.uvs()) for(float value:uv) {memory.clear();memory.putFloat(value);digest.update(memory.array());}
            for(float value:new float[]{quad.nx(),quad.ny(),quad.nz()}) {memory.clear();memory.putFloat(value);digest.update(memory.array());}
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
