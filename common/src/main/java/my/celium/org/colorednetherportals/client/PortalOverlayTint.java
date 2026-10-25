package my.celium.org.colorednetherportals.client;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.List;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import my.celium.org.colorednetherportals.ColoredNetherPortalsMod;
import my.celium.org.colorednetherportals.data.PortalPainter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * Colors the purple swirl over the screen while you stand in a portal, and the
 * loading screen while you travel through it.
 *
 * <p>Both draw the portal block's particle texture, which our model points at
 * {@code colorednetherportals:block/portal_overlay}. That texture's animation
 * frames live on the GPU, one texture per frame; this class rewrites them:
 * vanilla's portal frames (read from the game's own resources) normally, the
 * grey swirl in the portal's color while you are in a colored portal and
 * until you have arrived. Client only.
 */
final class PortalOverlayTint {
    static final Identifier SPRITE = Identifier.fromNamespaceAndPath(ColoredNetherPortalsMod.ID, "block/portal_overlay");
    private static final Identifier VANILLA_TEXTURE = Identifier.withDefaultNamespace("textures/block/nether_portal.png");
    private static final Identifier SWIRL_TEXTURE = Identifier.fromNamespaceAndPath(ColoredNetherPortalsMod.ID,
            "textures/block/portal_swirl.png");
    /** Keep the color this long after leaving a colored portal, so the trip's loading screen keeps it. */
    private static final long HOLD_MS = 4000;
    /** Repaint animated colors at most this often. */
    private static final long REPAINT_MS = 150;
    /** Vanilla's overlay texture is about this opaque; ours matches it. */
    private static final int ALPHA = 158;
    private static final int SIZE = 16;
    private static final int VANILLA = -1;
    private static final int UNKNOWN = -2;

    private static List<?> statesSeen;
    private static Int2ObjectMap<GpuTextureView> frames;
    private static NativeImage vanillaSheet;
    private static NativeImage swirlSheet;
    private static int shown = UNKNOWN;
    private static int heldColor = VANILLA;
    private static long heldUntil;
    private static long lastPaint;
    private static boolean broken;

    private PortalOverlayTint() {
    }

    /** Called every client tick. */
    static void tick(Minecraft minecraft) {
        if (broken || minecraft.player == null && !(minecraft.gui.screen() instanceof LevelLoadingScreen)) {
            return;
        }
        long now = Util.getMillis();
        int here = minecraft.player == null ? -1
                : ClientPortals.colorAtEyes(minecraft.player.getX(), minecraft.player.getEyeY(), minecraft.player.getZ());
        if (here >= 0) {
            heldColor = here;
            heldUntil = now + HOLD_MS;
        } else if (now > heldUntil && !(minecraft.gui.screen() instanceof LevelLoadingScreen)) {
            heldColor = VANILLA;
        }
        int wanted = ColoredNetherPortalsMod.isModEnabled() ? heldColor : VANILLA;
        try {
            if (!locate(minecraft)) {
                return;
            }
            if (wanted != shown && (shown == UNKNOWN || now - lastPaint >= REPAINT_MS)) {
                paint(wanted);
                shown = wanted;
                lastPaint = now;
            }
        } catch (ReflectiveOperationException | IOException | RuntimeException e) {
            broken = true;
            ColoredNetherPortalsMod.warn("Can't color the portal screen effect in this Minecraft version: {}", e.toString());
        }
    }

    /** Finds our sprite's frame textures; again after every resource reload. Returns whether they are known. */
    @SuppressWarnings("unchecked")
    private static boolean locate(Minecraft minecraft) throws ReflectiveOperationException, IOException {
        AbstractTexture texture = minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        if (!(texture instanceof TextureAtlas atlas)) {
            return false;
        }
        List<?> states = (List<?>) field(TextureAtlas.class, "animatedTexturesStates").get(atlas);
        if (states == statesSeen) {
            return frames != null;
        }
        // A new atlas (first use or a resource reload): find our sprite's animation again.
        statesSeen = states;
        frames = null;
        shown = UNKNOWN;
        for (Object state : states) {
            Object animation = field(state.getClass(), "animationInfo").get(state);
            SpriteContents contents = (SpriteContents) field(animation.getClass(), "this$0").get(animation);
            if (SPRITE.equals(contents.name())) {
                frames = (Int2ObjectMap<GpuTextureView>) field(state.getClass(), "frameTexturesByIndex").get(state);
            }
        }
        closeSheets();
        vanillaSheet = read(minecraft, VANILLA_TEXTURE);
        swirlSheet = read(minecraft, SWIRL_TEXTURE);
        return frames != null;
    }

