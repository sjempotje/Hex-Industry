package hex;

import hex.types.*;
import hex.content.*;
import arc.util.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import mindustry.gen.*;
import mindustry.mod.*;

import static mindustry.Vars.*;

public class Main extends Plugin {

	public static boolean initialized;

	public static Seq<Hex> hexes = new Seq<>();
	public static Seq<Human> humans = new Seq<>();

	@Override
	public void init() {
		Schems.load();
		Fractions.load();
		HexBuilds.load();
		Buttons.load();

		netServer.admins.actionFilters.clear();
		netServer.admins.addActionFilter(action -> false);

		Timer.schedule(() -> {
			if (initialized) humans.each(ppl -> ppl.production.update());
		}, 0f, 1f);

		Timer.schedule(() -> {
			humans.each(ppl -> {
				Call.setHudText(ppl.player.con, "[gray]hex #" + String.valueOf(ppl.location().id) + 
												"\n[green]" + ppl.production.ppl() + "[][]\\" + ppl.production.pplMax());
			});
		}, 0f, .01f);
	}

	@Override
	public void registerServerCommands(CommandHandler handler) {}

	@Override
	public void registerClientCommands(CommandHandler handler) {
		handler.register("init", "Initialize new game", args -> {
			// change rules
			state.rules.enemyCoreBuildRadius = 0f;
			state.rules.unitCap = 16;
			state.rules.infiniteResources = true;

			// generate hex-map
			Point2 start = new Point2();
			Point2 point = new Point2();

			while (true) {
				hexes.add(new Hex(point.x, point.y));
				point.add(38, 0);

				if (!Hex.bounds(point.x, point.y)) {
					start.add(19 * (start.x == 0 ? 1 : -1), 11);
					point.set(start);

					if (!Hex.bounds(start.x, start.y)) break;
				}
			}

			// synchronize the world
			Call.worldDataBegin();
			Groups.player.each(ppl -> netServer.sendWorldData(ppl));

			// ask unit type & abilities
			Groups.player.each(ppl -> humans.add(new Human(ppl, Fractions.horde)));

			// spawn a citadel in a random hex
			humans.each(ppl -> ppl.init(hexes.get(Mathf.random(hexes.size - 1))));

			initialized = true;
		});
	}
}
