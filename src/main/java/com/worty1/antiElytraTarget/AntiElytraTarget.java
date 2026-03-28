package com.worty1.antiElytraTarget;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class AntiElytraTarget extends JavaPlugin implements Listener {

    private static AntiElytraTarget instance;
    private final Map<UUID, PlayerData> playerData = new ConcurrentHashMap<>();
    private final Map<UUID, Long> punishedPlayers = new ConcurrentHashMap<>();
    private ConfigCache cache;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        reloadCache();
        getServer().getPluginManager().registerEvents(this, this);

        new BukkitRunnable() {
            public void run() {
                long now = System.currentTimeMillis();
                punishedPlayers.entrySet().removeIf(e -> {
                    if (now > e.getValue()) {
                        Player p = Bukkit.getPlayer(e.getKey());
                        if (p != null) p.sendMessage(cache.msgPunishEnded);
                        return true;
                    }
                    return false;
                });
            }
        }.runTaskTimerAsynchronously(this, 20L, 20L);
    }

    private void reloadCache() {
        reloadConfig();
        cache = new ConfigCache();

        cache.maxViolations = getConfig().getInt("settings.max-violations");
        cache.resetMillis = getConfig().getLong("settings.reset-seconds") * 1000L;
        cache.warnThreshold = getConfig().getInt("settings.warning-threshold");
        cache.punishDuration = getConfig().getLong("settings.punishment-duration-seconds") * 1000L;
        cache.banCommand = getConfig().getString("settings.ban-command");
        cache.banReason = getConfig().getString("settings.ban-reason");
        cache.discordWebhook = getConfig().getString("discord.webhook-url");
        cache.debug = getConfig().getBoolean("settings.debug");

        cache.msgWarning = color(getConfig().getString("messages.warning"));
        cache.msgPunished = color(getConfig().getString("messages.punishment"));
        cache.msgPunishEnded = color(getConfig().getString("messages.punishment-ended"));
        cache.msgCannotAttack = color(getConfig().getString("messages.attack-blocked"));
        cache.msgFireworkBlock = color(getConfig().getString("messages.firework-blocked"));
        cache.msgStaffAlert = color(getConfig().getString("messages.staff-warning"));
        cache.msgNoPerm = color(getConfig().getString("messages.no-permission"));
        cache.msgReload = color(getConfig().getString("messages.reloaded"));
        cache.msgNotFound = color(getConfig().getString("messages.player-not-found"));
        cache.msgCleared = color(getConfig().getString("messages.data-cleared"));

        cache.msgNoData = color(getConfig().getString("messages.no-data"));
        cache.msgViolationInfo = color(getConfig().getString("messages.violation-info"));
        cache.msgLastSwitch = color(getConfig().getString("messages.last-switch"));
        cache.msgPunishActive = color(getConfig().getString("messages.punishment-active"));
        cache.msgPunishNone = color(getConfig().getString("messages.punishment-none"));
        cache.msgPunishFinished = color(getConfig().getString("messages.punishment-finished"));

        cache.cmdUsageStatus = color(getConfig().getString("commands.usage-status"));
        cache.cmdUsageClear = color(getConfig().getString("commands.usage-clear"));

        cache.helpHeader = color(getConfig().getString("help.header"));
        cache.helpReload = color(getConfig().getString("help.reload"));
        cache.helpStatus = color(getConfig().getString("help.status"));
        cache.helpClear = color(getConfig().getString("help.clear"));

        cache.effectLightning = getConfig().getBoolean("effects.lightning");
        cache.effectSound = getConfig().getBoolean("effects.sound");
        cache.effectParticle = getConfig().getBoolean("effects.particle");
    }

    @EventHandler
    public void onHotbarSwitch(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        if (!p.isGliding()) return;

        ItemStack chest = p.getInventory().getChestplate();
        if (chest == null || chest.getType() != Material.ELYTRA) return;

        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        PlayerData d = playerData.computeIfAbsent(id, k -> new PlayerData());

        if (now - d.lastSwitch > cache.resetMillis) {
            d.reset();
            punishedPlayers.remove(id);
        }

        if (d.lastSwitch != 0 && now - d.lastSwitch <= 1L) {
            d.violations++;
            int v = d.violations;

            if (v == cache.warnThreshold) warn(p, v);
            if (v > cache.warnThreshold && !punishedPlayers.containsKey(id)) punish(p, v);
            if (v >= cache.maxViolations) ban(p);

            alert(p, v);
        }

        d.lastSwitch = now;
    }

    private void warn(Player p, int v) {
        p.sendMessage(cache.msgWarning.replace("%count%", v+"").replace("%max%", cache.maxViolations+""));
    }

    private void punish(Player p, int v) {
        long end = System.currentTimeMillis() + cache.punishDuration;
        punishedPlayers.put(p.getUniqueId(), end);
        p.sendMessage(cache.msgPunished.replace("%duration%", (cache.punishDuration/1000)+""));
    }

    private void ban(Player p) {
        String cmd = cache.banCommand.replace("%player%", p.getName()).replace("%reason%", cache.banReason);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }

    private void alert(Player p, int v) {
        String msg = cache.msgStaffAlert.replace("%player%", p.getName())
                .replace("%count%", v+"")
                .replace("%max%", cache.maxViolations+"");

        Bukkit.getOnlinePlayers().stream()
                .filter(pl -> pl.hasPermission("aet.alert"))
                .forEach(pl -> pl.sendMessage(msg));
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public static AntiElytraTarget getInstance() {
        return instance;
    }

    private static class ConfigCache {
        int maxViolations, warnThreshold;
        long resetMillis, punishDuration;
        String banCommand, banReason, discordWebhook;
        boolean debug, effectLightning, effectSound, effectParticle;

        String msgWarning, msgPunished, msgPunishEnded, msgCannotAttack, msgFireworkBlock;
        String msgStaffAlert, msgNoPerm, msgReload, msgNotFound, msgCleared;

        String msgNoData, msgViolationInfo, msgLastSwitch, msgPunishActive, msgPunishNone, msgPunishFinished;

        String cmdUsageStatus, cmdUsageClear;

        String helpHeader, helpReload, helpStatus, helpClear;
    }

    private static class PlayerData {
        int violations;
        long lastSwitch;
        void reset() { violations = 0; lastSwitch = 0; }
    }
}