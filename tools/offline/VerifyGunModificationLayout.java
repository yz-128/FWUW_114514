import com.flansmod.client.gui.GunModificationLayout;
import com.flansmod.client.gui.GunModificationLayout.Anchor;
import com.flansmod.client.gui.GunModificationLayout.Rect;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Responsive screen geometry and dense projected-socket placement, without Minecraft or OpenGL. */
public final class VerifyGunModificationLayout {
    private static int checks;
    private static void check(boolean ok, String message) {
        checks++;
        if(!ok) throw new AssertionError(message);
    }
    private static void inside(Rect inner, Rect outer, String message) {
        check(inner.w() > 0 && inner.h() > 0 && inner.x() >= outer.x() && inner.y() >= outer.y()
                && inner.right() <= outer.right() && inner.bottom() <= outer.bottom(), message + ": " + inner);
    }
    private static boolean overlaps(Rect a, Rect b) {
        return a.x() < b.right() && a.right() > b.x() && a.y() < b.bottom() && a.bottom() > b.y();
    }
    public static void main(String[] args) {
        var random = new Random(1101);
        for(int[] size : new int[][] {{320,240},{480,270},{640,360},{960,540},{1280,720}}) {
            var layout = GunModificationLayout.of(size[0], size[1]);
            Rect screen = new Rect(0, 0, size[0], size[1]);
            for(Rect region : List.of(layout.header(), layout.footer(), layout.structure(), layout.inventory(),
                    layout.viewport(), layout.previewModel(), layout.treeArea(), layout.candidateArea(),
                    layout.search(), layout.stats(), layout.actions(), layout.context(), layout.status(), layout.hint()))
                inside(region, screen, "region within " + size[0] + "x" + size[1]);
            inside(layout.treeArea(), layout.structure(), "tree clipped within structure");
            inside(layout.candidateArea(), layout.inventory(), "cards clipped within inventory");
            inside(layout.search(), layout.inventory(), "search within inventory");
            inside(layout.previewModel(), layout.viewport(), "model within viewport");
            check(!overlaps(layout.structure(), layout.viewport()), "sidebar and model cannot overlap");
            check(!overlaps(layout.actions(), layout.stats()), "actions and stats cannot overlap");
            if(!layout.compact()) check(!overlaps(layout.inventory(), layout.viewport()), "inventory and model cannot overlap");
            Rect view = layout.viewport();
            for(int round = 0; round < 100; round++) {
                var anchors = new ArrayList<Anchor>();
                for(int i = 0; i < 40; i++) anchors.add(new Anchor(i,
                        view.x() + 1 + random.nextFloat() * (view.w() - 2),
                        view.y() + 1 + random.nextFloat() * (view.h() - 2)));
                var copy = List.copyOf(anchors);
                var pins = GunModificationLayout.pins(view, anchors);
                check(anchors.equals(copy), "placement cannot reorder the caller's slot identity list");
                check(pins.size() <= 10, "bounded callout count");
                check(pins.equals(GunModificationLayout.pins(view, anchors)), "deterministic placement");
                for(int i = 0; i < pins.size(); i++) {
                    var pin = pins.get(i);
                    inside(pin.card(), view, "card inside viewport");
                    check(!overlaps(pin.card(), layout.hint()), "card avoids rotation hint");
                    check(!overlaps(pin.card(), layout.previewModel()), "card avoids default framed model");
                    var original = anchors.stream().filter(anchor -> anchor.id() == pin.id()).findFirst().orElseThrow();
                    check(pin.anchorX() == original.x() && pin.anchorY() == original.y(), "connector preserves projected socket");
                    check(pin.connectorX() >= pin.card().x() && pin.connectorX() <= pin.card().right(), "connector meets card");
                    for(int j = 0; j < i; j++) check(!overlaps(pin.card(), pins.get(j).card()), "dense socket cards cannot overlap");
                }
            }
            var invalid = List.of(new Anchor(1, Float.NaN, view.y()), new Anchor(2, view.x() - 1, view.y()),
                    new Anchor(3, view.x() + 30, view.y() + 50), new Anchor(3, view.x() + 35, view.y() + 50));
            check(GunModificationLayout.pins(view, invalid).size() <= 1, "invalid coordinates and duplicate slots are filtered");
        }
        Rect r = new Rect(3, 5, 10, 20);
        check(r.contains(3, 5) && !r.contains(13, 5) && !r.contains(3, 25), "hit bounds match GUI half-open rectangles");
        check(r.inset(100).w() == 0 && r.inset(100).h() == 0, "large insets stay nonnegative");
        System.out.println("PASS: " + checks + " checks; 5 GUI resolutions, dense sockets, identity, non-overlap and hit bounds. No game launched.");
    }
}
