package my.celium.org.colorednetherportals.neoforge;

import my.celium.org.client.ClientHooks;
import my.celium.org.colorednetherportals.ColoredNetherPortalsMod;
import my.celium.org.colorednetherportals.server.PortalColors;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * NeoForge entrypoint. Mycel wires the packets (optional both ways); dye
 * coloring runs on the server; client hooks live in {@link ClientColoredNetherPortals}.
 */
@Mod(ColoredNetherPortalsMod.ID)
public final class ColoredNetherPortalsNeoForge {
    public ColoredNetherPortalsNeoForge(IEventBus modBus, @SuppressWarnings("unused") ModContainer container) {
        if (!net.neoforged.fml.ModList.get().isLoaded("mycel")) {
            my.celium.org.colorednetherportals.MissingMycel.logOnce();
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                    (net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) -> {
                if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                    my.celium.org.colorednetherportals.MissingMycel.tell(player);
                }
            });
            return;
        }
        my.celium.org.colorednetherportals.ModGate.open();
        ColoredNetherPortalsMod.init();
        NeoForge.EVENT_BUS.addListener(ColoredNetherPortalsNeoForge::onRightClickBlock);
        if (FMLEnvironment.getDist().isClient()) {
            ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class,
                    () -> (ModContainer ignored, Screen parent) -> ClientHooks.createConfigScreen(parent,
                            ColoredNetherPortalsMod.ID));
            ClientColoredNetherPortals.register();
        }
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide() && PortalColors.onUse(event.getEntity(), event.getHand(), event.getPos())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }
}
