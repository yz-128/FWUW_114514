import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.common.types.*;
import org.joml.Matrix4f;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** Same mesh, texture, camera and scale; only the slide/sight mount height changes in memory. */
public final class CompareBocchiSlideHeight {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectories(out);
        var pack=new OfflinePack(root);pack.readTypes();
        var gun=(GunType)pack.types.stream().filter(t->"BocchiTheGlock18C".equals(t.shortName)).findFirst().orElseThrow();
        float[] levels={18.5F,18F,17.5F};
        var result=new BufferedImage(1800,460,BufferedImage.TYPE_INT_RGB);
        var graphics=result.createGraphics();graphics.setColor(new Color(0x12202F));graphics.fillRect(0,0,1800,460);
        graphics.setFont(new Font("Microsoft YaHei",Font.PLAIN,18));
        for(int i=0;i<levels.length;i++) {
            float y=levels[i];gun.slots[0].y=y*gun.modelScale/16F;gun.slots[1].y=(y-17.5F)*gun.modelScale/16F;
            var parts=new ArrayList<FPPreview.Part>();
            parts.add(new FPPreview.Part(new Matrix4f(),ModelCache.rigFor(gun,gun.modelScale),FPPreview.skin(root,gun.texture),-1));
            for(var group:gun.defaultAttachments)FPPreview.addNodes(parts,root,gun,group,new Matrix4f(),0);
            var image=RenderGunModificationLayout.renderMesh(parts,600,400);
            ImageIO.write(image,"png",out.resolve("slide-y-"+y+".png").toFile());
            graphics.drawImage(image,i*600,46,null);graphics.setColor(new Color(0x5DE0C2));
            graphics.drawString((i==0?"当前上移 +1 像素":i==1?"上移 +0.5 像素":"原包挂点（保留读取器修复）")+" / Y="+y,i*600+18,29);
        }
        graphics.setFont(new Font("Microsoft YaHei",Font.PLAIN,12));graphics.setColor(new Color(0x849BAE));
        graphics.drawString("离线真实模型比较：仅内存改动高度，未写入内容包；三图同一视角。",18,451);
        graphics.dispose();ImageIO.write(result,"png",out.resolve("slide-height-three-way.png").toFile());
    }
}
