package org.clockworx.cotr.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.clockworx.cotr.bank.exchange.EmeraldTracker;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * EmeraldTrackingListener - Tracks emerald inflow from all configured sources.
 *
 * Sources include:
 * - Mining emerald ore
 * - Loot table generation
 * - Mob drops
 * - Villager trading
 */
public class EmeraldTrackingListener implements Listener {
    private final EmeraldTracker tracker;

    public EmeraldTrackingListener(@NotNull EmeraldTracker tracker) {
        this.tracker = tracker;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(@NotNull BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        if (type != Material.EMERALD_ORE && type != Material.DEEPSLATE_EMERALD_ORE) {
            return;
        }
        
        Player player = event.getPlayer();
        Collection<ItemStack> drops = block.getDrops(player.getInventory().getItemInMainHand(), player);
        int emeralds = countEmeralds(drops);
        if (emeralds > 0) {
            tracker.trackMined(block.getLocation(), emeralds);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLootGenerate(@NotNull LootGenerateEvent event) {
        int emeralds = countEmeralds(event.getLoot());
        if (emeralds <= 0) {
            return;
        }
        
        Location location = event.getLootContext() != null ? event.getLootContext().getLocation() : null;
        if (location != null) {
            tracker.trackLoot(location, emeralds);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(@NotNull EntityDeathEvent event) {
        Entity entity = event.getEntity();
        int emeralds = countEmeralds(event.getDrops());
        if (emeralds > 0) {
            tracker.trackMobDrops(entity.getLocation(), emeralds);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTradeResultClick(@NotNull InventoryClickEvent event) {
        if (!(event.getInventory() instanceof MerchantInventory)) {
            return;
        }
        if (event.getSlotType() != InventoryType.SlotType.RESULT) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        ItemStack result = event.getCurrentItem();
        if (result != null && result.getType() == Material.EMERALD) {
            Player player = (Player) event.getWhoClicked();
            tracker.trackTrading(player.getLocation(), result.getAmount());
        }
    }

    private int countEmeralds(@NotNull Collection<ItemStack> items) {
        int total = 0;
        for (ItemStack item : items) {
            if (item != null && item.getType() == Material.EMERALD) {
                total += item.getAmount();
            }
        }
        return total;
    }
}
