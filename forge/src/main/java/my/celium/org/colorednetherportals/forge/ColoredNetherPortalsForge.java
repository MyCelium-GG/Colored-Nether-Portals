package my.celium.org.colorednetherportals.forge;

import my.celium.org.client.ClientHooks;
import my.celium.org.colorednetherportals.ColoredNetherPortalsMod;
import my.celium.org.colorednetherportals.server.PortalColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge entrypoint: packets travel on Mycel's shared channel (registered in
 * {@link ColoredNetherPortalsMod#init()}); dye coloring runs on the server;
 * client hooks live in {@link ClientColoredNetherPortals} so dedicated servers
 * never load client classes.
 */
@Mod(ColoredNetherPortalsMod.ID)
public final class ColoredNetherPortalsForge {
    public ColoredNetherPortalsForge(@SuppressWarnings("unused") FMLJavaModLoadingContext context) {
        ColoredNetherPortalsMod.init();
        PlayerInteractEvent.RightClickBlock.BUS.addListener(ColoredNetherPortalsForge::onRightClickBlock);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory(
                            (Minecraft minecraft, Screen parent) -> ClientHooks.createConfigScreen(parent,
                                    ColoredNetherPortalsMod.ID)));
            ClientColoredNetherPortals.register();
        }
    }

    /** Returning {@code true} cancels the click (Forge 26.x cancellable-bus style). */
    private static boolean onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide() && PortalColors.onUse(event.getEntity(), event.getHand(), event.getPos())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            return true;
        }
        return false;
    }
}
