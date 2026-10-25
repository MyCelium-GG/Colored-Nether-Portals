package my.celium.org.colorednetherportals.fabric;

import my.celium.org.colorednetherportals.client.ClientPortals;
import my.celium.org.fabric.FabricNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

/** Fabric client entrypoint: portal tints every tick, and sneak + right-click opens the color menu. */
public final class ColoredNetherPortalsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricNetworking.flushClient();
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientPortals.tick());
        UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                level.isClientSide() && hand == InteractionHand.MAIN_HAND
                        && ClientPortals.onUse(hit.getBlockPos(), player.isShiftKeyDown(), player.getItemInHand(hand).isEmpty())
                        ? InteractionResult.SUCCESS
                        : InteractionResult.PASS);
    }
}
