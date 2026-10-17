package com.crystaltides.core.modules;

import com.crystaltides.core.CrystalCore;
import com.crystaltides.core.api.CrystalModule;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Módulo de Bolsa de Valores Dinámica de Recursos (Marketplace).
 * Gestiona precios fluctuantes basados en oferta y demanda, persistidos en la base de datos.
 */
public class MarketplaceModule extends CrystalModule {

    public static class MarketItem {
        private final String id;
        private final String displayName;
        private final Material material;
        private final double basePrice;
        private final double minPrice;
        private final double maxPrice;
        private double currentPrice;
        private long totalSold;
        private long totalBought;

        public MarketItem(String id, String displayName, Material material, double basePrice, double minPrice, double maxPrice) {
            this.id = id;
            this.displayName = displayName;
            this.material = material;
            this.basePrice = basePrice;
            this.minPrice = minPrice;
            this.maxPrice = maxPrice;
            this.currentPrice = basePrice;
            this.totalSold = 0;
            this.totalBought = 0;
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public Material getMaterial() { return material; }
        public double getCurrentPrice() { return currentPrice; }

        public synchronized void registerSale(int amount) {
            totalSold += amount;
            // A mayor oferta (ventas), el precio cae gradualmente (-0.5% cada 10 unidades)
            double drop = (amount * 0.0005) * basePrice;
            currentPrice = Math.max(minPrice, currentPrice - drop);
        }

        public synchronized void registerPurchase(int amount) {
            totalBought += amount;
            // A mayor demanda (compras), el precio sube gradualmente (+0.5% cada 10 unidades)
            double rise = (amount * 0.0005) * basePrice;
            currentPrice = Math.min(maxPrice, currentPrice + rise);
        }
    }

    private final Map<String, MarketItem> marketCatalog = new ConcurrentHashMap<>();

    public MarketplaceModule(CrystalCore plugin) {
        super(plugin, "Marketplace");
    }

    @Override
    public void onEnable() {
        super.onEnable();

        initializeCatalog();
        initDatabaseTable();
        startPriceSyncTask();

        plugin.getLogger().info("📈 Módulo Marketplace (Bolsa de Valores Dinámica) habilitado.");
    }

    private void initializeCatalog() {
        // Recursos clave vainilla y mods (minerales, comida mítica, agricultura)
        registerItem(new MarketItem("diamante", "Diamante", Material.DIAMOND, 100.0, 30.0, 350.0));
        registerItem(new MarketItem("netherite", "Lingote de Netherite", Material.NETHERITE_INGOT, 800.0, 250.0, 2500.0));
        registerItem(new MarketItem("hierro", "Lingote de Hierro", Material.IRON_INGOT, 15.0, 5.0, 60.0));
        registerItem(new MarketItem("oro", "Lingote de Oro", Material.GOLD_INGOT, 35.0, 10.0, 120.0));
        registerItem(new MarketItem("esmeralda", "Esmeralda", Material.EMERALD, 40.0, 12.0, 150.0));
        registerItem(new MarketItem("trigo", "Trigo de Granja", Material.WHEAT, 2.0, 0.5, 10.0));
        registerItem(new MarketItem("zanahoria", "Zanahoria", Material.CARROT, 2.5, 0.8, 12.0));
        registerItem(new MarketItem("patata", "Patata", Material.POTATO, 2.5, 0.8, 12.0));
        registerItem(new MarketItem("obsidiana", "Obsidiana", Material.OBSIDIAN, 8.0, 2.0, 30.0));
        registerItem(new MarketItem("perla_ender", "Perla de Ender", Material.ENDER_PEARL, 20.0, 5.0, 80.0));
    }

    private void registerItem(MarketItem item) {
        marketCatalog.put(item.getId().toLowerCase(Locale.ROOT), item);
    }

    public MarketItem getItem(String id) {
        if (id == null) return null;
        return marketCatalog.get(id.toLowerCase(Locale.ROOT));
    }

    public Map<String, MarketItem> getCatalog() {
        return marketCatalog;
    }

