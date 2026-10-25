package com.flansmod.client.gui;

import com.flansmod.common.GunAttachments;
import com.flansmod.common.GunAttachmentCatalog;
import com.flansmod.common.GunStats;
import com.flansmod.common.inventory.GunModificationMenu;
import com.flansmod.common.network.GunModificationPayload;
import com.flansmod.common.types.AttachmentType;
import com.flansmod.common.types.GunType;
import com.flansmod.common.types.InfoType;
import com.flansmod.common.types.ModifiableType;
import com.flansmod.common.types.ModifiableType.DefaultAttachment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

/** A server-owned gunsmith menu with a responsive assembly tree and an actual 3D preview. */
public final class GunModificationScreen extends AbstractContainerScreen<GunModificationMenu> {
    private static final int BG = 0xFC151614, PANEL = 0xFF1D1F1B, BORDER = 0xFF45463D;
    private static final int TEXT = 0xFFDAD5C5, MUTED = 0xFF939184, ACCENT = 0xFFB7AD8D;
    private static final int GOOD = 0xFFA5B18A, BAD = 0xFFBE8272;
    private static final int ROW = 24;
    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
    private record MountRow(int[] parent, int slot, int[] path, int depth, ModifiableType owner,
                            DefaultAttachment node) { }
    private record CandidateId(AttachmentType type, int sourceSlot) { }
    private record Candidate(CandidateId id, int inventorySlot, ItemStack stack, AttachmentType type, int owned) { }
    private record Action(Rect bounds, Component label, boolean enabled, Runnable run, int[] railPath) {
        private Action(Rect bounds, Component label, boolean enabled, Runnable run) { this(bounds, label, enabled, run, null); }
    }
    private record RailDrag(int[] path, GunModificationRailDrag.Axis axis, double mouseX, double mouseY,
                            int revision, int offset) { }
    private final GunModificationPreview preview = new GunModificationPreview();
    private final List<MountRow> rows = new ArrayList<>();
    private final List<Candidate> candidates = new ArrayList<>();
    private final List<AttachmentType> incompatibleInventory = new ArrayList<>();
    private final List<Action> actions = new ArrayList<>();
    private final GunAttachmentCatalog catalog = new GunAttachmentCatalog(AttachmentType.attachments);
    private final Map<AttachmentType, ItemStack> catalogIcons = new HashMap<>();
    private GunModificationLayout.Layout layout;
    private List<List<DefaultAttachment>> tree = List.of();
    private List<List<DefaultAttachment>> proposed = List.of();
    private Rect structure, inventoryPanel, viewport, treeArea, candidateArea;
    private EditBox search;
    private int[] selectedParent = new int[0], selectedPath = new int[0];
    private int selectedSlot, step, offset, treeScroll, candidateScroll;
    private CandidateId selectedId;
    private int[] replacementPath = new int[0];
    private int observedRevision = -1, observedResponse = -1, pendingTicks;
    private int requestedOperation = -1;
    private boolean compact, inventoryTab, dragging, pending, showCompatible;
    private boolean showMounts = true;
    private RailDrag railDrag;
    private int railStep;
    private String railError;
    private String searchValue = "";
    private Component tooltip, localStatus;
    private List<Component> candidateTooltip;
    private ItemStack tooltipStack = ItemStack.EMPTY;

    public GunModificationScreen(GunModificationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gunsmith.flansmod." + key, args);
    }

    private static Rect rect(GunModificationLayout.Rect r) { return new Rect(r.x(), r.y(), r.w(), r.h()); }

    @Override protected void init() {
        stopRailDrag();
        imageWidth = width; imageHeight = height;
        super.init(); leftPos = topPos = 0;
        layout = GunModificationLayout.of(width, height); compact = layout.compact();
        structure = rect(layout.structure()); inventoryPanel = rect(layout.inventory()); viewport = rect(layout.viewport());
        treeArea = rect(layout.treeArea()); candidateArea = rect(layout.candidateArea());
        var searchBounds = layout.search();
        search = new EditBox(font, searchBounds.x(), searchBounds.y(), searchBounds.w(), searchBounds.h(), tr("search"));
        search.setBordered(false); search.setTextColor(TEXT); search.setHint(tr("search"));
        search.setMaxLength(64); search.setValue(searchValue); search.setTextShadow(false);
        search.setResponder(value -> { searchValue = value; candidateScroll = 0; refreshCandidates(); });
        addWidget(search);
        refreshAssembly();
    }

    @Override protected void containerTick() {
        super.containerTick();
        if(observedRevision != menu.revision()) refreshAssembly();
        if(observedResponse != menu.response()) {
            observedResponse = menu.response(); pending = false; pendingTicks = 0; localStatus = null;
        }
        if(pending && ++pendingTicks > 80) {
            pending = false; localStatus = tr("waiting_retry");
        }
        refreshCandidates();
    }

    private void refreshAssembly() {
        if(railDrag != null) { stopRailDrag(); localStatus = tr("rail_drag_cancelled"); }
        observedRevision = menu.revision();
        var gun = menu.gunType();
        tree = gun == null ? List.of() : GunAttachments.read(gun, menu.gunStack());
        if(gun == null) return;
        if(pending && requestedOperation == GunModificationPayload.REMOVE) {
            selectedPath = replacementPath = new int[0]; selectedId = null; step = offset = 0;
        }
        if(GunAttachments.ownerAt(gun, tree, selectedParent) == null) {
            selectedParent = selectedPath = new int[0]; selectedSlot = 0; selectedId = null;
        }
        var currentOwner = GunAttachments.ownerAt(gun, tree, selectedParent);
        if(currentOwner != null && (selectedSlot < 0 || selectedSlot >= currentOwner.slots.length)) {
            selectedSlot = 0; selectedPath = new int[0]; selectedId = null;
        }
        if(selectedPath.length > 0 && GunAttachments.nodeAt(tree, selectedPath) == null) selectedPath = new int[0];
        var selected = selectedPath.length == 0 ? null : GunAttachments.nodeAt(tree, selectedPath);
        if(selected != null) { step = selected.step; offset = selected.offset; }
        rows.clear();
        for(var row : GunAttachmentCatalog.treeEntries(gun, tree))
            rows.add(new MountRow(row.parentPath(), row.slot(), row.nodePath(), row.depth(), row.owner(), row.node()));
        treeScroll = Math.max(0, Math.min(treeScroll, Math.max(0, rows.size() * ROW - treeArea.h)));
        refreshCandidates();
    }

