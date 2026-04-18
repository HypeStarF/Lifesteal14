package me.sdmannen.lifesteal14.game;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

public class LobbyCageManager {

    private final JavaPlugin plugin;

    private World world;
    private int minX;
    private int maxX;
    private int minY;
    private int maxY;
    private int minZ;
    private int maxZ;
    private Location center;
    private boolean created;

    public LobbyCageManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void createCage() {
        world = plugin.getServer().getWorlds().get(0);

        int centerX = world.getSpawnLocation().getBlockX();
        int centerZ = world.getSpawnLocation().getBlockZ();
        int floorY = world.getHighestBlockYAt(centerX, centerZ) + 1;

        minX = centerX - 3;
        maxX = centerX + 3;
        minY = floorY + 20;
        maxY = floorY + 4;
        minZ = centerZ - 3;
        maxZ = centerZ + 3;

        center = new Location(world, centerX + 0.5, floorY + 1.0, centerZ + 0.5);

        rebuildCage();
        created = true;
    }

    public void rebuildCage() {
        if (world == null) {
            return;
        }

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean wall = x == minX || x == maxX || z == minZ || z == maxZ;
                    boolean floor = y == minY;
                    boolean roof = y == maxY;

                    if (wall || floor || roof) {
                        world.getBlockAt(x, y, z).setType(Material.GLASS, false);
                    } else {
                        world.getBlockAt(x, y, z).setType(Material.AIR, false);
                    }
                }
            }
        }

        created = true;
    }

    public void removeCage() {
        if (!created || world == null) {
            return;
        }

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (world.getBlockAt(x, y, z).getType() == Material.GLASS) {
                        world.getBlockAt(x, y, z).setType(Material.AIR, false);
                    }
                }
            }
        }

        created = false;
    }

    public void restoreState(World world, int minX, int maxX, int minY, int maxY, int minZ, int maxZ, Location center, boolean created) {
        this.world = world;
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.center = center;
        this.created = created;
    }

    public void teleportToCage(org.bukkit.entity.Player player) {
        if (center != null) {
            player.teleport(center);
        }
    }

    public boolean isCreated() {
        return created;
    }

    public World getWorld() {
        return world;
    }

    public int getMinX() {
        return minX;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public Location getCenter() {
        return center;
    }
}