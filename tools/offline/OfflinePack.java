import com.flansmod.common.FlansMod;
import com.flansmod.common.TextDecoding;
import com.flansmod.common.types.*;
import net.minecraft.resources.ResourceLocation;
import java.nio.file.*;
import java.util.*;

/** Filesystem resource probe and the game's definition readers, without booting Minecraft. */
public final class OfflinePack {
    final Path root;
    final Map<String,Path> files = new TreeMap<>();
    final List<InfoType> types = new ArrayList<>();
    OfflinePack(Path root) throws Exception {
        this.root=root.toAbsolutePath();
        try(var walk=Files.walk(root)) {
            for(Path p:walk.filter(Files::isRegularFile).toList())
                files.put(root.relativize(p).toString().replace('\\','/').toLowerCase(Locale.ROOT), p);
        }
    }
    boolean has(ResourceLocation id) { return path(id)!=null; }
    Path path(ResourceLocation id) { return id==null?null:files.get("assets/"+id.getNamespace()+"/"+id.getPath()); }
    void readTypes() throws Exception {
        FlansMod.contentPacks.put(root.getFileName().toString(),root);
        for(EnumType kind:EnumType.values()) {
            Path dir=root.resolve(kind.folderName);
            if(!Files.isDirectory(dir)) continue;
            try(var stream=Files.list(dir)) {
                for(Path file:stream.filter(Files::isRegularFile).sorted().toList()) {
                    String n=file.getFileName().toString();
                    if(!n.toLowerCase(Locale.ROOT).matches(".*\\.(txt|df)")) continue;
                    TypeFile tf=new TypeFile(kind,n.substring(0,n.lastIndexOf('.')));
                    tf.contentPack=root.getFileName().toString();
                    tf.lines.addAll(TextDecoding.readLines(file));
                    InfoType type=kind.getTypeClass().getConstructor(TypeFile.class).newInstance(tf);
                    type.read(tf);
                    types.add(type);
                }
            }
        }
    }
}
