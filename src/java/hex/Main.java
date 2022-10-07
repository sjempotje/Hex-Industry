package hex;

import arc.Events;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Timer;
import hex.components.Icons;
import hex.components.MenuListener;
import hex.content.*;
import hex.types.Hex;
import hex.types.Human;
import hex.types.ai.*;
import hex.Generator.MapSize;
import mindustry.game.EventType.PlayerJoin;
import mindustry.game.EventType.PlayerLeave;
import mindustry.game.Rules.TeamRule;
import mindustry.content.Blocks;
import mindustry.content.UnitTypes;
import mindustry.game.Rules;
import mindustry.game.Team;
import mindustry.gen.Player;
import mindustry.mod.Plugin;
import mindustry.net.Administration;

import static hex.components.Bundle.findLocale;
import static hex.components.Bundle.get;
import static mindustry.Vars.*;

public class Main extends Plugin {

    public static final Seq<Hex> hexes = new Seq<>();
    public static final Seq<Human> humans = new Seq<>();
    public static final Rules rules = new Rules();

    @Override
    public void init() {
        HexSchematics.load();
        Fractions.load();
        HexBuilds.load();
        Packages.load();
        Weapons.load();
        Buttons.load();
        MenuListener.load();
        Icons.load();

        netServer.admins.actionFilters.clear().add(action -> false);
        netServer.assigner = (player, players) -> Team.derelict;

        Blocks.unloader.solid = false; // why?
        Blocks.groundFactory.solid = false;

        UnitTypes.crawler.aiController = HexSuicideAI::new;
        UnitTypes.mono.aiController = HexMinerAI::new;
        UnitTypes.poly.aiController = HexBuilderAI::new;
        UnitTypes.quad.aiController = HexAtomicAI::new;
        UnitTypes.flare.aiController = HexFlyingAI::new;
        UnitTypes.horizon.aiController = HexFlyingAI::new;
        UnitTypes.zenith.aiController = HexFlyingAI::new;
        UnitTypes.alpha.aiController = HexEmptyAI::new;
        UnitTypes.beta.aiController = HexEmptyAI::new;
        UnitTypes.gamma.aiController = HexEmptyAI::new;

        // apply custom ai
        content.units().each(type -> type.playerControllable = false);

        rules.enemyCoreBuildRadius = 0f;
        rules.unitCap = 16;
        rules.infiniteResources = true;
        rules.damageExplosions = false;
        rules.fire = false;
        rules.waves = false;
        rules.canGameOver = false;
        rules.revealedBlocks.add(Blocks.duct);
        rules.modeName = "Hex Industry";

        for (Team team : Team.all) {
            TeamRule rule = rules.teams.get(team);
            rule.cheat = true; // just for visual
            rule.unitDamageMultiplier = rule.blockDamageMultiplier = 0f;
        }

        Timer.schedule(() -> {
            humans.each(Human::update);
            Buttons.update();
        }, 0f, 1f);
        Timer.schedule(Generator::update, 0f, 0.02f);

        Events.on(PlayerJoin.class, event -> Politics.join(event.player));
        Events.on(PlayerLeave.class, event -> Politics.leave(event.player));
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        handler.<Player>register("guide", "Manual with info about mechanics.", (args, player) -> Guide.show(player));

        handler.<Player>register("join", "[player]", "Offer the player to team up.", (args, player) -> Politics.join(args, player));

        handler.<Player>register("spectate", "Watching the game is fun too!", (args, player) -> Politics.spectate(player));

        handler.<Player>register("author", "Plugin creators.", (args, player) -> player.sendMessage(get("author", findLocale(player))));
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("host", "<size>", "Initialize new game.", args -> {
            MapSize size = MapSize.get(args[0]);
            if (size == null) Log.err("Can't find a MapSize with provided name.");
            else {
                Generator.play(size);
                state.rules = rules;

                logic.play();
                netServer.openServer();
            }
        });

        handler.register("hack", "<human>", "Cheating is bad, but sometimes.", args -> {
            Human human = Human.from(args[0]);
            if (human == null) Log.err("Can't find a Human with provided name.");
            else human.production.all(1000);
        });

        handler.register("openall", "Open all hexes.", args -> hexes.each(Hex::isClosed, Hex::open));

        handler.register("strictoff", "DO NOT USE THIS ONLY FOR TESTING!", args -> {
            netServer.admins.actionFilters.clear();
            Administration.Config.strict.set(false);
        });
    }
}
