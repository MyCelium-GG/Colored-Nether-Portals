package my.celium.org.colorednetherportals.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import my.celium.org.client.MycelTheme;
import my.celium.org.colorednetherportals.ColoredNetherPortalsMod;
import my.celium.org.colorednetherportals.data.PortalPainter;
import my.celium.org.colorednetherportals.data.PortalShape;
import my.celium.org.colorednetherportals.data.PortalStyle;
import my.celium.org.colorednetherportals.data.PortalStyle.Mode;
import my.celium.org.colorednetherportals.data.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * The portal color menu, opened by sneaking and right-clicking a portal with
 * an empty hand. Left: a live preview, the portal at its real size in its
 * obsidian frame under a day or night sky, each block painted the way the
 * world will show it. Right: style tiles (each showing the style in your
 * colors), the colors (up to 16 for gradients and pulses) with palette,
 * picker and hex field, and the speed of animated styles. Apply sends it to
 * the server (or saves it locally on servers without the mod). Client only.
 */
public final class PortalColorScreen extends Screen {
    static final int TITLE_BAR = 20;
    static final int FOOTER = 28;
    static final int PREVIEW_W = 104;
    static final int TILE_H = 22;
    static final int CELL = 13;
    static final int PALETTE_COLS = 8;
    static final int PALETTE_ROWS = 3;
    static final int PICK_H = 30;
    static final int HUE_H = 6;
    static final int ROW_H = 14;
    static final long MESSAGE_MS = 4000;
    /** Vanilla's portal: 32 frames of 16x16 stacked in one 16x512 sheet, one frame per tick. */
    static final Identifier PORTAL_TEXTURE = Identifier.withDefaultNamespace("textures/block/nether_portal.png");
    /** Ours: the grey swirl colored portals are tinted from, 32 frames, two ticks each. */
    static final Identifier SWIRL_TEXTURE = Identifier.fromNamespaceAndPath(ColoredNetherPortalsMod.ID,
            "textures/block/portal_swirl.png");
    static final Identifier OBSIDIAN_TEXTURE = Identifier.withDefaultNamespace("textures/block/obsidian.png");
    static final int FRAMES = 32;
    static final int GROUND_H = 10;
    /** Preview size in px per block, at most a block's real 16 px. */
    static final int MAX_BLOCK = 16;

    private static final String[] SPEEDS = {"Slow", "Normal", "Fast"};
    private static final String[] ROW_BUTTONS = {"+", "-", "Reverse"};
    private static final String[] ROW_TIPS = {"Add a color after the selected one", "Remove the selected color",
            "Reverse the order"};
    /** Night sky stars: x, y as fractions of the preview, fixed so they don't flicker. */
    private static final float[][] STARS = {{0.12f, 0.08f}, {0.3f, 0.2f}, {0.8f, 0.06f}, {0.66f, 0.27f},
            {0.18f, 0.42f}, {0.88f, 0.38f}, {0.45f, 0.12f}, {0.07f, 0.6f}, {0.92f, 0.55f}, {0.55f, 0.33f},
            {0.25f, 0.68f}, {0.78f, 0.72f}};
    private static boolean night = true;

    /** The portal's corner block, which is its id. */
    private final BlockPos pos;
    private final PortalShape shape;
    private PortalStyle style;
    /** Which color the palette and picker edit, an index into the style's colors. */
    private int slot;
    private float hue;
    private float saturation;
    private float value;
    private EditBox hex;
    private boolean syncingHex;
    private FlatButton applyButton;
    /** What is being dragged: 1 = saturation/value square, 2 = hue bar. */
    private int dragging;
    private String message;
    private int messageColor;
    private long messageUntil;

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        int right() {
            return x + w;
        }

