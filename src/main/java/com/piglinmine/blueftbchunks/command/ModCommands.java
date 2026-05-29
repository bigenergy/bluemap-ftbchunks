package com.piglinmine.blueftbchunks.command;

import com.piglinmine.blueftbchunks.BlueChunks;
import com.piglinmine.blueftbchunks.data.ChunkDataManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Registers and handles administrative commands for the mod.
 *
 * <p>Available commands:</p>
 * <ul>
 *   <li>{@code /blueftbchunks reload} - Reload data and synchronize with FTB Chunks</li>
 *   <li>{@code /blueftbchunks data save} - Immediately save data to file</li>
 *   <li>{@code /blueftbchunks data init} - Force complete re-initialization</li>
 *   <li>{@code /blueftbchunks data status} - Show current data status</li>
 * </ul>
 */
public final class ModCommands {

    private static final String COMMAND_ROOT = BlueChunks.MOD_ID;

    private ModCommands() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    /**
     * Registers all mod commands with the dispatcher.
     *
     * @param dispatcher the command dispatcher
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal(COMMAND_ROOT)
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("reload")
                                .executes(ModCommands::executeReload))
                        .then(Commands.literal("data")
                                .then(Commands.literal("save")
                                        .executes(ModCommands::executeSave))
                                .then(Commands.literal("init")
                                        .executes(ModCommands::executeInit))
                                .then(Commands.literal("status")
                                        .executes(ModCommands::executeStatus)))
        );
    }

    /**
     * Handles the /ftbchunksbluemap reload command.
     * Performs a full memory flush and reloads all data from scratch.
     */
    private static int executeReload(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ChunkDataManager manager = ChunkDataManager.getInstance();

        if (!manager.isInitialized()) {
            source.sendFailure(Component.literal("Data manager is not initialized!")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Performing full memory flush and reload...")
                .withStyle(ChatFormatting.YELLOW), true);

        try {
            manager.fullFlushAndReload(source.getServer());

            source.sendSuccess(() -> Component.literal("Reload complete! ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(String.format("(%d chunks, %d teams)",
                                    manager.getClaimData().getTotalChunkCount(),
                                    manager.getClaimData().getTotalTeamCount()))
                            .withStyle(ChatFormatting.GRAY)), true);

            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Reload failed: " + e.getMessage())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    /**
     * Handles the /ftbchunksbluemap data save command.
     * Immediately saves data to file.
     */
    private static int executeSave(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ChunkDataManager manager = ChunkDataManager.getInstance();

        if (!manager.isInitialized()) {
            source.sendFailure(Component.literal("Data manager is not initialized!")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Saving claim data...")
                .withStyle(ChatFormatting.YELLOW), true);

        try {
            manager.synchronizeWithFTBChunks();
            boolean success = manager.saveToFile();

            if (success) {
                source.sendSuccess(() -> Component.literal("Save complete! ")
                        .withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(String.format("(%d chunks, %d teams)",
                                        manager.getClaimData().getTotalChunkCount(),
                                        manager.getClaimData().getTotalTeamCount()))
                                .withStyle(ChatFormatting.GRAY)), true);
                return 1;
            } else {
                source.sendFailure(Component.literal("Save failed! Check server logs.")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }
        } catch (Exception e) {
            source.sendFailure(Component.literal("Save failed: " + e.getMessage())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    /**
     * Handles the /ftbchunksbluemap data init command.
     * Forces complete re-initialization.
     */
    private static int executeInit(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ChunkDataManager manager = ChunkDataManager.getInstance();

        if (!manager.isInitialized()) {
            source.sendFailure(Component.literal("Data manager is not initialized!")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Forcing complete re-initialization...")
                .withStyle(ChatFormatting.YELLOW), true);

        try {
            manager.forceReinitialize();

            source.sendSuccess(() -> Component.literal("Re-initialization complete! ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(String.format("(%d chunks, %d teams)",
                                    manager.getClaimData().getTotalChunkCount(),
                                    manager.getClaimData().getTotalTeamCount()))
                            .withStyle(ChatFormatting.GRAY)), true);

            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Re-initialization failed: " + e.getMessage())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    /**
     * Handles the /ftbchunksbluemap data status command.
     * Shows current data status.
     */
    private static int executeStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ChunkDataManager manager = ChunkDataManager.getInstance();

        source.sendSuccess(() -> Component.literal("=== FTBChunksBlueMap Data Status ===")
                .withStyle(ChatFormatting.GOLD), false);

        if (!manager.isInitialized()) {
            source.sendSuccess(() -> Component.literal("Status: ")
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.literal("NOT INITIALIZED")
                            .withStyle(ChatFormatting.RED)), false);
            return 1;
        }

        var data = manager.getClaimData();

        source.sendSuccess(() -> Component.literal("Status: ")
                .withStyle(ChatFormatting.WHITE)
                .append(Component.literal("Initialized")
                        .withStyle(ChatFormatting.GREEN)), false);

        source.sendSuccess(() -> Component.literal("Total Chunks: ")
                .withStyle(ChatFormatting.WHITE)
                .append(Component.literal(String.valueOf(data.getTotalChunkCount()))
                        .withStyle(ChatFormatting.AQUA)), false);

        source.sendSuccess(() -> Component.literal("Total Teams: ")
                .withStyle(ChatFormatting.WHITE)
                .append(Component.literal(String.valueOf(data.getTotalTeamCount()))
                        .withStyle(ChatFormatting.AQUA)), false);

        source.sendSuccess(() -> Component.literal("Dimensions: ")
                .withStyle(ChatFormatting.WHITE)
                .append(Component.literal(String.valueOf(data.getDimensions().size()))
                        .withStyle(ChatFormatting.AQUA)), false);

        if (data.getLastSaved() > 0) {
            long secondsAgo = (System.currentTimeMillis() - data.getLastSaved()) / 1000;
            String timeAgo = formatTimeAgo(secondsAgo);
            source.sendSuccess(() -> Component.literal("Last Saved: ")
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(timeAgo)
                            .withStyle(ChatFormatting.GRAY)), false);
        } else {
            source.sendSuccess(() -> Component.literal("Last Saved: ")
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.literal("Never")
                            .withStyle(ChatFormatting.GRAY)), false);
        }

        return 1;
    }

    /**
     * Formats seconds into a human-readable "time ago" string.
     */
    private static String formatTimeAgo(long seconds) {
        if (seconds < 60) {
            return seconds + " seconds ago";
        } else if (seconds < 3600) {
            return (seconds / 60) + " minutes ago";
        } else if (seconds < 86400) {
            return (seconds / 3600) + " hours ago";
        } else {
            return (seconds / 86400) + " days ago";
        }
    }
}

