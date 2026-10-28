package my.celium.org.colorednetherportals.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;

/**
 * Writes images into an animated sprite's frame textures (the values of its
 * {@code frameTexturesByIndex}). The GPU classes moved and changed between
 * Minecraft lines; this is the 26.3 copy (one per line in common/src/mc).
 */
final class FrameWriter {
    private final CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

    /** Mip levels of {@code frame}, a {@code GpuTextureView}. */
    int mipLevels(Object frame) {
        return ((GpuTextureView) frame).texture().getMipLevels();
    }

    /** Writes {@code image} into mip level {@code mip} of {@code frame}, at its top left corner. */
    void write(Object frame, NativeImage image, int mip) {
        GpuTexture target = ((GpuTextureView) frame).texture();
        encoder.writeToTexture(target, image, mip, 0, 0, 0);
    }
}