        int bottom() {
            return y + h;
        }
    }

    public PortalColorScreen(PortalShape shape) {
        super(Component.literal("Colored Nether Portals"));
        this.shape = shape;
        this.pos = shape.corner();
        PortalStyle current = applied();
        // A vanilla portal starts on friendly colors so picking a style shows something at once.
        this.style = current.isVanilla() ? new PortalStyle(Mode.VANILLA, 0x3AB3DA, 0xB388FF, 2) : current;
        syncHsv();
    }

    /** What the portal shows right now. */
    private PortalStyle applied() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? PortalStyle.VANILLA : ClientPortals.style(minecraft.level, pos);
    }

    private boolean changed() {
        return !style.sameLook(applied());
    }

    // ================================================================ layout

    private int panelW() {
        return Math.min(width - 12, 400);
    }

    private int panelH() {
        return Math.min(height - 12, 252);
    }

    private int px() {
        return (width - panelW()) / 2;
    }

    private int py() {
        return (height - panelH()) / 2;
    }

    private Rect preview() {
        int w = Math.min(PREVIEW_W, panelW() / 4);
        return new Rect(px() + 8, py() + TITLE_BAR + 6, w, panelH() - TITLE_BAR - 6 - FOOTER - 2);
    }

    private int x0() {
        return preview().right() + 12;
    }

    private int x1() {
        return px() + panelW() - 10;
    }

    private int top() {
        return py() + TITLE_BAR + 6;
    }

    /** Style tiles: 3 columns, 2 rows. */
    private List<Rect> tiles() {
        List<Rect> out = new ArrayList<>();
        int w = (x1() - x0() - 2 * 4) / 3;
        int y = top() + 11;
        Mode[] modes = Mode.values();
        for (int i = 0; i < modes.length; i++) {
            out.add(new Rect(x0() + (i % 3) * (w + 4), y + (i / 3) * (TILE_H + 3), w, TILE_H));
        }
        return out;
    }

    private int descY() {
        return top() + 11 + 2 * TILE_H + 3 + 5;
    }

    /** Where the second section ("Colors", or "Speed" for rainbow and cycle) starts. */
    private int sectionY() {
        return descY() + 15;
    }

    /** Gradients and pulses edit a row of colors; solid has one labeled slot. */
    private boolean multi() {
        return style.mode().maxColors > 1;
    }

    private int colorCount() {
        return multi() ? style.colors().size() : style.mode().colors;
    }

    /** The color row's buttons: add, remove and reverse, right-aligned. */
    private List<Rect> rowButtons() {
        List<Rect> out = new ArrayList<>();
        if (!multi()) {
            return out;
        }
        int y = sectionY() + 11 + 3;
        int x = x1();
        for (String label : ROW_BUTTONS) {
            x -= font.width(label) + 12 + 3;
        }
        for (String label : ROW_BUTTONS) {
            int w = font.width(label) + 12;
            x += 3;
            out.add(new Rect(x, y, w, ROW_H));
            x += w;
        }
        return out;
    }

    private List<Rect> slots() {
        List<Rect> out = new ArrayList<>();
        int x = x0();
        int y = sectionY() + 11;
        if (!multi()) {
            for (int i = 0; i < colorCount(); i++) {
                int w = Math.max(font.width("Color"), font.width("#000000")) + 30;
                out.add(new Rect(x, y, w, 20));
            }
            return out;
        }
        int n = colorCount();
        int room = rowButtons().get(0).x() - 6 - x;
        int w = Math.max(6, Math.min(20, room / n - 2));
        for (int i = 0; i < n; i++) {
            out.add(new Rect(x + i * (w + 2), y, w, 20));
        }
        return out;
    }

    private void addColor() {
        List<Integer> colors = new ArrayList<>(style.colors());
        if (colors.size() >= PortalStyle.MAX_COLORS) {
            return;
        }
        // Halfway to the next color, or a copy at the end, so the portal barely changes until you pick one.
        int here = colors.get(slot);
        int rgb = slot + 1 < colors.size() ? Colors.lerp(here, colors.get(slot + 1), 0.5f) : here;
        colors.add(slot + 1, rgb);
        style = style.withColors(colors);
        slot++;
        syncHsv();
    }

    private void removeColor() {
        List<Integer> colors = new ArrayList<>(style.colors());
        if (colors.size() <= style.mode().colors) {
            return;
        }
        colors.remove(slot);
        style = style.withColors(colors);
        slot = Math.min(slot, colors.size() - 1);
        syncHsv();
    }

    private void reverseColors() {
        List<Integer> colors = new ArrayList<>(style.colors());
        java.util.Collections.reverse(colors);
        style = style.withColors(colors);
        slot = colors.size() - 1 - slot;
        syncHsv();
    }

    private int paletteY() {
        return sectionY() + 11 + 20 + 6;
    }

    private Rect palette() {
        return new Rect(x0(), paletteY(), PALETTE_COLS * CELL, PALETTE_ROWS * CELL);
    }

    private Rect square() {
        int x = palette().right() + 10;
        return new Rect(x, paletteY(), Math.max(30, x1() - x), PICK_H);
    }

    private Rect hueBar() {
        Rect square = square();
        return new Rect(square.x(), square.bottom() + 3, square.w(), HUE_H);
    }

    private int hexRowY() {
        return palette().bottom() + 6;
    }

    /** Speed chips: in the section for rainbow and cycle, on the hex row for pulse. */
    private List<Rect> speedChips() {
        List<Rect> out = new ArrayList<>();
        if (!style.animated()) {
            return out;
        }
        boolean beside = style.mode().colors > 0;
        int x = beside ? square().x() + font.width("Speed") + 6 : x0();
        int y = beside ? hexRowY() : sectionY() + 11;
        for (String label : SPEEDS) {
            int w = font.width(label) + 12;
            out.add(new Rect(x, y, w, ROW_H));
            x += w + 3;
        }
        return out;
    }

    // =============================================================== widgets

    @Override
    protected void init() {
        String text = hex == null ? Colors.toHex(activeColor()) : hex.getValue();
        hex = new EditBox(font, x0() + font.width("Hex") + 10, hexRowY() + 3, 50, 9, Component.literal("Hex color"));
        hex.setBordered(false);
        hex.setMaxLength(7);
        hex.setTextColor(MycelTheme.TEXT);
        hex.setValue(text);
        hex.setResponder(value -> {
            if (syncingHex) {
                return;
            }
            int rgb = Colors.parseHex(value);
            if (rgb >= 0) {
                setActiveColor(rgb, true);
            }
        });
        hex.visible = style.mode().colors > 0;
        addRenderableWidget(hex);

        int y = py() + panelH() - FOOTER + (FOOTER - FlatButton.HEIGHT) / 2;
        int right = x1();
        FlatButton back = new FlatButton(right - 54, y, 54, CommonComponents.GUI_BACK, false, this::onClose);
        applyButton = new FlatButton(right - 54 - 4 - 64, y, 64, Component.literal("Apply"), true, this::apply);
        FlatButton reset = new FlatButton(right - 54 - 4 - 64 - 4 - 54, y, 54, Component.literal("Reset"), false,
                this::reset);
        reset.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("Back to the vanilla purple portal")));
        addRenderableWidget(reset);
        addRenderableWidget(applyButton);
        addRenderableWidget(back);
    }

    private int activeColor() {
        return style.colors().get(slot);
    }

    /** Changes the color being edited; {@code fromHex} leaves the hex field as typed. */
    private void setActiveColor(int rgb, boolean fromHex) {
        style = style.withColor(slot, rgb);
        if (!fromHex) {
            updateHexField();
        }
        float[] hsv = Colors.toHsv(rgb);
        if (hsv[1] > 0.01f && hsv[2] > 0.01f) {
            hue = hsv[0];
        }
        saturation = hsv[1];
        value = hsv[2];
    }

    private void syncHsv() {
        float[] hsv = Colors.toHsv(activeColor());
        hue = hsv[0];
        saturation = hsv[1];
        value = hsv[2];
        updateHexField();
    }

    private void updateHexField() {
        if (hex != null) {
            syncingHex = true;
            hex.setValue(Colors.toHex(activeColor()));
            syncingHex = false;
        }
    }

    private void pickFromSquare(double mx, double my) {
        Rect r = square();
        saturation = (float) Math.max(0, Math.min(1, (mx - r.x()) / (r.w() - 1)));
        value = (float) Math.max(0, Math.min(1, 1 - (my - r.y()) / (r.h() - 1)));
        applyPicker();
    }

    private void pickFromHue(double mx) {
        Rect r = hueBar();
        hue = (float) Math.max(0, Math.min(0.999, (mx - r.x()) / r.w()));
        applyPicker();
    }

    private void applyPicker() {
        style = style.withColor(slot, Colors.hsv(hue, saturation, value));
        updateHexField();
    }

    private void setMode(Mode mode) {
        style = style.withMode(mode);
        if (slot >= Math.min(style.colors().size(), Math.max(1, mode.maxColors))) {
            slot = 0;
        }
        syncHsv();
        rebuildWidgets();
    }

    private void apply() {
        if (minecraft == null || minecraft.level == null || !changed()) {
            return;
        }
        boolean shared = ClientPortals.serverHasMod();
        ClientPortals.set(minecraft.level, pos, style);
        ColoredNetherPortalsMod.debug("Applied {} at {} ({})", style, pos, shared ? "server" : "local");
        if (style.isVanilla()) {
            flash("Back to the vanilla portal", MycelTheme.GOOD);
        } else if (shared) {
            flash("Applied for everyone with the mod", MycelTheme.GOOD);
        } else {
            flash("Applied on this computer only", MycelTheme.WARN);
        }
    }

    private void reset() {
        setMode(Mode.VANILLA);
        apply();
    }

    private void flash(String text, int color) {
        message = text;
        messageColor = color;
        messageUntil = Util.getMillis() + MESSAGE_MS;
    }

    @Override
    public void tick() {
        if (applyButton != null) {
            applyButton.active = changed();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(null);
    }

    // ================================================================ render

    private static double ticks() {
        return Util.getMillis() / 50.0;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float a) {
        int px = px();
        int py = py();
        int pw = panelW();
        int ph = panelH();
        gfx.fill(0, 0, width, height, MycelTheme.SHADE);
        MycelTheme.panel(gfx, px, py, px + pw, py + ph);
        gfx.fill(px + 2, py + 2, px + pw - 2, py + TITLE_BAR, MycelTheme.ACCENT_DARK);
        gfx.fill(px + 2, py + TITLE_BAR - 1, px + pw - 2, py + TITLE_BAR, MycelTheme.HEADER_EDGE);
        gfx.text(font, title, px + 9, py + 7, MycelTheme.HEADING);
        boolean shared = ClientPortals.serverHasMod();
        String scope = shared ? "Everyone with the mod sees this" : "Only you see this";
        int scopeX = px + pw - 9 - font.width(scope);
        if (scopeX > px + 9 + font.width(title) + 8) {
            gfx.text(font, scope, scopeX, py + 7, shared ? 0xFFFFCDD2 : MycelTheme.WARN);
        }
        gfx.fill(px + 2, py + ph - FOOTER, px + pw - 2, py + ph - FOOTER + 1, MycelTheme.BORDER);
        renderHexFrame(gfx);
        if (applyButton != null) {
            applyButton.active = changed();
        }
        super.extractRenderState(gfx, mouseX, mouseY, a);
        renderPreview(gfx, mouseX, mouseY);
        renderTiles(gfx, mouseX, mouseY);
        if (style.mode().colors > 0) {
            label(gfx, switch (style.mode()) {
                case GRADIENT -> "COLORS, BOTTOM TO TOP";
                case PULSE -> "COLORS, IN ORDER";
                default -> "COLORS";
            }, x0(), sectionY());
            renderSlots(gfx, mouseX, mouseY);
            renderPalette(gfx, mouseX, mouseY);
            renderPicker(gfx);
        }
        renderSpeed(gfx, mouseX, mouseY);
        renderFooter(gfx);
    }

    private void label(GuiGraphicsExtractor gfx, String text, int x, int y) {
        gfx.text(font, text, x, y, MycelTheme.FAINT);
        int lineX = x + font.width(text) + 5;
        if (lineX < x1()) {
            gfx.fill(lineX, y + 4, x1(), y + 5, MycelTheme.BORDER);
        }
    }

    private void renderHexFrame(GuiGraphicsExtractor gfx) {
        if (hex == null) {
            return;
        }
        hex.visible = style.mode().colors > 0;
        if (!hex.visible) {
            return;
        }
        Rect box = new Rect(x0() + font.width("Hex") + 5, hexRowY(), 60, ROW_H);
        gfx.text(font, "Hex", x0(), hexRowY() + 3, MycelTheme.DIM);
        gfx.fill(box.x(), box.y(), box.right(), box.bottom(), hex.isFocused() ? MycelTheme.ACCENT : MycelTheme.BORDER);
        gfx.fill(box.x() + 1, box.y() + 1, box.right() - 1, box.bottom() - 1, 0xFF1B1B1B);
    }

    // ------------------------------------------------------------ preview

    /** Px per block so the portal and its frame fit the preview. */
    private int blockSize() {
        Rect r = preview();
        int byWidth = (r.w() - 8) / (shape.width() + 2);
        int byHeight = (r.h() - GROUND_H - 8) / (shape.height() + 2);
        return Math.max(2, Math.min(MAX_BLOCK, Math.min(byWidth, byHeight)));
    }

    /**
     * The portal at its real size in an obsidian frame, standing on the
     * ground under a sky, every block painted the way the world will show it:
     * the vanilla texture for vanilla, the tinted swirl otherwise.
     */
    private void renderPreview(GuiGraphicsExtractor gfx, int mx, int my) {
        Rect r = preview();
        double ticks = ticks();
        gfx.enableScissor(r.x(), r.y(), r.right(), r.bottom());
        // Sky.
        int skyTop = night ? 0xFF060814 : 0xFF5F8FE8;
        int skyBottom = night ? 0xFF1B2448 : 0xFFB9D3FF;
        int bands = 24;
        for (int i = 0; i < bands; i++) {
            int y0 = r.y() + r.h() * i / bands;
            int y1 = r.y() + r.h() * (i + 1) / bands;
            gfx.fill(r.x(), y0, r.right(), y1, 0xFF000000 | Colors.lerp(skyTop, skyBottom, (float) i / (bands - 1)));
        }
        if (night) {
            for (float[] star : STARS) {
                int sx = r.x() + Math.round(star[0] * (r.w() - 2));
                int sy = r.y() + Math.round(star[1] * (r.h() - 40));
                boolean twinkle = ((long) (ticks / 10) + sx) % 7 == 0;
                gfx.fill(sx, sy, sx + 1, sy + 1, twinkle ? 0x80FFFFFF : 0xFFFFFFFF);
            }
        }
        int groundY = r.bottom() - GROUND_H;
        gfx.fill(r.x(), groundY, r.right(), r.bottom(), night ? 0xFF1E2B14 : 0xFF4E7A2C);
        gfx.fill(r.x(), groundY, r.right(), groundY + 2, night ? 0xFF2C3E1E : 0xFF6FA040);
        // Frame: obsidian all around, the portal inside.
        int block = blockSize();
        int w = shape.width();
        int h = shape.height();
        int left = r.x() + (r.w() - (w + 2) * block) / 2;
        int bottom = groundY;
        for (int u = -1; u <= w; u++) {
            for (int v = -1; v <= h; v++) {
                int x = left + (u + 1) * block;
                int y = bottom - (v + 2) * block;
                if (u < 0 || v < 0 || u >= w || v >= h) {
                    sheet(gfx, OBSIDIAN_TEXTURE, x, y, block, 0, 16, 0xFFFFFFFF);
                }
            }
        }
        for (int u = 0; u < w; u++) {
            for (int v = 0; v < h; v++) {
                portalBlock(gfx, style, u, v, w, h, left + (u + 1) * block, bottom - (v + 2) * block, block, ticks);
            }
        }
        gfx.disableScissor();
        gfx.outline(r.x() - 1, r.y() - 1, r.w() + 2, r.h() + 2, MycelTheme.BORDER);
        // Day / night switch, and the portal's size.
        String toggle = night ? "Night" : "Day";
        Rect chip = new Rect(r.x() + 3, r.y() + 3, font.width(toggle) + 8, 12);
        boolean hover = r.contains(mx, my);
        gfx.fill(chip.x(), chip.y(), chip.right(), chip.bottom(), hover ? 0xC0000000 : 0x80000000);
        gfx.text(font, toggle, chip.x() + 4, chip.y() + 2, hover ? MycelTheme.HEADING : MycelTheme.DIM);
        String size = w + "\u00D7" + h;
        int sw = font.width(size) + 8;
        gfx.fill(r.right() - 3 - sw, r.y() + 3, r.right() - 3, r.y() + 15, hover ? 0xC0000000 : 0x80000000);
        gfx.text(font, size, r.right() - 3 - sw + 4, r.y() + 5, hover ? MycelTheme.HEADING : MycelTheme.DIM);
        if (hover) {
            gfx.setTooltipForNextFrame(font, Component.literal("Click to switch to " + (night ? "day" : "night")), mx, my);
        }
    }

    /** One portal block in {@code style}: the vanilla texture, or the swirl's cells tinted like in the world. */
    private static void portalBlock(GuiGraphicsExtractor gfx, PortalStyle style, int u, int v, int w, int h, int x,
            int y, int size, double ticks) {
        if (style.isVanilla()) {
            sheet(gfx, PORTAL_TEXTURE, x, y, size, (int) ticks % FRAMES * 16, FRAMES * 16, 0xFFFFFFFF);
            return;
        }
        int[] cells = PortalPainter.cells(style, u, v, w, h, ticks);
        int n = PortalPainter.CELLS;
        int frameV = (int) (ticks / 2) % FRAMES * 16;
        int step = 16 / n;
        for (int cy = 0; cy < n; cy++) {
            for (int cu = 0; cu < n; cu++) {
                // Screen y grows downwards; cell rows count up from the bottom.
                int x0 = x + cu * size / n;
                int x1 = x + (cu + 1) * size / n;
                int y0 = y + (n - 1 - cy) * size / n;
                int y1 = y + (n - cy) * size / n;
                if (x1 > x0 && y1 > y0) {
                    gfx.blit(RenderPipelines.GUI_TEXTURED, SWIRL_TEXTURE, x0, y0, cu * step,
                            frameV + (n - 1 - cy) * step, x1 - x0, y1 - y0, step, step, 16, FRAMES * 16,
                            0xFF000000 | PortalPainter.swirlTint(cells[cy * n + cu]));
                }
            }
        }
    }

    /** One 16x16 frame starting at {@code v} of a 16 px wide sheet {@code sheetH} tall, drawn {@code size} px wide. */
    private static void sheet(GuiGraphicsExtractor gfx, Identifier texture, int x, int y, int size, int v, int sheetH,
            int color) {
        gfx.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, v, size, size, 16, 16, 16, sheetH, color);
    }

    // ------------------------------------------------------------ style tiles

    private void renderTiles(GuiGraphicsExtractor gfx, int mx, int my) {
        label(gfx, "STYLE", x0(), top());
        List<Rect> tiles = tiles();
        Mode[] modes = Mode.values();
        double ticks = ticks();
        for (int i = 0; i < modes.length; i++) {
            Rect r = tiles.get(i);
            boolean on = style.mode() == modes[i];
            boolean hover = r.contains(mx, my);
            gfx.fill(r.x(), r.y(), r.right(), r.bottom(), on ? MycelTheme.ACCENT : hover ? 0xFF5A5A5A : MycelTheme.BORDER);
            gfx.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.bottom() - 1,
                    on ? 0xFF3A1414 : hover ? 0xFF2E2E2E : MycelTheme.SURFACE);
            // A tiny 2x3 portal in this style, with your colors.
            PortalStyle sample = style.withMode(modes[i]);
            int bx = r.x() + 4;
            int cell = Math.max(2, (r.h() - 6) / 3);
            int by = r.bottom() - 3 - 3 * cell;
            for (int u = 0; u < 2; u++) {
                for (int v = 0; v < 3; v++) {
                    portalBlock(gfx, sample, u, v, 2, 3, bx + u * cell, by + (2 - v) * cell, cell, ticks);
                }
            }
            bx += 2 * cell - 4;
            gfx.text(font, MycelTheme.fit(font, modes[i].label, r.w() - 18), bx + 9, r.y() + (r.h() - 8) / 2,
                    on || hover ? MycelTheme.HEADING : MycelTheme.TEXT);
        }
        gfx.text(font, MycelTheme.fit(font, description(style.mode()), x1() - x0()), x0(), descY(), MycelTheme.DIM);
    }

    private static String description(Mode mode) {
        return switch (mode) {
            case VANILLA -> "The usual purple portal.";
            case SOLID -> "One color for the whole portal.";
            case GRADIENT -> "Fades through your colors, bottom to top.";
            case RAINBOW -> "Rainbow stripes flowing up the portal.";
            case CYCLE -> "The whole portal runs through every color.";
            case PULSE -> "Fades through your colors and back.";
        };
    }

    private void chip(GuiGraphicsExtractor gfx, Rect r, String label, boolean on, boolean hover) {
        gfx.fill(r.x(), r.y(), r.right(), r.bottom(), on ? MycelTheme.ACCENT : hover ? 0xFF5A5A5A : MycelTheme.BORDER);
        gfx.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.bottom() - 1,
                on ? MycelTheme.ACCENT_DARK : hover ? MycelTheme.BORDER : MycelTheme.SURFACE);
        gfx.text(font, label, r.x() + 6, r.y() + 3, on || hover ? MycelTheme.HEADING : MycelTheme.DIM);
    }

    // ------------------------------------------------------------ colors

    private void renderSlots(GuiGraphicsExtractor gfx, int mx, int my) {
        List<Rect> slots = slots();
        for (int i = 0; i < slots.size(); i++) {
            Rect r = slots.get(i);
            boolean on = slot == i;
            boolean hover = r.contains(mx, my);
            int rgb = style.colors().get(i);
            gfx.fill(r.x(), r.y(), r.right(), r.bottom(), on ? MycelTheme.HEADING : hover ? 0xFF5A5A5A : MycelTheme.BORDER);
            if (!multi()) {
                gfx.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.bottom() - 1, on ? 0xFF2E2E2E : MycelTheme.SURFACE);
                gfx.fill(r.x() + 3, r.y() + 3, r.x() + 19, r.bottom() - 3, 0xFF000000 | rgb);
                gfx.text(font, "Color", r.x() + 23, r.y() + 2, on ? MycelTheme.HEADING : MycelTheme.TEXT);
                gfx.text(font, Colors.toHex(rgb), r.x() + 23, r.y() + 11, MycelTheme.FAINT);
                continue;
            }
            gfx.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.bottom() - 1, 0xFF000000 | rgb);
            if (on) {
                int dot = Colors.isLight(rgb) ? 0xFF000000 : 0xFFFFFFFF;
                int dx = r.x() + r.w() / 2;
                gfx.fill(dx - 1, r.bottom() - 5, dx + 1, r.bottom() - 3, dot);
            }
            if (hover) {
                gfx.setTooltipForNextFrame(font, Component.literal("Color " + (i + 1) + " of " + slots.size() + "  "
                        + Colors.toHex(rgb)), mx, my);
            }
        }
        List<Rect> buttons = rowButtons();
        for (int i = 0; i < buttons.size(); i++) {
            Rect r = buttons.get(i);
            boolean usable = i == 0 ? style.colors().size() < PortalStyle.MAX_COLORS
                    : i == 1 ? style.colors().size() > style.mode().colors : true;
            boolean hover = usable && r.contains(mx, my);
            chip(gfx, r, ROW_BUTTONS[i], false, hover);
            if (!usable) {
                gfx.fill(r.x(), r.y(), r.right(), r.bottom(), 0x90101010);
            } else if (hover) {
                gfx.setTooltipForNextFrame(font, Component.literal(ROW_TIPS[i]), mx, my);
            }
        }
    }

    private void renderPalette(GuiGraphicsExtractor gfx, int mx, int my) {
        Rect r = palette();
        int hovered = paletteAt(mx, my);
        for (int i = 0; i < Colors.PRESETS.length; i++) {
            int x = r.x() + (i % PALETTE_COLS) * CELL;
            int y = r.y() + (i / PALETTE_COLS) * CELL;
            int rgb = Colors.PRESETS[i].rgb();
            boolean picked = rgb == activeColor();
            gfx.fill(x, y, x + CELL - 1, y + CELL - 1,
                    picked ? MycelTheme.HEADING : i == hovered ? 0xFF9A9A9A : 0xFF0E0E0E);
            gfx.fill(x + 1, y + 1, x + CELL - 2, y + CELL - 2, 0xFF000000 | rgb);
            if (picked) {
                int dot = Colors.isLight(rgb) ? 0xFF000000 : 0xFFFFFFFF;
                gfx.fill(x + CELL / 2 - 1, y + CELL / 2 - 1, x + CELL / 2 + 1, y + CELL / 2 + 1, dot);
            }
        }
        if (hovered >= 0) {
            Colors.Preset preset = Colors.PRESETS[hovered];
            gfx.setTooltipForNextFrame(font, Component.literal(preset.name() + "  " + Colors.toHex(preset.rgb())), mx, my);
        }
    }

    private int paletteAt(double mx, double my) {
        Rect r = palette();
        if (!r.contains(mx, my)) {
            return -1;
        }
        int index = (int) ((my - r.y()) / CELL) * PALETTE_COLS + (int) ((mx - r.x()) / CELL);
        return index < Colors.PRESETS.length ? index : -1;
    }

    private void renderPicker(GuiGraphicsExtractor gfx) {
        Rect sq = square();
        int cellW = 3;
        int cellH = 2;
        for (int x = 0; x < sq.w(); x += cellW) {
            float s = (float) x / (sq.w() - 1);
            for (int y = 0; y < sq.h(); y += cellH) {
                float v = 1 - (float) y / (sq.h() - 1);
                gfx.fill(sq.x() + x, sq.y() + y, Math.min(sq.right(), sq.x() + x + cellW),
                        Math.min(sq.bottom(), sq.y() + y + cellH), 0xFF000000 | Colors.hsv(hue, s, v));
            }
        }
        gfx.outline(sq.x() - 1, sq.y() - 1, sq.w() + 2, sq.h() + 2, MycelTheme.BORDER);
        int mx = sq.x() + Math.round(saturation * (sq.w() - 1));
        int my = sq.y() + Math.round((1 - value) * (sq.h() - 1));
        gfx.outline(mx - 3, my - 3, 7, 7, 0xFF000000);
        gfx.outline(mx - 2, my - 2, 5, 5, 0xFFFFFFFF);
        Rect hb = hueBar();
        for (int x = 0; x < hb.w(); x++) {
            gfx.fill(hb.x() + x, hb.y(), hb.x() + x + 1, hb.bottom(), 0xFF000000 | Colors.hsv((float) x / hb.w(), 1, 1));
        }
        gfx.outline(hb.x() - 1, hb.y() - 1, hb.w() + 2, hb.h() + 2, MycelTheme.BORDER);
        int hx = hb.x() + Math.round(hue * (hb.w() - 1));
        gfx.fill(hx - 2, hb.y() - 2, hx + 3, hb.bottom() + 2, 0xFF000000);
        gfx.fill(hx - 1, hb.y() - 1, hx + 2, hb.bottom() + 1, 0xFFFFFFFF);
    }

    private void renderSpeed(GuiGraphicsExtractor gfx, int mx, int my) {
        List<Rect> chips = speedChips();
        if (chips.isEmpty()) {
            return;
        }
        if (style.mode().colors == 0) {
            label(gfx, "SPEED", x0(), sectionY());
        } else {
            gfx.text(font, "Speed", chips.get(0).x() - font.width("Speed") - 6, chips.get(0).y() + 3, MycelTheme.DIM);
        }
        for (int i = 0; i < chips.size(); i++) {
            chip(gfx, chips.get(i), SPEEDS[i], style.speed() == i + 1, chips.get(i).contains(mx, my));
        }
    }

    private void renderFooter(GuiGraphicsExtractor gfx) {
        int y = py() + panelH() - FOOTER + (FOOTER - 8) / 2;
        String text;
        int color;
        if (message != null && Util.getMillis() < messageUntil) {
            text = message;
            color = messageColor;
        } else if (changed()) {
            text = "Not applied yet";
            color = MycelTheme.WARN;
        } else if (!ClientPortals.serverHasMod()) {
            text = "This server doesn't have the mod";
            color = MycelTheme.FAINT;
        } else {
            text = "Portal at " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
            color = MycelTheme.FAINT;
        }
        int max = x1() - 54 - 4 - 64 - 4 - 54 - 8 - (px() + 10);
        gfx.text(font, MycelTheme.fit(font, text, max), px() + 10, y, color);
    }

    // ================================================================= input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        double mx = event.x();
        double my = event.y();
        if (hex != null && hex.isFocused()) {
            hex.setFocused(false);
            setFocused(null);
        }
        if (preview().contains(mx, my)) {
            night = !night;
            return true;
        }
        List<Rect> tiles = tiles();
        Mode[] modes = Mode.values();
        for (int i = 0; i < modes.length; i++) {
            if (tiles.get(i).contains(mx, my)) {
                setMode(modes[i]);
                return true;
            }
        }
        List<Rect> speeds = speedChips();
        for (int i = 0; i < speeds.size(); i++) {
            if (speeds.get(i).contains(mx, my)) {
                style = style.withSpeed(i + 1);
                return true;
            }
        }
        if (style.mode().colors == 0) {
            return false;
        }
        List<Rect> slots = slots();
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).contains(mx, my)) {
                slot = i;
                syncHsv();
                return true;
            }
        }
        List<Rect> buttons = rowButtons();
        for (int i = 0; i < buttons.size(); i++) {
            if (buttons.get(i).contains(mx, my)) {
                switch (i) {
                    case 0 -> addColor();
                    case 1 -> removeColor();
                    default -> reverseColors();
                }
                return true;
            }
        }
        int preset = paletteAt(mx, my);
        if (preset >= 0) {
            setActiveColor(Colors.PRESETS[preset].rgb(), false);
            return true;
        }
        Rect sq = square();
        if (new Rect(sq.x() - 2, sq.y() - 2, sq.w() + 4, sq.h() + 4).contains(mx, my)) {
            dragging = 1;
            pickFromSquare(mx, my);
            return true;
        }
        Rect hb = hueBar();
        if (new Rect(hb.x() - 2, hb.y() - 3, hb.w() + 4, hb.h() + 6).contains(mx, my)) {
            dragging = 2;
            pickFromHue(mx);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging == 1) {
            pickFromSquare(event.x(), event.y());
            return true;
        }
        if (dragging == 2) {
            pickFromHue(event.x());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = 0;
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation()) {
            apply();
            return true;
        }
        return super.keyPressed(event);
    }
}
