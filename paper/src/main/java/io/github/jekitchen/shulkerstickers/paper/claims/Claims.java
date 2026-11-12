package io.github.jekitchen.shulkerstickers.paper.claims;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Logger;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/**
 * Claim checks for opening the editor on Paper (PRD §7.3, AC-10, owner decision 3). Editing a box
 * needs build permission, not just container access:
 *
 * <ol>
 *   <li>Every claim plugin we support that is installed is asked directly: GriefPrevention,
 *       WorldGuard, Lands, Towny, BentoBox and PlotSquared. An integration that throws refuses
 *       (fail closed) and logs once.
 *   <li>With {@code protection.simulate_place_event} on, a {@link ClaimTestPlaceEvent} is fired
 *       for every other plugin. Off by default: logging plugins such as CoreProtect would record
 *       a placement that a rollback would undo by deleting the box.
 *   <li>Claim plugins that cancel the right-click are already honored before this runs.
 * </ol>
 */
public final class Claims {

    private final List<ClaimCheck> checks;
    private final PluginManager events;
    private final Logger logger;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    public Claims(List<ClaimCheck> checks, PluginManager events, Logger logger) {
        this.checks = List.copyOf(checks);
        this.events = events;
        this.logger = logger;
    }

    /** No integrations; the test event still works if the config turns it on. */
    public static Claims none(PluginManager events, Logger logger) {
        return new Claims(List.of(), events, logger);
    }

    /** The integrations for the claim plugins that are installed and enabled. */
    public static Claims detect(Plugin plugin) {
        PluginManager pm = plugin.getServer().getPluginManager();
        List<ClaimCheck> found = new ArrayList<>();
        add(found, pm, plugin.getLogger(), "GriefPrevention", p -> new GriefPreventionCheck());
        add(found, pm, plugin.getLogger(), "WorldGuard", p -> new WorldGuardCheck());
        add(found, pm, plugin.getLogger(), "Lands", p -> new LandsCheck(plugin));
        add(found, pm, plugin.getLogger(), "Towny", p -> new TownyCheck());
        add(found, pm, plugin.getLogger(), "BentoBox", p -> new BentoBoxCheck());
        add(found, pm, plugin.getLogger(), "PlotSquared", p -> new PlotSquaredCheck());
        if (!found.isEmpty()) {
            plugin.getLogger().info("Claim checks: " + found.stream().map(ClaimCheck::plugin).toList());
        }
        return new Claims(found, pm, plugin.getLogger());
    }

    private static void add(List<ClaimCheck> found, PluginManager pm, Logger logger, String name,
            Function<String, ClaimCheck> make) {
        if (!pm.isPluginEnabled(name)) {
            return;
        }
        try {
            found.add(make.apply(name));
        } catch (RuntimeException | LinkageError e) {
            logger.warning("Couldn't hook into " + name + " (" + e + "); editing in its claims is refused");
            found.add(new Refuse(name));
        }
    }

    public List<String> plugins() {
        return checks.stream().map(ClaimCheck::plugin).toList();
    }

    /** Self-test: the plugins whose hook failed to call its API at {@code block}. */
    public List<String> probeFailures(Block block) {
        List<String> failed = new ArrayList<>();
        for (ClaimCheck check : checks) {
            try {
                check.probe(block);
                if (check instanceof Refuse) {
                    failed.add(check.plugin() + " (hook didn't load)");
                }
            } catch (RuntimeException | LinkageError e) {
                failed.add(check.plugin() + " (" + e + ")");
            }
        }
        return failed;
    }

    /** Region thread of {@code box}. True if every check lets the player build at the box. */
    public boolean mayEdit(Player player, Block box, boolean simulatePlaceEvent) {
        for (ClaimCheck check : checks) {
            boolean allowed;
            try {
                allowed = check.canBuild(player, box);
            } catch (RuntimeException | LinkageError e) {
                if (warned.add(check.plugin())) {
                    logger.warning("The " + check.plugin() + " claim check failed (" + e + "); editing is refused");
                }
                allowed = false;
            }
            if (!allowed) {
                return false;
            }
        }
        if (simulatePlaceEvent) {
            ClaimTestPlaceEvent test = new ClaimTestPlaceEvent(box, player);
            events.callEvent(test);
            return !test.isCancelled() && test.canBuild();
        }
        return true;
    }

    /** Stands in for an integration that couldn't load: refuses, so a broken hook never opens claims. */
    private record Refuse(String plugin) implements ClaimCheck {
        @Override
        public boolean canBuild(Player player, Block block) {
            return false;
        }
    }
}
