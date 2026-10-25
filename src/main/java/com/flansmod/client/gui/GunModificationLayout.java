package com.flansmod.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Screen-independent geometry shared by the live gunsmith and the offline layout preview. */
public final class GunModificationLayout {
    private GunModificationLayout() { }

    public record Rect(int x, int y, int w, int h) {
        public Rect {
            if(w < 0 || h < 0) throw new IllegalArgumentException("Negative rectangle size");
        }
        public int right() { return x + w; }
        public int bottom() { return y + h; }
        public boolean contains(double mx, double my) {
            return mx >= x && mx < right() && my >= y && my < bottom();
        }
        public Rect inset(int amount) {
            int horizontal = Math.min(Math.max(0, amount), w / 2);
            int vertical = Math.min(Math.max(0, amount), h / 2);
            return new Rect(x + horizontal, y + vertical, w - horizontal * 2, h - vertical * 2);
        }
    }

    public record Layout(int width, int height, boolean compact, Rect header, Rect footer,
                         Rect structure, Rect inventory, Rect viewport, Rect previewModel,
                         Rect treeArea, Rect candidateArea, Rect search, Rect stats, Rect actions,
                         Rect context, Rect status, Rect hint) { }

    /** Coordinates are absolute GUI coordinates after projecting the actual attachment socket. */
    public record Anchor(int id, float x, float y) { }

    /** Connector ends at the card's inner edge; the live screen draws its own elbow and glyph. */
    public record Pin(int id, float anchorX, float anchorY, Rect card,
                      float connectorX, float connectorY, boolean above) { }

    public static Layout of(int width, int height) {
        if(width < 320 || height < 240)
            throw new IllegalArgumentException("Gunsmith requires at least 320 by 240 GUI pixels");
        boolean compact = width < 600;
        int margin = compact ? 7 : 10, gap = 8;
        int bodyTop = 43, bodyBottom = height - 91;
        int bodyHeight = Math.max(42, bodyBottom - bodyTop);
        Rect structure, inventory, viewport;
        if(compact) {
            int side = Math.min(134, Math.max(96, width * 31 / 100));
            structure = inventory = new Rect(margin, bodyTop, side, bodyHeight);
            viewport = new Rect(structure.right() + gap, bodyTop,
                    width - margin - structure.right() - gap, bodyHeight);
        } else {
            int left = Math.min(188, Math.max(132, width * 20 / 100));
            int right = Math.min(224, Math.max(152, width * 23 / 100));
            structure = new Rect(margin, bodyTop, left, bodyHeight);
            inventory = new Rect(width - margin - right, bodyTop, right, bodyHeight);
            viewport = new Rect(structure.right() + gap, bodyTop,
                    inventory.x - structure.right() - gap * 2, bodyHeight);
        }
        int treeTop = structure.y + (compact ? 29 : 26);
        Rect treeArea = new Rect(structure.x + 4, treeTop, structure.w - 8,
                Math.max(1, structure.bottom() - treeTop - 5));
        int searchTop = inventory.y + (compact ? 52 : 49);
        Rect search = new Rect(inventory.x + 8, searchTop, inventory.w - 16, 15);
        int cardsTop = search.bottom() + 7;
        Rect candidateArea = new Rect(inventory.x + 6, cardsTop, inventory.w - 12,
                Math.max(1, inventory.bottom() - cardsTop - 5));
        int topReserve = 58;
        int bottomReserve = 52;
        // Small GUI scales retain a useful model area by hiding the callout strips.
        if(viewport.w < 180 || viewport.h < 160) { topReserve = 25; bottomReserve = 18; }
        Rect model = new Rect(viewport.x + 6, viewport.y + topReserve, viewport.w - 12,
                Math.max(1, viewport.h - topReserve - bottomReserve));
        Rect hint = new Rect(viewport.x + 7, viewport.bottom() - 15, viewport.w - 14, 11);
        Rect footer = new Rect(margin, height - 87, width - margin * 2, 83);
        return new Layout(width, height, compact,
                new Rect(margin, 7, width - margin * 2, 29), footer,
                structure, inventory, viewport, model, treeArea, candidateArea, search,
                new Rect(margin, height - 45, width - margin * 2, 27),
                new Rect(margin, height - 72, width - margin * 2, 22),
                new Rect(margin, height - 87, width - margin * 2, 11),
                new Rect(margin, height - 13, width - margin * 2, 10), hint);
    }

    /**
     * Place a stable pair of callout rows around a projected model. Cards never overlap and never
     * enter the viewport title or drag hint. The caller can prioritize anchors before passing them.
     * The returned list is immutable; duplicate ids and non-finite/offscreen anchors are discarded.
     */
    public static List<Pin> pins(Rect viewport, List<Anchor> anchors) {
        if(viewport.w < 180 || viewport.h < 160 || anchors.isEmpty()) return List.of();
        int inset = 8, gap = 5, cardWidth = Math.min(64, (viewport.w - inset * 2 - gap) / 2);
        int cardHeight = 28;
        int columns = Math.max(1, Math.min(5, (viewport.w - inset * 2 + gap) / (cardWidth + gap)));
        int maxPins = columns * 2;
        List<Anchor> accepted = new ArrayList<>(maxPins);
        for(Anchor anchor : anchors) {
            if(accepted.size() == maxPins) break;
            if(!Float.isFinite(anchor.x) || !Float.isFinite(anchor.y)
                    || !viewport.contains(anchor.x, anchor.y)
                    || accepted.stream().anyMatch(existing -> existing.id == anchor.id)) continue;
            accepted.add(anchor);
        }
        // Balanced rows avoid hiding a crowded collection of sockets above one another.
        accepted.sort(Comparator.comparingDouble(Anchor::y).thenComparingDouble(Anchor::x)
                .thenComparingInt(Anchor::id));
        int upperCount = Math.min(columns, (accepted.size() + 1) / 2);
        List<Pin> result = new ArrayList<>(accepted.size());
        placeRow(result, accepted.subList(0, upperCount), viewport, cardWidth, cardHeight, gap, true);
        placeRow(result, accepted.subList(upperCount, accepted.size()), viewport, cardWidth, cardHeight, gap, false);
        return List.copyOf(result);
    }

    private static void placeRow(List<Pin> result, List<Anchor> row, Rect viewport,
                                 int width, int height, int gap, boolean above) {
        if(row.isEmpty()) return;
        List<Anchor> sorted = new ArrayList<>(row);
        sorted.sort(Comparator.comparingDouble(Anchor::x).thenComparingInt(Anchor::id));
        int left = viewport.x + 8, right = viewport.right() - 8;
        int y = above ? viewport.y + 26 : viewport.bottom() - 20 - height;
        int lastRight = left - gap;
        for(int i = 0; i < sorted.size(); i++) {
            Anchor anchor = sorted.get(i);
            int remaining = sorted.size() - i - 1;
            int maxX = right - width - remaining * (width + gap);
            int desiredX = Math.round(anchor.x - width / 2F);
            int x = Math.max(lastRight + gap, Math.min(maxX, desiredX));
            Rect card = new Rect(x, y, width, height);
            result.add(new Pin(anchor.id, anchor.x, anchor.y, card,
                    Math.max(x + 5, Math.min(x + width - 5, anchor.x)), above ? card.bottom() : card.y,
                    above));
            lastRight = card.right();
        }
    }
}
