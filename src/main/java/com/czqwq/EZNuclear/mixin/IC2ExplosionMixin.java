package com.czqwq.EZNuclear.mixin;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.czqwq.EZNuclear.Config;

import cpw.mods.fml.common.FMLCommonHandler;
import gregtech.api.util.GTUtility;

@Mixin(value = ic2.core.ExplosionIC2.class, remap = false)
public class IC2ExplosionMixin {

    @Unique
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "EZNuclear-IC2ExplosionScheduler");
        t.setDaemon(true);
        return t;
    });

    static {
        System.out.println("[EZNuclear] IC2ExplosionMixin loaded");
    }

    // Flag to allow the deferred explosion to run once without being re-cancelled
    @Unique
    private volatile boolean eznuclear_ignoreNext = false;

    @Inject(method = "doExplosion", at = @At("HEAD"), cancellable = true)
    private void onDoExplosion(CallbackInfo ci) {
        // Check if IC2 explosions are disabled in config
        if (!Config.IC2Explosion) {
            // Even if explosion is disabled, still send the message to players
            MinecraftServer server = FMLCommonHandler.instance()
                .getMinecraftServerInstance();
            if (server != null) {
                if (!server.isSinglePlayer()) {
                    List<EntityPlayerMP> players = server.getConfigurationManager().playerEntityList;
                    for (EntityPlayerMP p : players) {
                        GTUtility.sendChatToPlayer(p, StatCollector.translateToLocal("info.ezunclear"));
                    }
                } else {
                    // For single player, try to send message to client
                    try {
                        Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
                        Object mc = mcClass.getMethod("getMinecraft")
                            .invoke(null);
                        Object thePlayer = mcClass.getField("thePlayer")
                            .get(mc);
                        if (thePlayer != null) {
                            Class<?> chatClass = Class.forName("net.minecraft.util.ChatComponentTranslation");
                            Object chat = chatClass.getConstructor(String.class, Object[].class)
                                .newInstance(new Object[] { "info.ezunclear", new Object[0] });
                            Class<?> iChatClass = Class.forName("net.minecraft.util.IChatComponent");
                            thePlayer.getClass()
                                .getMethod("addChatMessage", iChatClass)
                                .invoke(thePlayer, chat);
                        }
                    } catch (Throwable t) {
                        // Reflection failed, skipping client chat message
                    }
                }

                // Schedule the second message after 5 seconds
                SCHEDULER.schedule(() -> {
                    MinecraftServer srv = FMLCommonHandler.instance()
                        .getMinecraftServerInstance();
                    if (srv != null) {
                        if (!srv.isSinglePlayer()) {
                            List<EntityPlayerMP> players = srv.getConfigurationManager().playerEntityList;
                            for (EntityPlayerMP p : players) {
                                GTUtility.sendChatToPlayer(
                                    p,
                                    StatCollector.translateToLocal("info.ezunclear.preventexplosion"));
                            }
                        } else {
                            try {
                                Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
                                Object mc = mcClass.getMethod("getMinecraft")
                                    .invoke(null);
                                Object thePlayer = mcClass.getField("thePlayer")
                                    .get(mc);
                                if (thePlayer != null) {
                                    Class<?> chatClass = Class.forName("net.minecraft.util.ChatComponentTranslation");
                                    Object chat = chatClass.getConstructor(String.class, Object[].class)
                                        .newInstance(new Object[] { "info.ezunclear.preventexplosion", new Object[0] });
                                    Class<?> iChatClass = Class.forName("net.minecraft.util.IChatComponent");
                                    thePlayer.getClass()
                                        .getMethod("addChatMessage", iChatClass)
                                        .invoke(thePlayer, chat);
                                }
                            } catch (Throwable t) {
                                // Reflection failed, skipping client chat message
                            }
                        }
                    }
                }, 5L, TimeUnit.SECONDS);
            }

            ci.cancel();
            return;
        }

        // If this is the deferred invocation, allow it to proceed once
        if (eznuclear_ignoreNext) {
            eznuclear_ignoreNext = false;
            return;
        }

        // Only run on server side -- use server global instead of shadowing world
        MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();
        if (server == null) return;

        // Send initial message to players (run on server thread directly)
        try {
            server.getConfigurationManager()
                .sendChatMsg(new ChatComponentTranslation("info.ezunclear"));
        } catch (Throwable t) {
            t.printStackTrace();
        }

        // Cancel immediate explosion and schedule the real one after 5 seconds
        ci.cancel();

        SCHEDULER.schedule(() -> {
            MinecraftServer s = FMLCommonHandler.instance()
                .getMinecraftServerInstance();
            if (s != null) {
                try {
                    s.getConfigurationManager()
                        .sendChatMsg(new ChatComponentTranslation("info.ezunclear.interact"));
                } catch (Throwable t) {
                    t.printStackTrace();
                }

                // Set flag so the next doExplosion invocation is allowed through
                eznuclear_ignoreNext = true;

                // Invoke the original method on the target instance
                try {
                    ((ic2.core.ExplosionIC2) (Object) this).doExplosion();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }, 5L, TimeUnit.SECONDS);
    }
}