    /** Uploads every frame: vanilla's, or the swirl tinted {@code rgb}. */
    private static void paint(int rgb) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        int tint = rgb < 0 ? 0 : PortalPainter.swirlTint(rgb);
        for (Int2ObjectMap.Entry<GpuTextureView> entry : frames.int2ObjectEntrySet()) {
            int frame = entry.getIntKey();
            GpuTexture target = entry.getValue().texture();
            NativeImage image = new NativeImage(SIZE, SIZE, false);
            try {
                NativeImage sheet = rgb < 0 ? vanillaSheet : swirlSheet;
                int top = frame % (sheet.getHeight() / SIZE) * SIZE;
                for (int y = 0; y < SIZE; y++) {
                    for (int x = 0; x < SIZE; x++) {
                        int argb = sheet.getPixel(x, top + y);
                        image.setPixel(x, y, rgb < 0 ? argb : tinted(argb, tint));
                    }
                }
                upload(encoder, target, image);
            } finally {
                image.close();
            }
        }
        ColoredNetherPortalsMod.debug("Portal screen effect now {}", rgb < 0 ? "vanilla" : String.format("#%06X", rgb));
    }

    /** A grey swirl pixel in color {@code tint}, as opaque as vanilla's overlay. */
    private static int tinted(int argb, int tint) {
        int grey = argb & 0xFF;
        int r = (tint >> 16 & 0xFF) * grey / 255;
        int g = (tint >> 8 & 0xFF) * grey / 255;
        int b = (tint & 0xFF) * grey / 255;
        return ALPHA << 24 | r << 16 | g << 8 | b;
    }

    /** Mip 0 is the image; each further level averages 2x2 pixels of the one before. */
    private static void upload(CommandEncoder encoder, GpuTexture target, NativeImage image) {
        encoder.writeToTexture(target, image, 0, 0, 0, 0);
        NativeImage previous = image;
        for (int mip = 1; mip < target.getMipLevels(); mip++) {
            int size = Math.max(1, SIZE >> mip);
            NativeImage smaller = new NativeImage(size, size, false);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    smaller.setPixel(x, y, average(previous, x * 2, y * 2));
                }
            }
            encoder.writeToTexture(target, smaller, mip, 0, 0, 0);
            if (previous != image) {
                previous.close();
            }
            previous = smaller;
        }
        if (previous != image) {
            previous.close();
        }
    }

    private static int average(NativeImage image, int x, int y) {
        int a = 0;
        int r = 0;
        int g = 0;
        int b = 0;
        int n = 0;
        for (int dy = 0; dy < 2; dy++) {
            for (int dx = 0; dx < 2; dx++) {
                int px = Math.min(image.getWidth() - 1, x + dx);
                int py = Math.min(image.getHeight() - 1, y + dy);
                int argb = image.getPixel(px, py);
                a += argb >>> 24;
                r += argb >> 16 & 0xFF;
                g += argb >> 8 & 0xFF;
                b += argb & 0xFF;
                n++;
            }
        }
        return a / n << 24 | r / n << 16 | g / n << 8 | b / n;
    }

    private static NativeImage read(Minecraft minecraft, Identifier id) throws IOException {
        try (InputStream in = minecraft.getResourceManager().open(id)) {
            return NativeImage.read(in);
        }
    }

    private static void closeSheets() {
        if (vanillaSheet != null) {
            vanillaSheet.close();
            vanillaSheet = null;
        }
        if (swirlSheet != null) {
            swirlSheet.close();
            swirlSheet = null;
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
