package com.piglinmine.blueftbchunks.ftbchunks.region;

import com.flowpowered.math.vector.Vector2i;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.UsernameCache;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents a connected region of claimed chunks belonging to a single team.
 *
 * <p>A ClaimRegion contains metadata about the team and provides methods for
 * generating BlueMap marker content, including labels, detailed HTML views,
 * and geometric coordinates for rendering.</p>
 *
 * <p>Each region is assigned a unique identifier based on the team ID, dimension,
 * and region number, ensuring stable marker IDs across updates.</p>
 */
public class ClaimRegion {

    private static final int BLOCKS_PER_CHUNK = 16;
    private static final String AVATAR_URL_TEMPLATE = "https://mc-heads.net/head/%s/%d";

    private final Team team;
    private final String dimensionId;
    private final String teamName;
    private final int teamColor;
    private final int regionNumber;
    private final Set<ChunkDimPos> chunks;

    /**
     * Creates a new claim region.
     *
     * @param team         the owning team
     * @param dimensionId  the dimension ID (e.g., "minecraft:overworld")
     * @param teamName     the display name of the team
     * @param teamColor    the team's color as RGB
     * @param regionNumber the unique region number for this team
     */
    public ClaimRegion(Team team, String dimensionId, String teamName, int teamColor, int regionNumber) {
        this.team = team;
        this.dimensionId = dimensionId;
        this.teamName = teamName;
        this.teamColor = teamColor;
        this.regionNumber = regionNumber;
        this.chunks = new HashSet<>();
    }

    /**
     * Adds a chunk to this region.
     *
     * @param pos the chunk position to add
     */
    public void addChunk(ChunkDimPos pos) {
        chunks.add(pos);
    }

    /**
     * Returns the chunk coordinates as 2D vectors for shape rendering.
     *
     * @return an array of chunk coordinates
     */
    public Vector2i[] getChunkCoordinates() {
        return chunks.stream()
                .map(pos -> new Vector2i(pos.x(), pos.z()))
                .toArray(Vector2i[]::new);
    }

    /**
     * Returns all chunks in this region.
     *
     * @return an array of chunk positions
     */
    public ChunkDimPos[] getChunks() {
        return chunks.toArray(new ChunkDimPos[0]);
    }

    /**
     * Returns the owning team's UUID.
     *
     * @return the team UUID
     */
    public UUID getTeamId() {
        return team.getTeamId();
    }

    /**
     * Returns the dimension ID.
     *
     * @return the dimension ID string
     */
    public String getDimensionId() {
        return dimensionId;
    }

    /**
     * Returns the team's display color.
     *
     * @return the color as RGB integer
     */
    public int getTeamColor() {
        return teamColor;
    }

    /**
     * Returns the unique marker ID for this region.
     *
     * @return the marker ID string
     */
    public String getMarkerId() {
        return String.format("region_%s_%s_%d", getTeamId(), dimensionId, regionNumber);
    }

    /**
     * Returns a short label for the marker.
     *
     * @return the label string
     */
    public String getLabel() {
        int[] center = computeCenterBlockPosition();
        String formattedDimension = formatDimensionName(dimensionId);
        return String.format("%s • Region %d • %s (%d, %d)",
                teamName, regionNumber, formattedDimension, center[0], center[1]);
    }

    /**
     * Generates the HTML detail view for this region's marker popup.
     *
     * @return the HTML content as a string
     */
    public String getDetail() {
        StringBuilder html = new StringBuilder();

        html.append("<div style=\"").append(HtmlStyles.CONTAINER).append("\">");
        html.append(buildHeaderSection());
        html.append(buildMetadataSection());
        html.append(buildDivider());
        html.append(buildMembersSection());
        html.append("</div>");

        return html.toString();
    }

    // ==================== HTML Generation ====================

    private String buildHeaderSection() {
        String colorHex = toHexColor(teamColor);

        return String.format(
                "<div style=\"%s\">" +
                "<div style=\"width:16px;height:16px;border-radius:3px;background:%s;" +
                "box-shadow:0 2px 4px rgba(0,0,0,0.2);\"></div>" +
                "<h3 style=\"margin:0;font-size:1.2em;font-weight:600;color:%s;\">%s</h3>" +
                "</div>",
                HtmlStyles.HEADER,
                colorHex,
                HtmlStyles.TEXT_PRIMARY,
                escapeHtml(teamName)
        );
    }