    private void initDatabaseTable() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseModule databaseModule = plugin.getModuleManager().getModule(DatabaseModule.class);
            if (databaseModule == null) return;
            try (Connection conn = databaseModule.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "CREATE TABLE IF NOT EXISTS market_prices (" +
                                 "item_id VARCHAR(64) PRIMARY KEY, " +
                                 "display_name VARCHAR(128) NOT NULL, " +
                                 "current_price DOUBLE NOT NULL, " +
                                 "total_sold BIGINT DEFAULT 0, " +
                                 "total_bought BIGINT DEFAULT 0, " +
                                 "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                                 ")")) {
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "No se pudo inicializar la tabla market_prices", e);
            }
        });
    }

    private void startPriceSyncTask() {
        // Sincronizar cotizaciones con MySQL cada 5 minutos
        long interval = 20L * 60 * 5;
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            DatabaseModule databaseModule = plugin.getModuleManager().getModule(DatabaseModule.class);
            if (databaseModule == null) return;
            try (Connection conn = databaseModule.getConnection()) {
                String sql = "INSERT INTO market_prices (item_id, display_name, current_price, total_sold, total_bought) " +
                             "VALUES (?, ?, ?, ?, ?) " +
                             "ON DUPLICATE KEY UPDATE current_price = VALUES(current_price), " +
                             "total_sold = VALUES(total_sold), total_bought = VALUES(total_bought)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    for (MarketItem item : marketCatalog.values()) {
                        stmt.setString(1, item.getId());
                        stmt.setString(2, item.getDisplayName());
                        stmt.setDouble(3, item.getCurrentPrice());
                        stmt.setLong(4, item.totalSold);
                        stmt.setLong(5, item.totalBought);
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Error al sincronizar cotizaciones del mercado", e);
            }
        }, interval, interval);
    }

    public boolean sellItem(Player player, MarketItem item, int amount) {
        if (amount <= 0) return false;
        int countInInventory = countItems(player, item.getMaterial());

        if (countInInventory < amount) {
            player.sendMessage(Component.text("❌ No tienes suficientes " + item.getDisplayName() + " en tu inventario (Tienes: " + countInInventory + ")", NamedTextColor.RED));
            return false;
        }

        double unitPrice = item.getCurrentPrice();
        double totalGain = unitPrice * amount;

        removeItems(player, item.getMaterial(), amount);
        item.registerSale(amount);

        // Añadir KilluCoins al CrystalProfile del jugador
        ProfileModule profileModule = plugin.getModuleManager().getModule(ProfileModule.class);
        if (profileModule != null) {
            com.crystaltides.core.profile.CrystalProfile profile = profileModule.getProfile(player.getUniqueId());
            if (profile != null) {
                profile.setKillucoins(profile.getKillucoins() + (long) totalGain);
            }
        }

        player.sendMessage(Component.text("📈 Venta exitosa: ", NamedTextColor.GREEN, TextDecoration.BOLD)
                .append(Component.text(amount + "x " + item.getDisplayName(), NamedTextColor.WHITE))
                .append(Component.text(" por ", NamedTextColor.GRAY))
                .append(Component.text(String.format(Locale.US, "%.2f KC", totalGain), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(String.format(Locale.US, " (%.2f KC/u)", item.getCurrentPrice()), NamedTextColor.DARK_GRAY)));

        return true;
    }

    public boolean buyItem(Player player, MarketItem item, int amount) {
        if (amount <= 0) return false;
        ProfileModule profileModule = plugin.getModuleManager().getModule(ProfileModule.class);
        com.crystaltides.core.profile.CrystalProfile profile = profileModule != null ? profileModule.getProfile(player.getUniqueId()) : null;
        long currentBalance = profile != null ? profile.getKillucoins() : 0;
        double unitPrice = item.getCurrentPrice();
        double totalCost = unitPrice * amount;

        if (currentBalance < (long) totalCost) {
            player.sendMessage(Component.text(String.format(Locale.US, "❌ Fondos insuficientes. Requieres %.2f KC (Tienes: %d KC)", totalCost, currentBalance), NamedTextColor.RED));
            return false;
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(Component.text("❌ Tu inventario está lleno. Libera espacio antes de comprar.", NamedTextColor.RED));
            return false;
        }

        if (profile != null) {
            profile.setKillucoins(Math.max(0, profile.getKillucoins() - (long) totalCost));
        }

        item.registerPurchase(amount);
        player.getInventory().addItem(new ItemStack(item.getMaterial(), amount));

        player.sendMessage(Component.text("📉 Compra exitosa: ", NamedTextColor.AQUA, TextDecoration.BOLD)
                .append(Component.text(amount + "x " + item.getDisplayName(), NamedTextColor.WHITE))
                .append(Component.text(" por ", NamedTextColor.GRAY))
                .append(Component.text(String.format(Locale.US, "%.2f KC", totalCost), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(String.format(Locale.US, " (Nuevo precio: %.2f KC/u)", item.getCurrentPrice()), NamedTextColor.DARK_GRAY)));

        return true;
    }

    private int countItems(Player player, Material material) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    private void removeItems(Player player, Material material, int amountToRemove) {
        int left = amountToRemove;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack != null && stack.getType() == material) {
                if (stack.getAmount() <= left) {
                    left -= stack.getAmount();
                    player.getInventory().setItem(i, null);
                } else {
                    stack.setAmount(stack.getAmount() - left);
                    left = 0;
                }
                if (left <= 0) break;
            }
        }
    }
}