    private void refreshCandidates() {
        candidates.clear(); incompatibleInventory.clear();
        var gun = menu.gunType();
        if(gun == null) return;
        String query = searchValue.toLowerCase(Locale.ROOT).strip();
        Map<AttachmentType, Integer> counts = new HashMap<>(), sources = new HashMap<>();
        for(int i = 0; i < Math.min(36, menu.slots.size()); i++) {
            var stack = menu.slots.get(i).getItem();
            if(stack.isEmpty() || !(InfoType.getType(stack) instanceof AttachmentType type)) continue;
            counts.merge(type, stack.getCount(), Integer::sum); sources.putIfAbsent(type, i);
            if(matches(stack, type, query)) {
                var mountOwner = owner();
                boolean usableMount = mountOwner != null && selectedSlot >= 0 && selectedSlot < mountOwner.slots.length
                        && GunAttachments.slotCapacity(mountOwner,selectedSlot) > 0;
                boolean fitsInterface = usableMount && GunAttachments.allowed(mountOwner,selectedSlot,type);
                if(!showCompatible && fitsInterface)
                    candidates.add(new Candidate(new CandidateId(type, i), i, stack, type, stack.getCount()));
                if(!fitsInterface && !incompatibleInventory.contains(type)) incompatibleInventory.add(type);
            }
        }
        if(showCompatible) for(var type : catalog.compatible(owner(), selectedSlot)) {
            if(type.item == null) continue;
            int source = sources.getOrDefault(type, -1);
            var stack = source >= 0 ? menu.slots.get(source).getItem()
                    : catalogIcons.computeIfAbsent(type, part -> new ItemStack(part.item));
            if(matches(stack, type, query))
                candidates.add(new Candidate(new CandidateId(type, -1), source, stack, type, counts.getOrDefault(type, 0)));
        }
        if(candidates.stream().noneMatch(candidate -> candidate.id.equals(selectedId))) selectedId = null;
        if(candidateArea != null) candidateScroll = Math.max(0, Math.min(candidateScroll, candidateMaxScroll()));
    }

