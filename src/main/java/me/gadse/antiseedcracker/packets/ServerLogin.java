package me.gadse.antiseedcracker.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.InternalStructure;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.FieldAccessException;
import me.gadse.antiseedcracker.AntiSeedCracker;

public class ServerLogin extends PacketAdapter {

    private final AntiSeedCracker plugin;
    private volatile boolean warnedForOutdatedVersion = false;

    public ServerLogin(AntiSeedCracker plugin) {
        super(plugin, ListenerPriority.HIGHEST, PacketType.Play.Server.LOGIN);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        PacketContainer packet = event.getPacket();
        try {
            int structureSize = packet.getStructures().size();
            if (structureSize == 0) {
                plugin.getLogger().warning(
                        "Can not write hashed seed at login for player "
                                + event.getPlayer().getName() + ".");
                fallbackRewrite(packet);
                return;
            }
            InternalStructure structureModifier = packet.getStructures().read(structureSize - 1);
            if (structureModifier.getLongs().size() == 0) {
                fallbackRewrite(packet);
                return;
            }
            long original = structureModifier.getLongs().read(0);
            structureModifier.getLongs().write(0, plugin.randomizeHashedSeed(original));
        } catch (FieldAccessException | NullPointerException ex) {
            if (!warnedForOutdatedVersion) {
                if (ex instanceof FieldAccessException) {
                    plugin.getLogger().warning(
                            "You're running an unsupported version of Minecraft. Please update when possible.");
                } else {
                    plugin.getLogger().warning(
                            "You're running an old version of ProtocolLib. Please update it.");
                }
                warnedForOutdatedVersion = true;
            }
            fallbackRewrite(packet);
        } catch (Throwable t) {
            plugin.getLogger().warning(
                    "Unexpected error randomizing login hashed seed: " + t);
        }
        event.setPacket(packet);
    }

    private void fallbackRewrite(PacketContainer packet) {
        try {
            if (packet.getLongs().size() == 0) {
                return;
            }
            packet.getLongs().write(0, plugin.randomizeHashedSeed(packet.getLongs().read(0)));
        } catch (Throwable ignored) {
        }
    }
}
