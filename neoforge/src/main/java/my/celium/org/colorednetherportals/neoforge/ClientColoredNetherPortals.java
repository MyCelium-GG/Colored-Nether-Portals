package my.celium.org.colorednetherportals.neoforge;

import my.celium.org.colorednetherportals.client.ClientPortals;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** NeoForge client hooks: portal tints every tick, and sneak + right-click opens the color menu. */
public final class ClientColoredNetherPortals {
    private ClientColoredNetherPortals() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientColoredNetherPortals::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientColoredNetherPortals::onRightClickBlock);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        ClientPortals.tick();
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() && event.getHand() == InteractionHand.MAIN_HAND
                && ClientPortals.onUse(event.getPos(), event.getEntity().isShiftKeyDown(),
                        event.getEntity().getItemInHand(event.getHand()).isEmpty())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }
}
