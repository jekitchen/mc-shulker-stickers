package io.github.jekitchen.shulkerstickers.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.jekitchen.shulkerstickers.core.Changes;
import io.github.jekitchen.shulkerstickers.core.config.ConfigLoader;
import io.github.jekitchen.shulkerstickers.core.config.Cost;
import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class CostsTest {

    private static final StickerConfig PRICED = ConfigLoader.parse("""
            {"color": {"cost": {"type": "xp_levels", "amount": 1}},
             "icon": {"cost": {"type": "item", "item": "minecraft:paper", "amount": 2}},
             "name": {"cost": {"type": "xp_levels", "amount": 3}}}
            """).config();

    @Test
    void chargesOnlyForWhatChanged() {
        assertEquals(List.of(new Cost(Cost.Type.ITEM, 2, "minecraft:paper")),
                Costs.forChanges(new Changes(false, true, false), PRICED));
    }

    @Test
    void chargesEachChangedPartInOrder() {
        assertEquals(List.of(
                        new Cost(Cost.Type.XP_LEVELS, 1, null),
                        new Cost(Cost.Type.ITEM, 2, "minecraft:paper"),
                        new Cost(Cost.Type.XP_LEVELS, 3, null)),
                Costs.forChanges(new Changes(true, true, true), PRICED));
    }

    @Test
    void freeCostsAreLeftOut() {
        assertEquals(List.of(), Costs.forChanges(new Changes(true, true, true), StickerConfig.DEFAULTS));
    }

    @Test
    void xpLevelsAreSummed() {
        assertEquals(4, Costs.totalXpLevels(Costs.forChanges(new Changes(true, false, true), PRICED)));
    }
}
