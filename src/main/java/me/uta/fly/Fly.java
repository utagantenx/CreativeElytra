package me.uta.fly;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class Fly extends JavaPlugin implements Listener {

    private static final String PERMISSION =
            "CreativeElytra.allow";

    private static final int MAX_DAMAGE = 432;

    private double blocksPerDurability;
    private int durabilityPerBlocks;

    private boolean requirePermission;

    private NamespacedKey distanceKey;

    @Override
    public void onEnable() {

        saveDefaultConfig();

        loadConfigValues();

        distanceKey = new NamespacedKey(
                this,
                "elytra_distance"
        );

        getServer()
                .getPluginManager()
                .registerEvents(this, this);

        getLogger().info("================================");
        getLogger().info("       CreativeElytra Enabled");
        getLogger().info("       By: UTA GANTENX");
        getLogger().info("================================");
    }

    private void loadConfigValues() {

        requirePermission = getConfig().getBoolean(
                "require-permission",
                false
        );

        blocksPerDurability = Math.max(
                0.1,
                getConfig().getDouble(
                        "blocks-per-durability",
                        10.0
                )
        );

        durabilityPerBlocks = Math.max(
                1,
                getConfig().getInt(
                        "durability-per-blocks",
                        1
                )
        );
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {

        Player player = event.getPlayer();

        /*
         * Creative dan Spectator tidak disentuh.
         *
         * Tidak ada pengaturan fly speed
         * dari plugin.
         */
        if (player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) {

            return;
        }

        ItemStack elytra =
                player.getInventory().getChestplate();

        /*
         * Tidak memakai Elytra.
         */
        if (elytra == null
                || elytra.getType() != Material.ELYTRA) {

            disableFlight(player);
            return;
        }

        /*
         * Cek permission.
         */
        if (requirePermission
                && !player.hasPermission(PERMISSION)) {

            disableFlight(player);
            return;
        }

        if (!(elytra.getItemMeta()
                instanceof Damageable meta)) {

            disableFlight(player);
            return;
        }

        int damage = meta.getDamage();

        /*
         * Elytra sudah mencapai 432 damage.
         *
         * Item tetap ada, tetapi flight plugin
         * tidak dapat digunakan.
         */
        if (damage >= MAX_DAMAGE) {

            disableFlight(player);
            return;
        }

        /*
         * Izinkan flight.
         */
        player.setAllowFlight(true);

        /*
         * Kalau belum sedang terbang,
         * tidak menghitung jarak.
         */
        if (!player.isFlying()) {
            return;
        }

        if (event.getTo() == null) {
            return;
        }

        double dx =
                event.getTo().getX()
                        - event.getFrom().getX();

        double dy =
                event.getTo().getY()
                        - event.getFrom().getY();

        double dz =
                event.getTo().getZ()
                        - event.getFrom().getZ();

        double distance = Math.sqrt(
                dx * dx
                        + dy * dy
                        + dz * dz
        );

        /*
         * Tidak bergerak = tidak mengurangi durability.
         */
        if (distance <= 0.0) {
            return;
        }

        addFlightDistance(
                player,
                elytra,
                distance
        );
    }

    /**
     * Vanilla Elytra damage dibatalkan.
     *
     * Durability hanya berkurang melalui
     * sistem jarak plugin.
     */
    @EventHandler
    public void onElytraDamage(
            PlayerItemDamageEvent event) {

        ItemStack item = event.getItem();

        if (item.getType() == Material.ELYTRA) {
            event.setCancelled(true);
        }
    }

    /**
     * Menghitung jarak terbang.
     */
    private void addFlightDistance(
            Player player,
            ItemStack elytra,
            double distance) {

        if (!(elytra.getItemMeta()
                instanceof Damageable meta)) {
            return;
        }

        Double currentDistance =
                meta.getPersistentDataContainer().get(
                        distanceKey,
                        PersistentDataType.DOUBLE
                );

        if (currentDistance == null) {
            currentDistance = 0.0;
        }

        currentDistance += distance;

        /*
         * Berapa kali batas blok tercapai.
         */
        int triggers = (int) Math.floor(
                currentDistance
                        / blocksPerDurability
        );

        if (triggers <= 0) {

            saveDistance(
                    elytra,
                    meta,
                    currentDistance
            );

            return;
        }

        /*
         * Simpan sisa jarak.
         */
        double remainingDistance =
                currentDistance
                        - (triggers * blocksPerDurability);

        /*
         * Hitung damage.
         */
        int totalDamage =
                triggers * durabilityPerBlocks;

        int currentDamage =
                meta.getDamage();

        int newDamage =
                currentDamage + totalDamage;

        /*
         * Maksimal 432.
         *
         * Elytra tidak dihancurkan.
         */
        newDamage =
                Math.min(
                        MAX_DAMAGE,
                        newDamage
                );

        meta.setDamage(newDamage);

        /*
         * Simpan sisa jarak.
         */
        meta.getPersistentDataContainer().set(
                distanceKey,
                PersistentDataType.DOUBLE,
                remainingDistance
        );

        elytra.setItemMeta(meta);

        /*
         * Kalau sudah 432,
         * flight dimatikan.
         */
        if (newDamage >= MAX_DAMAGE) {

            disableFlight(player);
        }
    }

    /**
     * Menyimpan sisa jarak fractional.
     */
    private void saveDistance(
            ItemStack elytra,
            Damageable meta,
            double distance) {

        meta.getPersistentDataContainer().set(
                distanceKey,
                PersistentDataType.DOUBLE,
                distance
        );

        elytra.setItemMeta(meta);
    }

    /**
     * Mematikan flight.
     */
    private void disableFlight(Player player) {

        player.setAllowFlight(false);
        player.setFlying(false);
    }
}