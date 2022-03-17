package hex;

import arc.Events;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Timer;
import hex.components.Icons;
import hex.components.MenuListener;
import hex.content.*;
import hex.types.Hex;
import hex.types.Human;
import hex.types.ai.HexBuilderAI;
import hex.types.ai.HexMinerAI;
import hex.types.ai.HexSuicideAI;
import mindustry.game.EventType.PlayerJoin;
import mindustry.game.EventType.PlayerLeave;
import mindustry.content.Blocks;
import mindustry.content.UnitTypes;
import mindustry.game.Rules;
import mindustry.game.Team;
import mindustry.gen.Player;
import mindustry.mod.Plugin;
import mindustry.net.Administration;
import mindustry.world.blocks.distribution.MassDriver;

import static hex.components.Bundle.findLocale;
import static hex.components.Bundle.get;
import static mindustry.Vars.*;

public class Main extends Plugin {

    public static Seq<Hex> hexes = new Seq<>();
    public static Seq<Human> humans = new Seq<>();
    public static Rules rules = new Rules();

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

        netServer.admins.actionFilters.clear();
        netServer.admins.addActionFilter(action -> false);
        netServer.assigner = (player, players) -> Team.derelict;

        UnitTypes.crawler.defaultController = HexSuicideAI::new;
        UnitTypes.crawler.weapons.clear();
        UnitTypes.mono.defaultController = HexMinerAI::new;
        UnitTypes.poly.defaultController = HexBuilderAI::new;
        UnitTypes.poly.weapons.clear();

        Blocks.unloader.solid = false; // why?
        Blocks.groundFactory.solid = false;
        ((MassDriver) Blocks.massDriver).bullet.damage = 0f;

        rules.enemyCoreBuildRadius = 0f;
        rules.unitCap = 16;
        rules.infiniteResources = true;
        rules.damageExplosions = false;
        rules.fire = false;
        rules.waves = false;
        rules.canGameOver = false;
        rules.revealedBlocks.add(Blocks.duct);
        rules.modeName = "Hex Industry";

        for (Team team : Team.all) rules.teams.get(team).cheat = true;

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

        handler.<Player>register("join", "<player>", "Offer the player to team up.", (args, player) -> Politics.join(args[0], player));

        handler.<Player>register("spectate", "Watching the game is fun too!", (args, player) -> Politics.spectate(player));

        handler.<Player>register("author", "Plugin creators.", (args, player) -> player.sendMessage(get("author", findLocale(player))));
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("host", "Initialize new game.", args -> {
            Generator.play();
            state.rules = rules;

            logic.play();
            netServer.openServer();
        });

        handler.register("heck", "DO NOT USE THIS ONLY FOR TESTING!", args -> {
            humans.each(h -> h.production.all(1000));
            netServer.admins.actionFilters.clear();
            Administration.Config.strict.set(false);
        });
    }
}