    private String buildMetadataSection() {
        int[] center = computeCenterBlockPosition();
        String formattedDimension = formatDimensionName(dimensionId);

        StringBuilder html = new StringBuilder();
        html.append("<div style=\"").append(HtmlStyles.METADATA_GRID).append("\">");

        appendMetadataRow(html, "Dimension", formattedDimension);
        appendMetadataRow(html, "Chunks", String.valueOf(chunks.size()));
        appendMetadataRow(html, "Center", String.format("(%,d, %,d)", center[0], center[1]));
        appendMetadataRow(html, "Area", formatArea());

        html.append("</div>");
        return html.toString();
    }

    private void appendMetadataRow(StringBuilder html, String label, String value) {
        html.append(String.format(
                "<div style=\"color:%s;font-weight:500;\">%s:</div><div>%s</div>",
                HtmlStyles.TEXT_MUTED, label, value
        ));
    }

    private String buildMembersSection() {
        Map<UUID, String> onlineMembers = getOnlineMembers();
        List<MemberInfo> members = buildMemberList(onlineMembers);

        if (members.isEmpty()) {
            return buildEmptyMembersView();
        }

        StringBuilder html = new StringBuilder();
        html.append(buildMembersHeader(members.size(), onlineMembers.size()));
        html.append(buildMembersList(members));
        return html.toString();
    }

    private String buildMembersHeader(int totalCount, int onlineCount) {
        return String.format(
                "<div style=\"margin-bottom:12px;padding-bottom:8px;border-bottom:1px solid %s;\">" +
                "<span style=\"font-weight:600;font-size:1em;color:%s;\">Members</span> " +
                "<span style=\"color:%s;font-size:0.85em;margin-left:8px;\">" +
                "<span style=\"color:%s;\">● %d/%d online</span> " +
                "</span></div>",
                HtmlStyles.DIVIDER_COLOR,
                HtmlStyles.TEXT_PRIMARY,
                HtmlStyles.TEXT_MUTED,
                HtmlStyles.STATUS_ONLINE, onlineCount, totalCount
        );
    }

    private String buildMembersList(List<MemberInfo> members) {
        StringBuilder html = new StringBuilder();
        html.append("<div style=\"").append(HtmlStyles.MEMBERS_LIST).append("\">");

        for (MemberInfo member : members) {
            html.append(member.toHtml());
        }

        html.append("</div>");
        return html.toString();
    }

    private String buildEmptyMembersView() {
        return String.format(
                "<div style=\"text-align:center;padding:20px;color:%s;font-style:italic;\">" +
                "No members in this team</div>",
                HtmlStyles.TEXT_MUTED
        );
    }

    private String buildDivider() {
        return String.format(
                "<hr style=\"border:none;border-top:1px solid %s;margin:16px 0;\">",
                HtmlStyles.DIVIDER_COLOR
        );
    }

    // ==================== Data Retrieval ====================

    private Map<UUID, String> getOnlineMembers() {
        return team.getOnlineMembers().stream()
                .collect(Collectors.toMap(
                        ServerPlayer::getUUID,
                        player -> player.getGameProfile().getName()
                ));
    }

    private List<MemberInfo> buildMemberList(Map<UUID, String> onlineMembers) {
        return team.getMembers().stream()
                .map(memberId -> createMemberInfo(memberId, onlineMembers))
                .sorted()
                .collect(Collectors.toList());
    }

    private MemberInfo createMemberInfo(UUID memberId, Map<UUID, String> onlineMembers) {
        boolean isOnline = onlineMembers.containsKey(memberId);
        String name = isOnline
                ? onlineMembers.get(memberId)
                : UsernameCache.getLastKnownUsername(memberId);

        if (name == null || name.isBlank()) {
            name = "Unknown Player";
        }

        return new MemberInfo(name, isOnline, memberId);
    }

    // ==================== Utility Methods ====================

    private int[] computeCenterBlockPosition() {
        if (chunks.isEmpty()) {
            return new int[]{0, 0};
        }

        double sumX = 0;
        double sumZ = 0;

        for (ChunkDimPos pos : chunks) {
            sumX += pos.x() * BLOCKS_PER_CHUNK;
            sumZ += pos.z() * BLOCKS_PER_CHUNK;
        }

        int count = chunks.size();
        return new int[]{
                (int) Math.round(sumX / count),
                (int) Math.round(sumZ / count)
        };
    }

