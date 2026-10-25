package my.celium.org.colorednetherportals.fabric;

import my.celium.org.colorednetherportals.ColoredNetherPortalsMod;
import my.celium.org.colorednetherportals.server.PortalColors;
import my.celium.org.fabric.FabricNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionResult;

/**
 * Fabric entrypoint: bootstrap, the packets Mycel knows about (they may be
 * registered after Mycel's own entrypoint ran), and dye coloring on the server.
 */
public final class ColoredNetherPortalsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ColoredNetherPortalsMod.init();
        FabricNetworking.flush();
        UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                !level.isClientSide() && PortalColors.onUse(player, hand, hit.getBlockPos())
                        ? InteractionResult.SUCCESS
                        : InteractionResult.PASS);
    }
}