    private static boolean matches(ItemStack stack, AttachmentType type, String query) {
        return query.isEmpty() || stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)
                || type.shortName.toLowerCase(Locale.ROOT).contains(query);
    }

    private Candidate selectedCandidate() {
        return candidates.stream().filter(candidate -> candidate.id.equals(selectedId)).findFirst().orElse(null);
    }

    private ModifiableType owner() { return GunAttachments.ownerAt(menu.gunType(), tree, selectedParent); }

    private int[] removalPath() {
        return GunAttachmentCatalog.selectedRemovalPath(tree, selectedParent, selectedSlot,
                selectedPath.length > 0 ? selectedPath : replacementPath);
    }
    private long mountedCount(int[] parent, int slot) {
        return GunAttachments.children(tree, parent).stream().filter(node -> node.slot == slot).count();
    }
    private Component mountLabel(ModifiableType owner, int slot, int[] parent) {
        if(owner == null || slot < 0 || slot >= owner.slots.length) return tr("select_slot");
        return Component.empty().append(slotLabel(owner, slot)).append("  ")
                .append(tr("mount_occupancy", mountedCount(parent, slot), GunAttachments.slotCapacity(owner,slot)));
    }
    private Component mountContext(ModifiableType owner, int slot, int[] parent) {
        Component name = owner instanceof AttachmentType part ? partName(part) : menu.gunStack().getHoverName();
        return tr("mount_context", name, mountLabel(owner, slot, parent));
    }
    private Component fullHint() {
        var node = GunAttachments.nodeAt(tree, removalPath());
        if(node == null) return tr("select_existing_part");
        var part = AttachmentType.getAttachment(node.name);
        return tr("replace_requires_remove", part == null ? Component.literal(node.name) : partName(part));
    }

    private void makeProposal() {
        proposed = tree;
        if(railDrag != null) {
            proposed = GunModificationRailDrag.preview(menu.gunType(), tree, railDrag.path, railStep);
            return;
        }
        var candidate = selectedCandidate();
        if(candidate == null) return;
        var base = GunAttachmentCatalog.previewBase(menu.gunType(), tree, selectedParent, selectedSlot, replacementPath);
        if(GunAttachments.canInstallAssembly(menu.gunType(), base, selectedParent, selectedSlot, candidate.stack, step, offset) != null) return;
        proposed = base;
        var node = new DefaultAttachment();
        node.name = candidate.type.shortName; node.slot = selectedSlot; node.step = step; node.offset = offset;
        for(var group : GunAttachments.copy(candidate.type, GunAttachments.read(candidate.type, candidate.stack)))
            node.children.addAll(group);
        GunAttachments.position(owner(), node, candidate.type);
        GunAttachments.add(proposed, selectedParent, node);
    }

    private String installationError(Candidate candidate) {
        return GunAttachments.canInstallAssembly(menu.gunType(), tree, selectedParent, selectedSlot, candidate.stack, step, offset);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if(menu.gunType() == null || search == null) return;
        tooltip = null; candidateTooltip = null; tooltipStack = ItemStack.EMPTY; actions.clear();
        graphics.fill(0, 0, width, height, BG);
        graphics.fill(0, 0, width, 35, 0xFF10110F);
        graphics.fill(10, 35, width - 10, 36, BORDER);
        graphics.drawString(font, tr("workbench"), 12, 8, MUTED, false);
        graphics.drawString(font, tr("title"), 12 + font.width(tr("workbench")) + 12, 8, ACCENT, false);
        text(graphics, menu.gunStack().getHoverName(), 12, 26, width - 92, TEXT);
        addAction(graphics, new Rect(width - 62, 9, 50, 20), tr("close"), true, this::onClose, mouseX, mouseY);
        makeProposal();
        if(!compact || !inventoryTab) drawStructure(graphics, mouseX, mouseY);
        if(!compact || inventoryTab) drawInventory(graphics, mouseX, mouseY, partialTick);
        search.setVisible(!compact || inventoryTab);
        if(!search.isVisible()) search.setFocused(false);
        drawPreview(graphics, mouseX, mouseY);
        drawActions(graphics, mouseX, mouseY);
        drawStats(graphics);
        var status = railDrag != null ? railError == null ? tr("rail_dragging", railStep, railDrag.axis.maxStep())
                : tr("error." + railError) : localStatus != null ? localStatus : pending ? tr("waiting") : menu.statusMessage();
        if(railDrag == null && localStatus == null && !pending && menu.status() == 0) {
            var removable = GunAttachments.nodeAt(tree, removalPath());
            if(removable != null) {
                var type = AttachmentType.getAttachment(removable.name);
                status = tr("remove_assembly_hint", type == null ? Component.literal(removable.name) : partName(type),
                        GunAttachments.count(List.of(List.of(removable))));
                var selectedOwner = owner();
                if(selectedOwner != null && removable.slot >= 0 && removable.slot < selectedOwner.slots.length
                        && selectedOwner.slots[removable.slot].step > 0)
                    status = Component.empty().append(status).append(" · ").append(tr("rail_drag_hint"));
            }
            else if(mountedCount(selectedParent, selectedSlot) > 1) status = tr("select_existing_part");
        }
        if(status == null || status.getString().isBlank()) status = tr("help");
        text(graphics, status, 12, height - 12, width - 24, railError != null || menu.status() != 0 ? BAD : MUTED);
        if(new Rect(10, height - 15, width - 20, 14).contains(mouseX, mouseY)) tooltip = status;
        if(tooltip != null) graphics.renderTooltip(font, tooltip, mouseX, mouseY);
        if(candidateTooltip != null) graphics.renderComponentTooltip(font, candidateTooltip, mouseX, mouseY);
        if(!tooltipStack.isEmpty()) graphics.renderTooltip(font, tooltipStack, mouseX, mouseY);
    }

    private void panel(GuiGraphics graphics, Rect bounds) {
        graphics.fill(bounds.x, bounds.y, bounds.x + bounds.w, bounds.y + bounds.h, PANEL);
        graphics.renderOutline(bounds.x, bounds.y, bounds.w, bounds.h, BORDER);
        graphics.fill(bounds.x + 1, bounds.y + 23, bounds.x + bounds.w - 1, bounds.y + 24, 0xFF33362D);
    }

    private void sidebarHeader(GuiGraphics graphics, boolean showingInventory, int mouseX, int mouseY) {
        var area = showingInventory ? inventoryPanel : structure;
        if(compact) {
            int half = (area.w - 12) / 2;
            addAction(graphics, new Rect(area.x + 6, area.y + 5, half, 19), tr("structure"), true,
                    () -> { inventoryTab = false; search.setFocused(false); }, mouseX, mouseY, !inventoryTab);
            addAction(graphics, new Rect(area.x + 6 + half, area.y + 5, half, 19), tr("inventory"), true,
                    () -> inventoryTab = true, mouseX, mouseY, inventoryTab);
        } else {
            var label = showingInventory && owner() != null ? tr("catalog_mount", mountContext(owner(), selectedSlot, selectedParent))
                    : tr(showingInventory ? "inventory" : "structure");
            text(graphics, label, area.x + 10, area.y + 11, area.w - 20, TEXT);
            if(showingInventory && new Rect(area.x, area.y, area.w, 25).contains(mouseX, mouseY)) tooltip = label;
        }
    }

    private void drawStructure(GuiGraphics graphics, int mouseX, int mouseY) {
        panel(graphics, structure); sidebarHeader(graphics, false, mouseX, mouseY);
        graphics.enableScissor(treeArea.x, treeArea.y, treeArea.x + treeArea.w, treeArea.y + treeArea.h);
        for(int i = 0; i < rows.size(); i++) {
            var row = rows.get(i); int y = treeArea.y + i * ROW - treeScroll;
            if(y + ROW < treeArea.y || y > treeArea.y + treeArea.h) continue;
            boolean selected = row.node == null ? selectedPath.length == 0 && selectedSlot == row.slot
                    && Arrays.equals(selectedParent, row.parent) : Arrays.equals(selectedPath, row.path);
            var bounds = new Rect(treeArea.x + 2, y, treeArea.w - 6, ROW - 2);
            boolean hovered = bounds.contains(mouseX, mouseY) && treeArea.contains(mouseX, mouseY);
            if(selected || hovered) graphics.fill(bounds.x, y, bounds.x + bounds.w, y + ROW - 2, selected ? 0xFF3A3B30 : 0xFF292C25);
            if(selected) graphics.fill(bounds.x, y + 3, bounds.x + 2, y + ROW - 5, ACCENT);
            int indent = Math.min(row.depth * 7, Math.max(0, bounds.w - 64));
            int x = bounds.x + 7 + indent;
            if(row.depth > 0) graphics.fill(x - 4, y, x - 3, y + ROW - 2, 0xFF4A4D40);
            Component label;
            if(row.node == null) {
                graphics.drawString(font, "+", x, y + 6, ACCENT, false);
                label = mountLabel(row.owner, row.slot, row.parent);
                text(graphics, label, x + 11, y + 6, bounds.x + bounds.w - x - 15, MUTED);
            } else {
                var type = AttachmentType.getAttachment(row.node.name);
                if(type != null && type.item != null) graphics.renderFakeItem(new ItemStack(type.item), x, y + 2);
                label = type == null ? Component.literal(row.node.name) : partName(type);
                text(graphics, label, x + 19, y + 6, bounds.x + bounds.w - x - 23, selected ? ACCENT : TEXT);
            }
            if(hovered) tooltip = row.node == null ? mountContext(row.owner, row.slot, row.parent) : label;
        }
        graphics.disableScissor();
        scrollBar(graphics, treeArea, treeScroll, Math.max(0, rows.size() * ROW - treeArea.h));
        if(rows.isEmpty()) wrapped(graphics, tr("no_slots"), treeArea, MUTED);
    }

    private Component slotLabel(ModifiableType type, int slot) {
        var tags = slot >= 0 && slot < type.typeWhitelist.size() ? type.typeWhitelist.get(slot) : List.<String>of();
        var directions = Component.empty(); var categories = Component.empty();
        for(String category : tags) {
            String tag = category.toLowerCase(Locale.ROOT), key = "gunsmith.flansmod.kind." + tag;
            var label = I18n.exists(key) ? Component.translatable(key) : Component.literal(prettyKind(category));
            boolean direction = tag.equals("left") || tag.equals("right") || tag.equals("upper") || tag.equals("under")
                    || tag.equals("front") || tag.equals("rear");
            var destination = direction ? directions : categories;
            if(!destination.getString().isBlank()) destination.append(" / "); destination.append(label);
        }
        var detail = Component.empty();
        if(!directions.getString().isBlank()) detail.append(directions);
        if(!categories.getString().isBlank()) {
            if(!detail.getString().isBlank()) detail.append(" · "); detail.append(categories);
        }
        return detail.getString().isBlank() ? tr("slot", slot + 1) : tr("named_slot", slot + 1, detail);
    }

    /**
     * 1.7.10 printed the pack's raw category tag when it had no translation
     * ({@code GuiModernGunsmith}: {@code canTranslate ? I18n.format(key) : kind}). Keeping that
     * behaviour and only splitting the tag into words keeps the fallback readable:
     * "GenericMuzzle" becomes "Generic Muzzle" instead of one long clipped run.
     */
    private static String prettyKind(String category) {
        String text = category.replace('_', ' ');
        text = text.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ").replaceAll("(?<=[A-Z])(?=[A-Z][a-z])", " ");
        StringBuilder out = new StringBuilder(text.length());
        boolean start = true;
        for(int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(start ? Character.toUpperCase(c) : c);
            start = c == ' ';
        }
        return out.toString();
    }

    private int columns() { return Math.max(1, (candidateArea.w + 5) / 52); }
    private int cardHeight() { return 52; }
    private int candidateMaxScroll() { return Math.max(0, ((candidates.size() + columns() - 1) / columns()) * cardHeight() - candidateArea.h); }

    private void drawInventory(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        panel(graphics, inventoryPanel); sidebarHeader(graphics, true, mouseX, mouseY);
        var toggle = new Rect(inventoryPanel.x + 7, inventoryPanel.y + (compact ? 31 : 27), inventoryPanel.w - 14, 15);
        actions.add(new Action(toggle, tr("catalog_toggle"), true, () -> {
            showCompatible = !showCompatible; selectedId = null; replacementPath = new int[0];
            step = offset = candidateScroll = 0; localStatus = null; refreshCandidates();
        }));
        graphics.renderOutline(toggle.x, toggle.y + 1, 11, 11, showCompatible ? ACCENT : MUTED);
        if(showCompatible) graphics.drawString(font, "✓", toggle.x + 2, toggle.y + 2, ACCENT, false);
        text(graphics, tr("catalog_toggle"), toggle.x + 16, toggle.y + 3, toggle.w - 17, showCompatible ? ACCENT : TEXT);
        if(toggle.contains(mouseX, mouseY)) tooltip = tr("catalog_help");
        var searchBounds = layout.search();
        graphics.fill(searchBounds.x() - 2, searchBounds.y() - 3, searchBounds.right() + 2, searchBounds.bottom() + 2, 0xFF10120F);
        graphics.fill(searchBounds.x() - 2, searchBounds.bottom() + 2, searchBounds.right() + 2, searchBounds.bottom() + 3, BORDER);
        search.setVisible(true); search.render(graphics, mouseX, mouseY, partialTick);
        int cols = columns(), cardWidth = (candidateArea.w - 5 * (cols - 1)) / cols;
        graphics.enableScissor(candidateArea.x, candidateArea.y, candidateArea.x + candidateArea.w, candidateArea.y + candidateArea.h);
        for(int i = 0; i < candidates.size(); i++) {
            var candidate = candidates.get(i);
            int cardHeight = cardHeight();
            int x = candidateArea.x + (i % cols) * (cardWidth + 5), y = candidateArea.y + (i / cols) * cardHeight - candidateScroll;
            if(y + cardHeight < candidateArea.y || y > candidateArea.y + candidateArea.h) continue;
            var bounds = new Rect(x, y, cardWidth, cardHeight - 4);
            boolean hovered = bounds.contains(mouseX, mouseY) && candidateArea.contains(mouseX, mouseY);
            boolean selected = candidate.id.equals(selectedId);
            graphics.fill(x, y, x + cardWidth, y + cardHeight - 4, selected ? 0xFF464939 : hovered ? 0xFF34372D : 0xFF242820);
            graphics.renderOutline(x, y, cardWidth, cardHeight - 4, selected ? ACCENT : BORDER);
            graphics.fill(x + 1, y + cardHeight - 18, x + cardWidth - 1, y + cardHeight - 17, 0xFF3B4033);
            graphics.pose().pushPose();
            graphics.pose().translate(x + (cardWidth - 22) / 2F, y + 5, 0); graphics.pose().scale(1.375F, 1.375F, 1);
            graphics.renderFakeItem(candidate.stack, 0, 0); graphics.pose().popPose();
            text(graphics, candidate.stack.getHoverName(), x + 4, y + 36, cardWidth - 8,
                    selected ? ACCENT : candidate.owned > 0 ? TEXT : MUTED);
            String quantity = candidate.owned > 0 ? Integer.toString(candidate.owned) : "—";
            graphics.drawString(font, quantity, x + cardWidth - 3 - font.width(quantity), y + 3,
                    candidate.owned > 0 ? GOOD : BAD, false);
            if(candidate.owned == 0) graphics.fill(x + 1, y + 1, x + 3, y + cardHeight - 5, 0xFF76554C);
            if(hovered) {
                var details = new ArrayList<Component>();
                details.add(candidate.stack.getHoverName()); details.add(candidateState(candidate));
                details.add(tr("catalog_mount", mountContext(owner(), selectedSlot, selectedParent)));
                details.add(tr("part_interface", candidate.type.attachmentType));
                details.add(parentHint(candidate.type));
                if(candidate.owned == 0) details.add(tr("catalog_preview_only"));
                var error = installationError(candidate);
                if(error != null) details.add("full".equals(error) ? fullHint() : tr("error." + error));
                candidateTooltip = details;
            }
        }
        graphics.disableScissor();
        scrollBar(graphics, candidateArea, candidateScroll, candidateMaxScroll());
        if(candidates.isEmpty()) {
            var owner = owner();
            boolean full = owner != null && selectedSlot >= 0 && selectedSlot < owner.slots.length
                    && GunAttachments.children(tree, selectedParent).stream().filter(node -> node.slot == selectedSlot).count()
                    >= GunAttachments.slotCapacity(owner,selectedSlot);
            Component empty = full ? fullHint() : tr(showCompatible ? "catalog_empty" : "no_parts");
            if(!incompatibleInventory.isEmpty()) {
                var first = incompatibleInventory.getFirst();
                var parentText = Component.empty().append(tr("part_needs_parent", partName(first))).append("\n").append(parentHint(first));
                empty = full ? Component.empty().append(empty).append("\n").append(parentText) : parentText;
            }
            wrapped(graphics, empty, candidateArea, MUTED);
            if(candidateArea.contains(mouseX, mouseY)) tooltip = empty;
        }
    }

    private Component parentHint(AttachmentType child) {
        var parents = catalog.parents(child).stream().map(GunAttachmentCatalog.ParentMount::parent).distinct().limit(4).toList();
        if(parents.isEmpty()) return tr("direct_parents_none");
        var names = Component.empty();
        for(int i = 0; i < Math.min(3, parents.size()); i++) {
            if(i > 0) names.append(" / "); names.append(partName(parents.get(i)));
        }
        if(parents.size() > 3) names.append("…");
        return tr("direct_parents", names);
    }

    private Component candidateState(Candidate candidate) {
        boolean full = "full".equals(installationError(candidate));
        return tr(candidate.owned > 0 ? full ? "catalog_owned_full" : "catalog_owned"
                : full ? "catalog_missing_full" : "catalog_missing", candidate.owned);
    }

    private void drawPreview(GuiGraphics graphics, int mouseX, int mouseY) {
        panel(graphics, viewport);
        graphics.fillGradient(viewport.x + 1, viewport.y + 1, viewport.x + viewport.w - 1, viewport.y + viewport.h - 1, 0xFF292B25, 0xFF131511);
        for(int gy = viewport.y + 26; gy < viewport.y + viewport.h - 16; gy += 4)
            graphics.fill(viewport.x + 1, gy, viewport.x + viewport.w - 1, gy + 1, 0x03202218);
        graphics.fill(viewport.x + 12, viewport.y + viewport.h / 2, viewport.x + viewport.w - 12, viewport.y + viewport.h / 2 + 1, 0x154F5144);
        int resetWidth = viewport.w > 280 ? 48 : 35;
        int mountsWidth = viewport.w > 280 ? 50 : 35;
        if(selectedCandidate() == null)
            text(graphics, owner() instanceof AttachmentType part ? partName(part) : tr("assembly"),
                    viewport.x + 9, viewport.y + 9, viewport.w - resetWidth - mountsWidth - 22, ACCENT);
        else addAction(graphics, new Rect(viewport.x + 6, viewport.y + 3, Math.max(34, Math.min(94, viewport.w - resetWidth - mountsWidth - 20)), 18),
                tr("cancel_preview"), true, () -> { selectedId = null; replacementPath = new int[0]; step = offset = 0; localStatus = null; }, mouseX, mouseY);
        addAction(graphics, new Rect(viewport.x + viewport.w - resetWidth - 5, viewport.y + 3, resetWidth, 18),
                tr("reset"), true, preview::reset, mouseX, mouseY);
        addAction(graphics, new Rect(viewport.x + viewport.w - resetWidth - mountsWidth - 8, viewport.y + 3, mountsWidth, 18),
                tr("mounts"), true, () -> showMounts = !showMounts, mouseX, mouseY, showMounts);
        var model = layout.previewModel();
        boolean loaded = preview.render(graphics, menu.gunType(), menu.gunStack(), proposed,
                selectedCandidate() == null ? removalPath() : selectedPath,
                model.x(), model.y(), model.w(), model.h());
        if(!loaded) text(graphics, tr("loading"), viewport.x + 12, viewport.y + viewport.h / 2, viewport.w - 24, MUTED);
        if(loaded && railDrag != null) drawRailGuide(graphics);
        if(loaded && showMounts) drawMountPins(graphics, mouseX, mouseY);
        var selected = GunAttachments.nodeAt(tree, selectedPath);
        var selectedType = selected == null ? null : AttachmentType.getAttachment(selected.name);
        if(selectedType != null && selectedType.slots.length > 0 && viewport.w > 250)
            addAction(graphics, new Rect(viewport.x + viewport.w - 71, viewport.y + viewport.h - 17, 65, 14),
                    tr("child_mounts"), true, () -> focusMounts(selectedPath), mouseX, mouseY);
        else if(selectedParent.length > 0 && viewport.w > 250)
            addAction(graphics, new Rect(viewport.x + viewport.w - 71, viewport.y + viewport.h - 17, 65, 14),
                    tr("receiver_mounts"), true, () -> focusMounts(new int[0]), mouseX, mouseY);
        text(graphics, tr(railDrag == null ? "preview_help" : "rail_drag_help"), viewport.x + 9, viewport.y + viewport.h - 13,
                viewport.w - (viewport.w > 250 && (selectedParent.length > 0 || selectedType != null && selectedType.slots.length > 0) ? 90 : 18), MUTED);
    }

    private void drawMountPins(GuiGraphics graphics, int mouseX, int mouseY) {
        var mounts = rows.stream().filter(row -> row.node == null && Arrays.equals(row.parent, selectedParent)).toList();
        var anchors = new ArrayList<GunModificationLayout.Anchor>();
        // Keep the selected mount visible even when a receiver has more sockets than label space.
        for(int pass = 0; pass < 2; pass++) for(int i = 0; i < mounts.size(); i++) {
            var row = mounts.get(i);
            if((row.slot == selectedSlot) != (pass == 0)) continue;
            var installed = GunAttachments.children(tree, row.parent).stream().filter(node -> node.slot == row.slot).toList();
            float rail = installed.size() == 1 ? installed.getFirst().pos : 0;
            if(railDrag != null && installed.size() == 1 && row.slot == selectedSlot && Arrays.equals(row.parent, selectedParent)) {
                var draft = GunAttachments.nodeAt(proposed, railDrag.path);
                if(draft != null) rail = draft.pos;
            }
            var point = preview.projectMount(menu.gunType(), tree, row.parent, row.slot, rail);
            if(point != null) anchors.add(new GunModificationLayout.Anchor(i, point.x, point.y));
        }
        var pins = GunModificationLayout.pins(layout.viewport(), anchors);
        graphics.pose().pushPose(); graphics.pose().translate(0, 0, 500);
        for(var pin : pins) {
            var row = mounts.get(pin.id()); var card = rect(pin.card());
            var installed = GunAttachments.children(tree, row.parent).stream().filter(node -> node.slot == row.slot).toList();
            var part = installed.isEmpty() ? null : AttachmentType.getAttachment(installed.getFirst().name);
            boolean selected = row.slot == selectedSlot && Arrays.equals(row.parent, selectedParent);
            boolean hovered = card.contains(mouseX, mouseY);
            int ink = selected || hovered ? ACCENT : 0xFF6F7560;
            int ax = Math.round(pin.anchorX()), ay = Math.round(pin.anchorY());
            int cx = Math.round(pin.connectorX()), cy = Math.round(pin.connectorY());
            int elbow = (ay + cy) / 2;
            graphics.fill(ax, Math.min(ay, elbow), ax + 1, Math.max(ay, elbow) + 1, ink);
            graphics.fill(Math.min(ax, cx), elbow, Math.max(ax, cx) + 1, elbow + 1, ink);
            graphics.fill(cx, Math.min(elbow, cy), cx + 1, Math.max(elbow, cy) + 1, ink);
            graphics.fill(ax - 2, ay - 2, ax + 3, ay + 3, ink);
            graphics.fill(card.x, card.y, card.x + card.w, card.y + card.h, selected ? 0xF14A4D3C : 0xF120241D);
            graphics.renderOutline(card.x, card.y, card.w, card.h, ink);
            if(part != null && part.item != null) graphics.renderFakeItem(new ItemStack(part.item), card.x + 4, card.y + 6);
            else graphics.drawString(font, "+", card.x + 8, card.y + 10, MUTED, false);
            text(graphics, slotLabel(row.owner, row.slot), card.x + 24, card.y + 4, card.w - 27, selected ? ACCENT : TEXT);
            Component content = installed.size() > 1 ? tr("parts_count", installed.size()) : part == null ? tr("empty_mount") : partName(part);
            text(graphics, content, card.x + 24, card.y + 15, card.w - 27, MUTED);
            int[] railPath = installed.size() == 1 ? GunAttachmentCatalog.selectedRemovalPath(tree, row.parent, row.slot, null) : null;
            actions.add(new Action(card, mountLabel(row.owner, row.slot, row.parent), true, () -> selectRow(row), railPath));
            if(hovered) {
                var details = Component.empty().append(mountContext(row.owner, row.slot, row.parent)).append("\n").append(content);
                if(railPath != null && row.owner.slots[row.slot].step > 0) details.append("\n").append(tr("rail_drag_hint"));
                tooltip = details;
            }
        }
        graphics.pose().popPose();
    }

    private void selectRow(MountRow row) {
        selectedParent = Arrays.copyOf(row.parent, row.parent.length); selectedSlot = row.slot;
        selectedPath = GunAttachmentCatalog.selectedRemovalPath(tree, row.parent, row.slot, row.path);
        var node = GunAttachments.nodeAt(tree, selectedPath);
        selectedId = null; replacementPath = new int[0]; step = node == null ? 0 : node.step; offset = node == null ? 0 : node.offset;
        candidateScroll = 0; localStatus = null; refreshCandidates();
        for(int i = 0; i < rows.size(); i++) {
            var other = rows.get(i);
            boolean same = selectedPath.length > 0 ? Arrays.equals(other.path, selectedPath)
                    : other.node == null && other.slot == selectedSlot && Arrays.equals(other.parent, selectedParent);
            if(same) {
                treeScroll = Math.max(0, Math.min(Math.max(0, rows.size() * ROW - treeArea.h), i * ROW - treeArea.h / 2));
                break;
            }
        }
    }

    private void focusMounts(int[] parent) {
        var owner = GunAttachments.ownerAt(menu.gunType(), tree, parent);
        if(owner == null || owner.slots.length == 0) return;
        for(var row : rows) if(row.node == null && Arrays.equals(row.parent, parent)) { selectRow(row); break; }
    }

    private boolean beginRailDrag(int[] path, double mouseX, double mouseY) {
        if(pending || selectedCandidate() != null || path == null || path.length == 0) return false;
        var axis = GunModificationRailDrag.axis(preview.frame(), menu.gunType(), tree, path);
        var node = GunAttachments.nodeAt(tree, path);
        if(axis == null || node == null) return false;
        railDrag = new RailDrag(Arrays.copyOf(path, path.length), axis, mouseX, mouseY, menu.revision(), node.offset);
        railStep = node.step; railError = null; dragging = false; localStatus = null;
        search.setFocused(false);
        preview.lockFrame(true);
        return true;
    }

    private void updateRailDrag(double mouseX, double mouseY) {
        if(railDrag == null) return;
        if(railDrag.revision != menu.revision()) { stopRailDrag(); localStatus = tr("rail_drag_cancelled"); return; }
        railStep = GunModificationRailDrag.stepFor(railDrag.axis, mouseX - railDrag.mouseX, mouseY - railDrag.mouseY);
        railError = GunModificationRailDrag.error(menu.gunType(), tree, railDrag.path, railStep);
    }

    private void stopRailDrag() {
        railDrag = null; railError = null; preview.lockFrame(false);
    }

    private void drawRailGuide(GuiGraphics graphics) {
        var node = GunAttachments.nodeAt(tree, railDrag.path);
        if(node == null) return;
        var parent = Arrays.copyOf(railDrag.path, railDrag.path.length - 2);
        var point = preview.projectMount(menu.gunType(), tree, parent, node.slot, node.pos);
        if(point == null) return;
        var axis = railDrag.axis;
        float startX = point.x - axis.initialStep() * axis.dx(), startY = point.y - axis.initialStep() * axis.dy();
        float dx = axis.dx() * axis.maxStep(), dy = axis.dy() * axis.maxStep();
        graphics.enableScissor(viewport.x + 1, viewport.y + 24, viewport.x + viewport.w - 1, viewport.y + viewport.h - 16);
        graphics.pose().pushPose(); graphics.pose().translate(0, 0, 500);
        int ink = railError == null ? ACCENT : BAD;
        graphics.pose().pushPose(); graphics.pose().translate(startX, startY, 0);
        graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotation((float)Math.atan2(dy, dx)));
        graphics.fill(0, 0, Math.max(1, (int)Math.ceil(Math.hypot(dx, dy))), 1, ink);
        graphics.pose().popPose();
        int ticks = Math.min(32, axis.maxStep());
        for(int i = 0; i <= ticks; i++) {
            float t = (float)i / Math.max(1, ticks);
            int x = Math.round(startX + dx * t), y = Math.round(startY + dy * t);
            graphics.fill(x - 1, y - 2, x + 2, y + 3, ink);
        }
        int x = Math.round(startX + axis.dx() * railStep), y = Math.round(startY + axis.dy() * railStep);
        graphics.renderOutline(x - 4, y - 4, 9, 9, ink);
        graphics.pose().popPose(); graphics.disableScissor();
    }

    private void drawActions(GuiGraphics graphics, int mouseX, int mouseY) {
        var owner = owner(); var candidate = selectedCandidate();
        var removeAt = removalPath();
        var selected = GunAttachments.nodeAt(tree, removeAt);
        Component selection = candidate != null ? candidate.stack.getHoverName()
                : selected != null && AttachmentType.getAttachment(selected.name) != null
                ? partName(AttachmentType.getAttachment(selected.name))
                : owner == null || selectedSlot >= owner.slots.length ? tr("select_slot") : mountLabel(owner, selectedSlot, selectedParent);
        if((candidate != null || selected != null) && owner != null)
            selection = tr("catalog_selection", mountContext(owner, selectedSlot, selectedParent), selection);
        var context = layout.context();
        text(graphics, selection, context.x() + 2, context.y() + 1, context.w() - 4, TEXT);
        int buttonWidth = compact ? 59 : Math.min(100, width / 7), y = layout.actions().y();
        String reason = candidate == null ? null : installationError(candidate);
        boolean canInstall = !pending && railDrag == null && candidate != null && candidate.inventorySlot >= 0 && candidate.owned > 0 && reason == null;
        addAction(graphics, new Rect(10, y, buttonWidth, 21), tr("install"), canInstall,
                () -> send(GunModificationPayload.INSTALL, selectedParent, selectedSlot, candidate.inventorySlot, step, offset), mouseX, mouseY, canInstall);
        addAction(graphics, new Rect(16 + buttonWidth, y, buttonWidth, 21), tr("remove"), !pending && railDrag == null && selected != null,
                () -> send(GunModificationPayload.REMOVE, removeAt, 0, -1, 0, 0), mouseX, mouseY);
        int controlsX = 22 + 2 * buttonWidth, controlWidth = Math.max(50, (width - controlsX - 16) / 2);
        var movingType = candidate != null ? candidate.type : selected == null ? null : AttachmentType.getAttachment(selected.name);
        int maxStep = owner != null && selectedSlot >= 0 && selectedSlot < owner.slots.length ? Math.max(0, owner.slots[selectedSlot].step) : 0;
        int maxOffset = movingType == null ? 0 : Math.max(0, movingType.attachOffsets.length - 1);
        int shownStep = railDrag != null ? railStep : candidate == null && selected != null ? selected.step : step;
        int shownOffset = candidate == null && selected != null ? selected.offset : offset;
        positionControl(graphics, controlsX, y, controlWidth, "step", shownStep, maxStep, movingType != null && railDrag == null,
                () -> changePosition(-1, 0), () -> changePosition(1, 0), mouseX, mouseY);
        positionControl(graphics, controlsX + controlWidth + 4, y, controlWidth, "offset", shownOffset, maxOffset, movingType != null && railDrag == null,
                () -> changePosition(0, -1), () -> changePosition(0, 1), mouseX, mouseY);
        if(localStatus == null && candidate != null && candidate.owned == 0)
            localStatus = "full".equals(reason) ? Component.empty().append(tr("catalog_preview_only")).append(" · ").append(fullHint())
                    : tr("catalog_preview_only");
        else if(reason != null && localStatus == null) localStatus = "full".equals(reason) ? fullHint() : tr("error." + reason);
    }

    private void positionControl(GuiGraphics graphics, int x, int y, int w, String key, int value, int max,
                                 boolean active, Runnable lower, Runnable higher, int mouseX, int mouseY) {
        int side = Math.min(18, w / 4);
        addAction(graphics, new Rect(x, y, side, 21), Component.literal("−"), active && !pending && value > 0, lower, mouseX, mouseY);
        addAction(graphics, new Rect(x + w - side, y, side, 21), Component.literal("+"), active && !pending && value < max, higher, mouseX, mouseY);
        int inner = Math.max(0, w - 2 * side - 4);
        centered(graphics, tr(key), x + w / 2, y + 1, inner, MUTED);
        centered(graphics, Component.literal(value + "/" + max), x + w / 2, y + 12, inner, TEXT);
        if(new Rect(x + side, y, w - side * 2, 21).contains(mouseX, mouseY)) tooltip = tr(key + "_help");
    }

    private void changePosition(int stepDelta, int offsetDelta) {
        var removeAt = removalPath();
        var node = GunAttachments.nodeAt(tree, removeAt);
        if(selectedCandidate() == null && node != null)
            send(GunModificationPayload.MOVE, removeAt, 0, -1, node.step + stepDelta, node.offset + offsetDelta);
        else { step += stepDelta; offset += offsetDelta; localStatus = null; }
    }

    private void drawStats(GuiGraphics graphics) {
        var current = GunStats.effective(menu.gunType().withAttachments(tree));
        var comparedTree = proposed;
        var removeAt = removalPath();
        boolean removal = railDrag == null && selectedCandidate() == null && removeAt.length > 0;
        if(removal) { comparedTree = GunAttachments.copy(menu.gunType(), tree); GunAttachments.remove(comparedTree, removeAt); }
        var after = GunStats.effective(menu.gunType().withAttachments(comparedTree));
        int w = (width - 28) / 3, y = layout.stats().y();
        stat(graphics, 10, y, w, "damage", current.damage(), after.damage(), true, removal);
        stat(graphics, 14 + w, y, w, "recoil", Math.abs(current.pitch()), Math.abs(after.pitch()), false, removal);
        stat(graphics, 18 + 2 * w, y, w, "spread", current.hipSpread(), after.hipSpread(), false, removal);
    }

    private void stat(GuiGraphics graphics, int x, int y, int w, String key, float before, float after,
                      boolean biggerBetter, boolean removal) {
        graphics.fill(x, y, x + w, y + 27, 0xFF1D211A);
        text(graphics, tr(key), x + 7, y + 4, w / 2, MUTED);
        boolean changed = Math.abs(before - after) > .0001F;
        Component value = Component.literal(number(before) + (changed ? " → " + number(after) : ""));
        text(graphics, value, x + 7, y + 16, w - 14,
                !changed ? TEXT : ((after > before) == biggerBetter ? GOOD : BAD));
        float range = Math.max(.1F, Math.max(Math.abs(before), Math.abs(after)) * 1.2F);
        int length = (int)(Math.min(1F, Math.abs(after) / range) * Math.max(0, w - 14));
        graphics.fill(x + 7, y + 25, x + w - 7, y + 26, BORDER);
        graphics.fill(x + 7, y + 25, x + 7 + length, y + 26,
                changed ? (after > before) == biggerBetter ? GOOD : BAD : 0xFF73745C);
        // Comparison describes removal of the selected assembly, including all nested parts.
        if(changed && w > 132) text(graphics, tr(removal ? "after_remove" : "after_install"), x + w / 2, y + 4, w / 2 - 6, MUTED);
    }

    private static String number(float value) { return String.format(Locale.ROOT, "%.2f", value); }

    private static Component partName(AttachmentType type) {
        return type.item == null ? Component.literal(type.displayName()) : new ItemStack(type.item).getHoverName();
    }

    private void send(int operation, int[] path, int slot, int inventorySlot, int newStep, int newOffset) {
        if(pending) return;
        if(operation == GunModificationPayload.INSTALL) {
            var candidate = selectedCandidate();
            if(candidate == null || candidate.inventorySlot < 0 || candidate.inventorySlot >= menu.slots.size()
                    || candidate.owned <= 0) { localStatus = tr("catalog_preview_only"); return; }
            var source = menu.slots.get(candidate.inventorySlot).getItem();
            if(source.isEmpty() || InfoType.getType(source) != candidate.type) {
                localStatus = tr("error.invalid"); refreshCandidates(); return;
            }
            String reason = installationError(candidate);
            if(reason != null) { localStatus = tr("error." + reason); return; }
            inventorySlot = candidate.inventorySlot;
        }
        observedResponse = menu.response(); pending = true; requestedOperation = operation; pendingTicks = 0; localStatus = null;
        PacketDistributor.sendToServer(new GunModificationPayload(menu.containerId, menu.revision(), operation,
                Arrays.copyOf(path, path.length), slot, inventorySlot, newStep, newOffset));
    }

    private void addAction(GuiGraphics graphics, Rect bounds, Component label, boolean enabled, Runnable run, int mx, int my) {
        addAction(graphics, bounds, label, enabled, run, mx, my, false);
    }
    private void addAction(GuiGraphics graphics, Rect b, Component label, boolean enabled, Runnable run, int mx, int my, boolean selected) {
        actions.add(new Action(b, label, enabled, run));
        boolean hovered = enabled && b.contains(mx, my);
        graphics.fill(b.x, b.y, b.x + b.w, b.y + b.h, selected && enabled ? ACCENT : hovered ? 0xFF45483A : 0xFF282C24);
        graphics.renderOutline(b.x, b.y, b.w, b.h, selected || hovered ? ACCENT : BORDER);
        centered(graphics, label, b.x + b.w / 2, b.y + (b.h - 8) / 2, b.w - 6, enabled ? selected ? 0xFF181B15 : TEXT : 0xFF666B5A);
    }

    private void text(GuiGraphics graphics, Component value, int x, int y, int maxWidth, int color) {
        if(maxWidth < 4) return;
        String text = value.getString();
        if(font.width(text) > maxWidth) text = font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
        graphics.drawString(font, text, x, y, color, false);
    }
    private void centered(GuiGraphics graphics, Component value, int cx, int y, int maxWidth, int color) {
        String text = value.getString();
        if(font.width(text) > maxWidth) text = font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
        graphics.drawString(font, text, cx - font.width(text) / 2, y, color, false);
    }
    private void wrapped(GuiGraphics graphics, Component value, Rect area, int color) {
        graphics.enableScissor(area.x + 4, area.y, area.x + area.w - 4, area.y + area.h);
        int y = area.y + 7;
        for(var line : font.split(value, Math.max(16, area.w - 14))) {
            graphics.drawString(font, line, area.x + 6, y, color, false); y += 12;
        }
        graphics.disableScissor();
    }
    private void scrollBar(GuiGraphics graphics, Rect area, int scroll, int max) {
        if(max <= 0) return;
        int h = Math.max(8, area.h * area.h / (area.h + max));
        int y = area.y + (int)((area.h - h) * (double)scroll / max);
        graphics.fill(area.x + area.w - 2, area.y, area.x + area.w, area.y + area.h, 0xFF30372A);
        graphics.fill(area.x + area.w - 2, y, area.x + area.w, y + h, 0xFF8B8B70);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if(railDrag != null) return true;
        if(button != 0) return false;
        for(var action : actions) if(action.bounds.contains(mouseX, mouseY)) {
            if(action.enabled) {
                boolean actualAssembly = selectedCandidate() == null;
                playClick(); action.run.run();
                if(actualAssembly && action.railPath != null) beginRailDrag(action.railPath, mouseX, mouseY);
            }
            return true;
        }
        if(search.isVisible() && search.mouseClicked(mouseX, mouseY, button)) { setFocused(search); return true; }
        search.setFocused(false);
        if((!compact || !inventoryTab) && treeArea.contains(mouseX, mouseY)) {
            int index = (int)(mouseY - treeArea.y + treeScroll) / ROW;
            if(index >= 0 && index < rows.size()) {
                selectRow(rows.get(index)); playClick();
            }
            return true;
        }
        if((!compact || inventoryTab) && candidateArea.contains(mouseX, mouseY)) {
            int cols = columns(), cardWidth = (candidateArea.w - 5 * (cols - 1)) / cols;
            int relativeX = (int)(mouseX - candidateArea.x), relativeY = (int)(mouseY - candidateArea.y + candidateScroll);
            int cardHeight = cardHeight();
            int col = relativeX / (cardWidth + 5), row = relativeY / cardHeight;
            int index = row * cols + col;
            if(relativeX % (cardWidth + 5) < cardWidth && relativeY % cardHeight < cardHeight - 4
                    && col < cols && index >= 0 && index < candidates.size()) {
                var clicked = candidates.get(index).id;
                selectedId = clicked.equals(selectedId) ? null : clicked;
                replacementPath = selectedId == null ? new int[0] : selectedPath.length > 0
                        ? Arrays.copyOf(selectedPath, selectedPath.length) : replacementPath;
                step = offset = 0; selectedPath = new int[0]; localStatus = null; playClick();
            }
            return true;
        }
        if(viewport.contains(mouseX, mouseY)) {
            if(!pending && selectedCandidate() == null) {
                int[] picked = preview.pickAttachment(mouseX, mouseY);
                for(var row : rows) if(row.node != null && Arrays.equals(row.path, picked)) {
                    selectRow(row);
                    if(beginRailDrag(picked, mouseX, mouseY)) return true;
                    break;
                }
            }
            dragging = true; return true;
        }
        return false; // Hidden vanilla slots must never receive clicks from this custom layout.
    }

    private void playClick() {
        if(minecraft != null) minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1F));
    }
    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if(railDrag != null && button == 0) { updateRailDrag(mouseX, mouseY); return true; }
        if(dragging && button == 0) { preview.drag(dx, dy); return true; }
        return false;
    }
    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if(railDrag != null && button == 0) {
            updateRailDrag(mouseX, mouseY);
            if(railDrag != null) {
                var finished = railDrag; int nextStep = railStep; String error = railError;
                stopRailDrag();
                if(error != null) localStatus = tr("error." + error);
                else if(nextStep != finished.axis.initialStep())
                    send(GunModificationPayload.MOVE, finished.path, 0, -1, nextStep, finished.offset);
            }
            return true;
        }
        dragging = false; return true;
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if(railDrag != null) return true;
        if(viewport.contains(mouseX, mouseY)) { preview.zoom(vertical); return true; }
        if((!compact || !inventoryTab) && treeArea.contains(mouseX, mouseY)) {
            treeScroll = Math.max(0, Math.min(Math.max(0, rows.size() * ROW - treeArea.h), treeScroll - (int)(vertical * ROW * 2))); return true;
        }
        if((!compact || inventoryTab) && candidateArea.contains(mouseX, mouseY)) {
            candidateScroll = Math.max(0, Math.min(candidateMaxScroll(), candidateScroll - (int)(vertical * cardHeight()))); return true;
        }
        return false;
    }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if(keyCode == GLFW.GLFW_KEY_ESCAPE && railDrag != null) { stopRailDrag(); localStatus = tr("rail_drag_cancelled"); return true; }
        if(railDrag != null) return true;
        if(keyCode == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if(search.isFocused() && search.keyPressed(keyCode, scanCode, modifiers)) return true;
        if(search.isFocused()) return false;
        if(minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) { onClose(); return true; }
        return false;
    }
    @Override public boolean charTyped(char codePoint, int modifiers) {
        return railDrag != null || search.isVisible() && search.charTyped(codePoint, modifiers);
    }
    @Override public void onClose() { stopRailDrag(); super.onClose(); }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) { }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) { }
}
