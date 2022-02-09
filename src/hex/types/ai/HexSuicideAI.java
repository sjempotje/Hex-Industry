package hex.types.ai;

import arc.graphics.Color;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Time;
import hex.types.Hex;
import mindustry.ai.Pathfinder;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.entities.units.AIController;
import mindustry.gen.Call;
import mindustry.world.meta.BlockFlag;

import static hex.Main.hexes;
import static mindustry.Vars.tilesize;
import static mindustry.Vars.world;

public class HexSuicideAI extends AIController {

    public int state = 5;

    public Hex hex;
    public Seq<Vec2> marks;

    public HexSuicideAI() {
        Time.runTask(60f, () -> {
            hex = hexes.min(h -> h.pos().dst(unit));
            marks = Seq.with(from(7, -3), from(3, -3), from(3, 1));
        });
    }

    @Override
    public void updateMovement() {
        if (state < marks.size) {
            Vec2 pos = marks.get(state);
            moveTo(pos, 0f);

            if (unit.within(pos, 2f)) state++;
        } else if (state == 3) {
            Call.takeItems(world.build(hex.cx, hex.cy + 1), Items.sporePod, 10, unit);
            state = 4;
        } else if (state == 4) {
            pathfind(Pathfinder.fieldRally);
            target = targetFlag(unit.x, unit.y, BlockFlag.rally, false);

            if (unit.within(target, 10f)) {
                Call.effect(Fx.spawn, unit.x, unit.y, 0, Color.white);
                unit.controlWeapons(true);
            }
        }
    }

    public Vec2 from(int x, int y) {
        return new Vec2(hex.fx + x * tilesize + Mathf.random(8f), hex.fy + y * tilesize + Mathf.random(8f));
    }
}