    private String formatArea() {
        int totalBlocks = chunks.size() * BLOCKS_PER_CHUNK * BLOCKS_PER_CHUNK;

        if (totalBlocks >= 1_000_000) {
            return String.format("%,.1fM blocks²", totalBlocks / 1_000_000.0);
        } else if (totalBlocks >= 1_000) {
            return String.format("%,.1fK blocks²", totalBlocks / 1_000.0);
        }
        return String.format("%,d blocks²", totalBlocks);
    }

    private static String formatDimensionName(String dimension) {
        String cleaned = dimension.replace("minecraft:", "");
        return Arrays.stream(cleaned.split("[_:]"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    private static String toHexColor(int color) {
        return String.format("#%06X", (0xFFFFFF & color));
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    // ==================== Inner Classes ====================

    /**
     * CSS style constants for HTML generation.
     */
    private static final class HtmlStyles {
        static final String TEXT_PRIMARY = "#FFFFFF";
        static final String TEXT_SECONDARY = "#B9BBBE";
        static final String TEXT_MUTED = "#72767D";
        static final String DIVIDER_COLOR = "#40444B";
        static final String STATUS_ONLINE = "#43B581";
        static final String STATUS_OFFLINE = "#747F8D";
        static final String BACKGROUND_COLOR = "#2C2F33";

        static final String CONTAINER = "font-family:system-ui,-apple-system,sans-serif;" +
                "line-height:1.5;color:" + TEXT_PRIMARY;
        static final String HEADER = "display:flex;align-items:center;gap:10px;margin-bottom:12px";
        static final String METADATA_GRID = "display:grid;grid-template-columns:auto 1fr;" +
                "gap:8px 12px;font-size:0.9em;color:" + TEXT_SECONDARY;
        static final String MEMBERS_LIST = "display:flex;flex-direction:column;gap:8px;" +
                "max-height:300px;overflow-y:auto;padding-right:4px";

        private HtmlStyles() {}
    }

    /**
     * Represents a team member with display information.
     */
    private record MemberInfo(String name, boolean isOnline, UUID id) implements Comparable<MemberInfo> {

        private static final int AVATAR_SIZE = 32;
        private static final int STATUS_BADGE_SIZE = 12;

        @Override
        public int compareTo(@NotNull MemberInfo other) {
            if (this.isOnline != other.isOnline) {
                return this.isOnline ? -1 : 1;
            }
            return this.name.compareToIgnoreCase(other.name);
        }

        String toHtml() {
            String safeName = escapeHtml(name);
            String statusColor = isOnline ? HtmlStyles.STATUS_ONLINE : HtmlStyles.STATUS_OFFLINE;
            String textColor = isOnline ? HtmlStyles.TEXT_PRIMARY : HtmlStyles.TEXT_SECONDARY;
            String avatarUrl = String.format(AVATAR_URL_TEMPLATE, id, AVATAR_SIZE);

            return String.format(
                    "<div style=\"display:flex;align-items:center;gap:10px;padding:6px 8px;" +
                    "border-radius:6px;background:rgba(255,255,255,0.02);\">" +
                    "<div style=\"position:relative;width:%dpx;height:%dpx;flex-shrink:0;\">" +
                    "<img src=\"%s\" style=\"width:100%%;height:100%%;border-radius:6px;display:block;" +
                    "box-shadow:0 2px 4px rgba(0,0,0,0.3);\" loading=\"lazy\" alt=\"%s\">" +
                    "<div style=\"position:absolute;bottom:-4px;right:-4px;width:%dpx;height:%dpx;" +
                    "background:%s;border:3px solid %s;border-radius:50%%;\" title=\"%s\"></div>" +
                    "</div>" +
                    "<span style=\"color:%s;font-weight:500;font-size:0.95em;\">%s</span>" +
                    "</div>",
                    AVATAR_SIZE, AVATAR_SIZE,
                    avatarUrl,
                    safeName,
                    STATUS_BADGE_SIZE, STATUS_BADGE_SIZE,
                    statusColor,
                    HtmlStyles.BACKGROUND_COLOR,
                    isOnline ? "Online" : "Offline",
                    textColor, safeName
            );
        }
    }
}

