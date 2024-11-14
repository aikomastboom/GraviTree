package me.ryanhamshire.GraviTree;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

public class GraviTree extends JavaPlugin implements Listener {
    static GraviTree instance;

    //adds a server log entry
    public static void AddLogEntry(String entry) {
        instance.getLogger().info("GraviTree: " + entry);
    }

    private boolean config_universalPermission;
    private String config_chopInfoMessage;
    private String config_chopOn;
    private String config_chopOff;
    private String config_disabledForWorld;
    private boolean config_canDisable;
    boolean config_chopModeOnByDefault;
    private boolean config_overworld_only;
    private boolean config_logs_damage_players;
    private boolean config_allow_blocks_dropping;
    private Set<String> config_ignored_worlds;

    //initializes well...   everything
    public void onEnable() {
        GraviTree.instance = this;

        //read configuration settings (note defaults)
        this.getDataFolder().mkdirs();
        File configFile = new File(this.getDataFolder().getPath() + File.separatorChar + "config.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        FileConfiguration outConfig = new YamlConfiguration();

        this.config_universalPermission = config.getBoolean("All Players Have Permission", true);
        outConfig.set("All Players Have Permission", this.config_universalPermission);

        this.config_chopModeOnByDefault = config.getBoolean("Chop Mode Defaults ON", true);
        outConfig.set("Chop Mode Defaults ON", this.config_chopModeOnByDefault);

        this.config_chopInfoMessage = config.getString("Messages.Chop Toggle Info", "You can toggle falling tree blocks with /TreesFall.");
        outConfig.set("Messages.Chop Toggle Info", this.config_chopInfoMessage);

        this.config_chopOn = config.getString("Messages.Chop Toggle On", "Falling tree blocks enabled.");
        outConfig.set("Messages.Chop Toggle On", this.config_chopOn);

        this.config_chopOff = config.getString("Messages.Chop Toggle Off", "Falling tree blocks disabled.");
        outConfig.set("Messages.Chop Toggle Off", this.config_chopOff);

        this.config_disabledForWorld = config.getString("Messages.Disabled for World", "Disabled for this world.");
        outConfig.set("Messages.Disabled for World", this.config_disabledForWorld);

        this.config_canDisable = config.getBoolean("Players Can Disable", true);
        outConfig.set("Players Can Disable", this.config_canDisable);

        this.config_overworld_only = config.getBoolean("LogsOnlyFallInOverworld", true);
        outConfig.set("LogsOnlyFallInOverworld", this.config_overworld_only);

        this.config_logs_damage_players = config.getBoolean("FallingLogsDamagePlayers", false);
        outConfig.set("FallingLogsDamagePlayers", this.config_logs_damage_players);

        this.config_allow_blocks_dropping = config.getBoolean("AllowBlocksDropping", false);
        outConfig.set("AllowBlocksDropping", this.config_allow_blocks_dropping);

        List<?> ignored_worlds_objects = config.getList("IgnoredWorlds", List.of());
        List<String> ignored_worlds = new ArrayList<>();
        ignored_worlds_objects.forEach(s -> {
            String w = s.toString();
            this.getLogger().info("ignore: " + w);
                ignored_worlds.add(s.toString());
        });
        this.config_ignored_worlds = Set.of(ignored_worlds.toArray(new String[0]));
        outConfig.set("IgnoredWorlds", ignored_worlds_objects);


        try {
            outConfig.save(configFile);
        } catch (IOException e) {
            AddLogEntry("Encountered an issue while writing to the config file." + e.getMessage());
            e.printStackTrace();
        }

        //register for events
        this.getServer().getPluginManager().registerEvents(this, this);

        for (Player player : this.getServer().getOnlinePlayers()) {
            PlayerData.Preload(player);
        }

//        try
//        {
//            new Metrics(this, 3371);
//        }
//        catch (Throwable ignored){}

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new me.ryanhamshire.GraviTree.PlaceholderAPIHook(instance).register();
            AddLogEntry("GraviTree hooked into PlaceholderAPI.");
        }

        AddLogEntry("GraviTree enabled.");
    }

