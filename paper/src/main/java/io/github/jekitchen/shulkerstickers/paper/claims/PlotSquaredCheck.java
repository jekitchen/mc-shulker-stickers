package io.github.jekitchen.shulkerstickers.paper.claims;

import com.plotsquared.core.PlotSquared;
import com.plotsquared.core.configuration.Settings;
import com.plotsquared.core.location.Location;
import com.plotsquared.core.plot.Plot;
import com.plotsquared.core.plot.PlotArea;
import com.plotsquared.core.plot.flag.implementations.DoneFlag;
import java.util.function.Predicate;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * PlotSquared: the rules PlotSquared applies when a player places a block, minus its build-height
 * check (the box is already there). Outside plot areas, editing is allowed.
 */
final class PlotSquaredCheck implements ClaimCheck {

    @Override
    public String plugin() {
        return "PlotSquared";
    }

    @Override
    public boolean canBuild(Player player, Block block) {
        return spot(player, block).allows(player::hasPermission);
    }

    private static Spot spot(Player player, Block block) {
        Location at = Location.at(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        PlotArea area = PlotSquared.get().getPlotAreaManager().getPlotArea(at);
        if (area == null) {
            return Spot.NOT_PLOTS;
        }
        Plot plot = area.getPlot(at);
        if (plot == null) {
            return Spot.ROAD;
        }
        if (!plot.hasOwner()) {
            return Spot.UNOWNED_PLOT;
        }
        if (!plot.isAdded(player.getUniqueId())) {
            return Spot.SOMEONE_ELSES_PLOT;
        }
        if (Settings.Done.RESTRICT_BUILDING && DoneFlag.isDone(plot)) {
            return Spot.FINISHED_PLOT;
        }
        return Spot.OWN_PLOT;
    }

    /**
     * Where the box is, and the PlotSquared permission that lets a player build there anyway
     * ({@code null}: anyone may, or no one needs to).
     */
    enum Spot {
        NOT_PLOTS(null),
        ROAD("plots.admin.build.road"),
        UNOWNED_PLOT("plots.admin.build.unowned"),
        /** Not owner, trusted, or a member while the owner is online. */
        SOMEONE_ELSES_PLOT("plots.admin.build.other"),
        /** Marked done, with {@code done.restrict-building} on. */
        FINISHED_PLOT("plots.admin.build.other"),
        OWN_PLOT(null);

        private final String bypass;

        Spot(String bypass) {
            this.bypass = bypass;
        }

        boolean allows(Predicate<String> hasPermission) {
            return bypass == null || hasPermission.test(bypass);
        }
    }

    @Override
    public void probe(Block block) {
        Location at = Location.at(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        PlotArea area = PlotSquared.get().getPlotAreaManager().getPlotArea(at);
        if (area != null) {
            area.getPlot(at);
        }
    }
}
