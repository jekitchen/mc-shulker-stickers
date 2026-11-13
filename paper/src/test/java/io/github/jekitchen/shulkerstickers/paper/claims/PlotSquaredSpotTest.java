package io.github.jekitchen.shulkerstickers.paper.claims;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jekitchen.shulkerstickers.paper.claims.PlotSquaredCheck.Spot;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * PlotSquared's own build rules, as {@link PlotSquaredCheck} applies them. PlotSquared can't run
 * here (its plugin jar is only on SpigotMC), so this covers the decision once the plot is known.
 */
class PlotSquaredSpotTest {

    private static boolean allows(Spot spot, String... permissions) {
        return spot.allows(Set.of(permissions)::contains);
    }

    @Test
    void ownPlotsAndPlacesWithoutPlotsAreOpen() {
        assertTrue(allows(Spot.OWN_PLOT));
        assertTrue(allows(Spot.NOT_PLOTS));
    }

    @Test
    void everywhereElseNeedsPlotSquaredsAdminPermission() {
        assertFalse(allows(Spot.ROAD));
        assertFalse(allows(Spot.UNOWNED_PLOT));
        assertFalse(allows(Spot.SOMEONE_ELSES_PLOT));
        assertFalse(allows(Spot.FINISHED_PLOT));

        assertTrue(allows(Spot.ROAD, "plots.admin.build.road"));
        assertTrue(allows(Spot.UNOWNED_PLOT, "plots.admin.build.unowned"));
        assertTrue(allows(Spot.SOMEONE_ELSES_PLOT, "plots.admin.build.other"));
        assertTrue(allows(Spot.FINISHED_PLOT, "plots.admin.build.other"));
    }

    @Test
    void oneBypassDoesNotOpenAnotherSpot() {
        assertFalse(allows(Spot.SOMEONE_ELSES_PLOT, "plots.admin.build.road", "plots.admin.build.unowned"));
        assertFalse(allows(Spot.ROAD, "plots.admin.build.other"));
        assertEquals(6, Spot.values().length, "a new spot needs a test here");
    }
}