    public void onDisable() {
        @SuppressWarnings("unchecked")
        Collection<Player> players = (Collection<Player>) this.getServer().getOnlinePlayers();
        for (Player player : players) {
            PlayerData data = PlayerData.FromPlayer(player);
            data.saveChanges();
            data.waitForSaveComplete();
        }

        AddLogEntry("GraviTree disabled.");
    }

    //handles slash commands
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        if (sender instanceof Player player
                && "treesfall".equalsIgnoreCase(cmd.getName())) {
            if (!this.config_canDisable) {
                return true;
            }
            if (this.config_ignored_worlds.contains(player.getWorld().getName())) {
                player.sendMessage(ChatColor.AQUA + this.config_disabledForWorld);
                return true;
            }
            PlayerData playerData = PlayerData.FromPlayer(player);
            playerData.setChopEnabled(!playerData.isChopEnabled());
            if (playerData.isChopEnabled()) {
                player.sendMessage(ChatColor.AQUA + this.config_chopOn);
            } else {
                player.sendMessage(ChatColor.AQUA + this.config_chopOff);
            }
            return true;
        }

        return false;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData.Preload(player);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerData.FromPlayer(player).saveChanges();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onBlockBreak(BlockBreakEvent event) {
        if (event.getPlayer() instanceof Player player
                && event.getBlock() instanceof Block brokenBlock
                && brokenBlock.getWorld() instanceof World world) {

            if (this.config_ignored_worlds.contains(world.getName())) {
                return;
            }

            if (this.config_overworld_only && world.getEnvironment() != Environment.NORMAL) {
                return;
            }

            if (!GraviTree.blockIsLog(brokenBlock)) {
                return;
            }

            if (!this.hasPermission(player)) {
                return;
            }

            PlayerData playerData = PlayerData.FromPlayer(player);

            if (!playerData.isGotChopInfo() && (!playerData.isChopEnabled() || this.config_canDisable)) {
                player.sendMessage(ChatColor.AQUA + GraviTree.instance.config_chopInfoMessage);
                playerData.setGotChopInfo(true);
            }

            if (!playerData.isChopEnabled()) {
                return;
            }

            final BlockFace[] adjacentFaces = new BlockFace[]{
                    BlockFace.EAST,
                    BlockFace.WEST,
                    BlockFace.SOUTH,
                    BlockFace.NORTH,
                    BlockFace.NORTH_EAST,
                    BlockFace.NORTH_WEST,
                    BlockFace.SOUTH_EAST,
                    BlockFace.SOUTH_WEST
            };

            // traverse logs down to roots starting at broken block
            Block bestUnderBlock = brokenBlock;
            Material brokenBlockMaterial = brokenBlock.getType();
            do {
                bestUnderBlock = bestUnderBlock.getRelative(BlockFace.DOWN);
                while (bestUnderBlock.getType() == brokenBlockMaterial) {
                    // go down as far as possible
                    bestUnderBlock = bestUnderBlock.getRelative(BlockFace.DOWN);
                }
                // bestUnderBlock is first non log (eg dirt / air / leaves)
                if (blockIsRootType(bestUnderBlock)) {
                    break;
                }
                // not at the root, any logs connecting logs around?
                for (BlockFace nearBelowFace : adjacentFaces) {
                    Block nearBelowBlock = bestUnderBlock.getRelative(nearBelowFace);
                    if (blockIsRootType(nearBelowBlock)) {
                        bestUnderBlock = nearBelowBlock;
                        break;
                    }
                    if (nearBelowBlock.getType() == brokenBlockMaterial) {
                        bestUnderBlock = nearBelowBlock;
                        break;
                    }
                }
            } while (bestUnderBlock.getType() == brokenBlockMaterial);


            if (!blockIsRootType(bestUnderBlock)) {
                return;
            }

            for (BlockFace adjacentFace : adjacentFaces) {
                Block adjacentBlock = brokenBlock.getRelative(adjacentFace);
                if (adjacentBlock.getType() == brokenBlockMaterial) {
                    // 2x2 tree only falls when last log is chopped
                    // not sure what effect this has on chopping at horizontal branches
                    return;
                }
            }
            Block aboveBlock = brokenBlock.getRelative(BlockFace.UP);
            while (aboveBlock.getType() == brokenBlockMaterial) {
                aboveBlock = aboveBlock.getRelative(BlockFace.UP);
            }

            if (!this.blockIsTreeTopper(aboveBlock)) {
                // not a treetop on top of this log
                return;
            }

            int depth = 50;
//        GraviTree.instance.getLogger().info(
//                "depth=" + depth + " blockType=" + brokenBlock.getType() +
//                        " visiting x=" + brokenBlock.getX() + " z=" + brokenBlock.getZ() + " y=" + brokenBlock.getY());
            ConcurrentLinkedQueue<Block> visited = new ConcurrentLinkedQueue<Block>();
            visited.add(brokenBlock);
            FallTask fallTask = new FallTask(brokenBlock, this.config_allow_blocks_dropping,
                    player, depth, brokenBlock.getType(), visited);
            Bukkit.getScheduler().runTaskLater(GraviTree.instance, fallTask, 1L);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    void onEntityDamage(EntityDamageEvent event) {
        if (this.config_logs_damage_players || event.getEntityType() != EntityType.PLAYER) {
            return;
        }

        DamageCause cause = event.getCause();
        if (cause != DamageCause.FALLING_BLOCK && cause != DamageCause.SUFFOCATION) {
            return;
        }

        Block faceBlock = ((Player) (event.getEntity())).getEyeLocation().getBlock();

        Material type = faceBlock.getType();
        if (type == Material.AIR || Tag.LOGS.isTagged(type)) {
            WorldBorder border = faceBlock.getWorld().getWorldBorder();
            if (border != null) {
                if (!outsideWorldBorder(faceBlock.getX(), faceBlock.getY(), faceBlock.getZ(),
                        border.getCenter().getBlockX(), border.getCenter().getBlockY(), border.getCenter().getBlockZ(),
                        border.getSize(), border.getDamageBuffer())) {
                    event.setCancelled(true);
                }
            }
        }
    }

    static boolean outsideWorldBorder(int x, int y, int z, int borderCenterX, int borderCenterY, int borderCenterZ,
                                      double borderSize, double borderDamageBuffer) {
        int damageDistance = (int) (borderSize / 2 + borderDamageBuffer);

        int maxx = borderCenterX + damageDistance;
        if (x > maxx) return true;

        int minx = borderCenterX - damageDistance;
        if (x < minx) return true;

        int maxz = borderCenterZ + damageDistance;
        if (z > maxz) return true;

        int minz = borderCenterZ - damageDistance;
        if (z < minz) return true;

        return false;
    }

    private boolean hasPermission(Player player) {
        if (GraviTree.instance.config_universalPermission) {
            return true;
        }
        return player.hasPermission("gravitree.chop");
    }

    private static void markVisited(Block block) {
        block.setMetadata("gravitree.seen", new FixedMetadataValue(GraviTree.instance, Boolean.TRUE));
    }

    private static boolean haveVisited(Block block) {
        return block.hasMetadata("gravitree.seen");
    }

    private static boolean materialIsLog(Material type) {
        return (Tag.LOGS.isTagged(type) || type == Material.CRIMSON_STEM || type == Material.WARPED_STEM);
    }

    private static boolean blockIsLog(Block block) {
        Material type = block.getType();
        return (Tag.LOGS.isTagged(type) || type == Material.CRIMSON_STEM || type == Material.WARPED_STEM);
    }

    private boolean blockIsRootType(Block block) {
        Material type = block.getType();
        return (type == Material.DIRT || type == Material.GRASS_BLOCK || type == Material.PODZOL
                || type == Material.STONE || type == Material.COBBLESTONE
                || type == Material.SAND || type == Material.MUD || type == Material.MOSS_BLOCK
                || type == Material.NETHERRACK || type == Material.WARPED_NYLIUM
                || type == Material.CRIMSON_NYLIUM || type == Material.MANGROVE_ROOTS
                || ExtraTags.TERRACOTTA.isTagged(type)
        );
    }

    private boolean blockIsTreeTopper(Block block) {
        Material type = block.getType();
        return (type == Material.AIR || type == Material.SNOW || type == Material.NETHER_WART_BLOCK
                || type == Material.WARPED_WART_BLOCK || type == Material.SHROOMLIGHT
                || Tag.LEAVES.isTagged(type)
        );
    }

    private static boolean blockIsPassthrough(Block block) {
        Material type = block.getType();
        return (type == Material.SNOW || type == Material.CRIMSON_STEM
                || type == Material.WARPED_STEM
                || type == Material.WARPED_WART_BLOCK
                || type == Material.NETHER_WART_BLOCK
                || type == Material.SHROOMLIGHT
                || Tag.LEAVES.isTagged(type) || Tag.LOGS.isTagged(type));
    }

    static boolean blockIsBreakable(Block block) {
        if (block.getY() < 0) {
            return false;
        }

        Material type = block.getType();
        return (Tag.LEAVES.isTagged(type)
                || type == Material.AIR || type == Material.VINE || type == Material.COCOA
                || type == Material.TORCH || type == Material.SNOW || type == Material.WEEPING_VINES
                || type == Material.TWISTING_VINES || type == Material.NETHER_WART_BLOCK
                || type == Material.WARPED_WART_BLOCK || type == Material.SHROOMLIGHT
        );
    }

    static boolean blockIsTreeAdjacent(Block block) {
        Material type = block.getType();
        return (Tag.LEAVES.isTagged(type) || type == Material.AIR
                || type == Material.VINE || type == Material.COCOA
                || type == Material.TORCH || type == Material.SNOW || type == Material.GRASS_BLOCK
                || type == Material.DIRT || type == Material.STONE || type == Material.COBBLESTONE
                || type == Material.TALL_GRASS || type == Material.WEEPING_VINES
                || type == Material.TWISTING_VINES || type == Material.NETHERRACK
                || type == Material.CRIMSON_NYLIUM || type == Material.CRIMSON_ROOTS
                || type == Material.WARPED_NYLIUM || type == Material.WARPED_ROOTS
                || type == Material.NETHER_WART_BLOCK || type == Material.WARPED_WART_BLOCK
                || type == Material.SHROOMLIGHT
        );
    }

    static class FallTask implements Runnable {
        private final Block blockToDrop;
        private final boolean allowBlocksDropping;
        private final Player player;
        private final int depth;
        private final Material droppedMaterial;
        private ConcurrentLinkedQueue<Block> lastVisited = null;


        final BlockFace[] adjacentFaces = new BlockFace[]{
                BlockFace.EAST,
                BlockFace.WEST,
                BlockFace.SOUTH,
                BlockFace.NORTH,
                BlockFace.NORTH_EAST,
                BlockFace.NORTH_WEST,
                BlockFace.SOUTH_EAST,
                BlockFace.SOUTH_WEST
        };

        /**
         * we asume to start chopping at the base of the tree.. only going up
         *
         * @param blockToDrop
         * @param allowBlocksDropping
         * @param player
         * @param depth
         * @param droppedMaterial
         * @param lastVisited
         */
        public FallTask(Block blockToDrop, boolean allowBlocksDropping,
                        Player player, int depth, Material droppedMaterial,
                        ConcurrentLinkedQueue<Block> lastVisited) {
            this.blockToDrop = blockToDrop;
            this.allowBlocksDropping = allowBlocksDropping;
            this.player = player;
            this.depth = depth;
            this.droppedMaterial = droppedMaterial;
            this.lastVisited = lastVisited; // no need to traverse back to where we came from (this may need to be list)
        }

        @Override
        public void run() {
//            GraviTree.instance.getLogger().info(
//                    "depth=" + depth + " blockType=" + blockToDrop.getType() + " visiting"
//                    + " x=" + blockToDrop.getX() + " z=" + blockToDrop.getZ() + " y=" + blockToDrop.getY());

            if (blockToDrop.getType() == droppedMaterial) {
                FallingBlock fallingBlock = blockToDrop.getWorld().spawnFallingBlock(
                        blockToDrop.getLocation().add(.5, 0, .5),
                        blockToDrop.getBlockData());
                fallingBlock.setDropItem(this.allowBlocksDropping);

                blockToDrop.setType(Material.AIR);


                Block underBlock = blockToDrop.getRelative(BlockFace.DOWN);
                while (GraviTree.blockIsBreakable(underBlock)) {
                    underBlock.breakNaturally();
                    underBlock = underBlock.getRelative(BlockFace.DOWN);
                }


            }
            if (depth <= 0) {
                return;
            }

            ConcurrentLinkedQueue<Block> nearAboveBlocks = new ConcurrentLinkedQueue<Block>();
            Block aboveBlock = blockToDrop.getRelative(BlockFace.UP);

            for (BlockFace blockFace : adjacentFaces) {
                nearAboveBlocks.add(blockToDrop.getRelative(blockFace));
            }

            ConcurrentLinkedQueue<FallTask> newTasks = new ConcurrentLinkedQueue<FallTask>();
            for (Block block : nearAboveBlocks) {
                if (block.getType() == droppedMaterial && !lastVisited.contains(block)) {
                    lastVisited.add(block);
                    FallTask fallTask = new FallTask(block,
                            allowBlocksDropping, player, depth - 1, droppedMaterial,
                            lastVisited);
                    newTasks.add(fallTask);
                }
            }

            if (aboveBlock.getType() == droppedMaterial && !lastVisited.contains(aboveBlock)) {
                lastVisited.add(aboveBlock);
                FallTask fallTask = new FallTask(aboveBlock,
                        allowBlocksDropping, player, depth - 1, droppedMaterial,
                        lastVisited);
                newTasks.add(fallTask);
            }

            if (newTasks.isEmpty()) {
                nearAboveBlocks = new ConcurrentLinkedQueue<Block>();
                // maybe the tree continues diagonally
                for (BlockFace blockFace : adjacentFaces) {
                    nearAboveBlocks.add(aboveBlock.getRelative(blockFace));
                }
                for (Block block : nearAboveBlocks) {
                    if (block.getType() == droppedMaterial && !lastVisited.contains(block)) {
                        lastVisited.add(block);
                        FallTask fallTask = new FallTask(block,
                                allowBlocksDropping, player, depth - 1, droppedMaterial,
                                lastVisited);
                        newTasks.add(fallTask);
                    }
                }
            }

            long i = 0;
            for (FallTask task : newTasks) {
                Bukkit.getScheduler().runTaskLater(GraviTree.instance, task, ++i);
            }
        }
    }

    static class FallTaskOrig implements Runnable {
        private final Block blockToDrop;
        private final boolean breakUnderBlocks;
        private final boolean allowBlocksDropping;
        private final int max_x;
        private final int max_z;
        private final int min_x;
        private final int min_z;
        private final Player player;


        final BlockFace[] adjacentFaces = new BlockFace[]{
                BlockFace.EAST,
                BlockFace.WEST,
                BlockFace.SOUTH,
                BlockFace.NORTH,
                BlockFace.NORTH_EAST,
                BlockFace.NORTH_WEST,
                BlockFace.SOUTH_EAST,
                BlockFace.SOUTH_WEST
        };

        final BlockFace[] adjacentFacesUpDown = new BlockFace[]{
                BlockFace.EAST,
                BlockFace.WEST,
                BlockFace.SOUTH,
                BlockFace.NORTH,
                BlockFace.NORTH_EAST,
                BlockFace.NORTH_WEST,
                BlockFace.SOUTH_EAST,
                BlockFace.SOUTH_WEST,
                BlockFace.UP,
                BlockFace.DOWN
        };

        /**
         * we asume to start chopping at the base of the tree.. only going up
         *
         * @param blockToDrop
         * @param breakUnderBlocks
         * @param allowBlocksDropping
         * @param min_x
         * @param max_x
         * @param min_z
         * @param max_z
         * @param player
         */
        public FallTaskOrig(Block blockToDrop, boolean breakUnderBlocks, boolean allowBlocksDropping,
                            int min_x, int max_x, int min_z, int max_z, Player player) {
            this.blockToDrop = blockToDrop;
            this.breakUnderBlocks = breakUnderBlocks;
            this.allowBlocksDropping = allowBlocksDropping;
            this.min_x = min_x;
            this.min_z = min_z;
            this.max_x = max_x;
            this.max_z = max_z;
            this.player = player;
        }

        @Override
        public void run() {
            if (GraviTree.blockIsLog(blockToDrop)) {
                FallingBlock fallingBlock = blockToDrop.getWorld().spawnFallingBlock(
                        blockToDrop.getLocation().add(.5, 0, .5),
                        blockToDrop.getBlockData());
                fallingBlock.setDropItem(this.allowBlocksDropping);

                blockToDrop.setType(Material.AIR);

                if (this.breakUnderBlocks) {
                    Block underBlock = blockToDrop.getRelative(BlockFace.DOWN);
                    while (GraviTree.blockIsBreakable(underBlock)) {
                        underBlock.breakNaturally();
                        underBlock = underBlock.getRelative(BlockFace.DOWN);
                    }
                }
            }

            ConcurrentLinkedQueue<Block> nearAboveBlocks = new ConcurrentLinkedQueue<Block>();
            Block aboveBlock = blockToDrop.getRelative(BlockFace.UP);

            nearAboveBlocks.add(aboveBlock);
            for (BlockFace blockFace : adjacentFaces) {
                nearAboveBlocks.add(aboveBlock.getRelative(blockFace));
            }

            boolean foundLogAbove = false;
            ConcurrentLinkedQueue<FallTaskOrig> newTasks = new ConcurrentLinkedQueue<FallTaskOrig>();
            for (Block block : nearAboveBlocks) {
                if (GraviTree.blockIsLog(block)) {
                    Block underBlock = block.getRelative(BlockFace.DOWN);
                    if (GraviTree.blockIsBreakable(underBlock)) {
                        FallTaskOrig fallTask = new FallTaskOrig(block, block != aboveBlock,
                                allowBlocksDropping, min_x, max_x, min_z, max_z, player);
                        newTasks.add(fallTask);
                        foundLogAbove = true;
                    }
                } else if (!GraviTree.blockIsTreeAdjacent(block)) {
                    return;
                }
            }

            long i = 0;
            for (FallTaskOrig task : newTasks) {
                Bukkit.getScheduler().runTaskLater(GraviTree.instance, task, ++i);
            }

            if (!foundLogAbove && !breakUnderBlocks) {
                ConcurrentLinkedQueue<Block> blocksToFall = new ConcurrentLinkedQueue<Block>();

                GraviTree.markVisited(aboveBlock);
                ConcurrentLinkedQueue<Block> blocksToVisit = new ConcurrentLinkedQueue<Block>();
                for (BlockFace blockFace : adjacentFaces) {
                    blocksToVisit.add(blockToDrop.getRelative(blockFace));
                }

                for (Block block : blocksToVisit) {
                    GraviTree.markVisited(block);
                }

                Block nextBlock;
                while ((nextBlock = blocksToVisit.poll()) != null) {
                    if (GraviTree.blockIsLog(nextBlock)) {
                        Block underBlock = nextBlock.getRelative(BlockFace.DOWN);
                        if (blockIsBreakable(underBlock) && !blocksToFall.contains(nextBlock)) {
                            blocksToFall.add(nextBlock);
                        }
                    }

                    if (GraviTree.blockIsPassthrough(nextBlock)) {
                        Block[] nextBlocks = new Block[]
                                {
                                        nextBlock,
                                        nextBlock.getRelative(BlockFace.UP),
                                        nextBlock.getRelative(BlockFace.DOWN)
                                };

                        for (Block nextNextBlock : nextBlocks) {
                            for (BlockFace adjacentFace : adjacentFacesUpDown) {
                                trackTraversal(nextNextBlock, adjacentFace, blocksToVisit);
                            }
                        }
                    }
                }

                Block blockToDrop;
                long delayInTicks = 1;
                while ((blockToDrop = blocksToFall.poll()) != null) {
                    FallTaskOrig fallTask = new FallTaskOrig(blockToDrop, true, allowBlocksDropping,
                            min_x, max_x, min_z, max_z, player);
                    Bukkit.getScheduler().runTaskLater(GraviTree.instance, fallTask, delayInTicks++);
                }
            }
        }

        private void trackTraversal(Block nextNextBlock, BlockFace face, ConcurrentLinkedQueue<Block> blocksToVisit) {
            Block adjacentBlock = nextNextBlock.getRelative(face);
            if (adjacentBlock.getX() >= min_x
                    && adjacentBlock.getX() <= max_x
                    && adjacentBlock.getZ() >= min_z
                    && adjacentBlock.getZ() <= max_z
                    && !GraviTree.haveVisited(adjacentBlock)) {
                blocksToVisit.add(adjacentBlock);
                GraviTree.markVisited(adjacentBlock);
            }
        }
    }
}