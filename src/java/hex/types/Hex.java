package hex.types;

import arc.func.Cons3;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Point2;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Time;
import hex.Generator;
import hex.content.Buttons;
import hex.content.HexBuilds;
import hex.types.buttons.BuildButton;
import hex.types.buttons.Button;
import hex.types.buttons.OpenButton;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.gen.Call;
import mindustry.graphics.Pal;
import mindustry.world.blocks.environment.Floor;
import useful.Bundle;

import static hex.Main.*;
import static hex.Generator.*;
import static hex.content.HexSchematics.*;
import static mindustry.Vars.*;

public class Hex {

    public static final int width = 27;
    public static final int height = 25;
    public static final int radius = 52;
    public static final Rand random = new Rand();
    public static final float basedst = 600f;

    protected static int _id;

    public int x;
    public int y;
    public int cx;
    public int cy;
    public float fx;
    public float fy;

    public int health;
    public float step;
    public Color color;
    public int instdeg;

    public Human owner;
    public int id;

    public Seq<Button> buttons = new Seq<>();
    public HexBuild build;

    public boolean open;
    public boolean busy;
    public boolean base;
    public HexEnv env;
    public byte door;

    public Hex(Point2 pos) {
        x = pos.x;
        y = pos.y;
        cx = x + width / 2;
        cy = y + height / 2;
        fx = cx * tilesize;
        fy = cy * tilesize;

        base = (cx - 13) % 57 == 0 && (cy - ((cx - 13) % 114 == 0 ? 12 : 45)) % 66 == 0;
        env = base ? HexEnv.base : HexEnv.get();
        door = (byte) random.nextLong();
        id = _id++;

        closed.floor(x, y);
    }

    public void update(Human human) {
        if (busy) for (float i = 0; i < 60; i += 12f) Time.run(i, () -> smoke(human));
        else for (int deg = 0; deg < health; deg++) {
            float dx = fx + Mathf.cosDeg(deg * step) * radius;
            float dy = fy + Mathf.sinDeg(deg * step) * radius;
            Call.effect(human.player.con, Fx.mineSmall, dx, dy, 0, color);
        }
        
        if (owner == null || owner == human.leader) buttons.each(b -> b.update(human));
        if (base && open) for (float i = 0; i < 60; i += 2f) Time.run(i, () -> inst(human));
    }

    public void smoke(Human human) {
        float deg = Mathf.random(360f);
        float dst = Mathf.random(110f);
        float dx = fx + Mathf.cosDeg(deg) * dst;
        float dy = fy + Mathf.sinDeg(deg) * dst;
        Call.effect(human.player.con, Fx.smokeCloud, dx, dy, 0, Color.white);
    }

    public void inst(Human human) {
        float dx = fx + Mathf.cosDeg(instdeg += 12f) * basedst;
        float dy = fy + Mathf.sinDeg(instdeg) * basedst;
        Call.effect(human.player.con, Fx.instShoot, dx, dy, instdeg, Color.white);
    }

    public static boolean bounds(Point2 pos) {
        return pos.x + width > world.width() || pos.y + height > world.height();
    }

    public void build(HexBuild build) {
        if (build == null) return; // this happens sometimes
        build.build(this);
        this.build = build;

        health = build.health;
        step = 360f / health;
        damage(0); // update color

        if (base) onEmpty(() -> Generator.setc(cx, cy, isCitadel() ? Blocks.coreNucleus : Blocks.coreShard, owner.player.team()));
    }

    public boolean damage(int damage) {
        health = Math.max(health - damage, 0);
        color = Color.valueOf("38d667").lerp(Pal.health, 1 - (float) health / build.health);
        cooldown(damage == 0 ? 600f : Time.toMinutes);
        return health <= 0;
    }

    public void cooldown(float time) {
        busy = true;
        Time.run(time, () -> busy = false);
    }

    public void lose(Human attacker) {
        if (owner == null) return; // this happens sometimes
        build.destroy(owner.production);
        if (attacker != null) {
            owner.production.check(owner);
            attacker.stats.destroyed++;
        }
        clear();

        Human human = Human.from(this);
        if (base && human != null) human.lose();
    }

    public void open() {
        door(door).airNet(x, y);
        open = true;
        env.build(this);

        onEmpty(() -> openedNeighbours().each(bour -> {
            if (bour.isClosed()) bour.buttons.add(new OpenButton(bour));
        }));
    }

    public void clear() {
        if (owner == null) return; // this happens sometimes
        build.explode(this);
        env.build(this);

        build = null;
        owner = null;
        health = 0;
    }

    public void clearButtons() {
        buttons.each(Buttons::unregister);
        buttons.clear();
        env.terrain(this);
    }

    public String health(Human human) {
        return health == 0 ? Bundle.get("hex.zerohp", human.locale) : Bundle.format("hex.health", human.locale, color, health, build.health);
    }

    public void attacked(Human human, Weapon weapon) {
        owner.player.sendMessage(attacked(owner, human, weapon));
        owner.slaves().each(slave -> slave.player.sendMessage(attacked(slave, human, weapon)));
    }

    private String attacked(Human to, Human from, Weapon weapon) {
        return Bundle.format("hex.attack", to.locale, from.player.coloredName(), cx, cy, Bundle.get(build.name, to.locale), health(to), Bundle.get(weapon.name + ".name", to.locale));
    }

    public Seq<Hex> neighbours() {
        return hexes.select(hex -> pos().within(hex.pos(), 210f));
    }

    public Seq<Hex> openedNeighbours() {
        return neighbours().select(hex -> world.tile((hex.cx + cx) / 2, (hex.cy + cy) / 2).block() == Blocks.air && hex != this);
    }

    public Vec2 pos() {
        return new Vec2(fx, fy);
    }

    public Point2 point() {
        return new Point2(cx, cy);
    }

    public boolean isClosed() {
        return !open;
    }

    public boolean isCaptured(Human owner) {
        return hexes.contains(hex -> hex.base && pos().within(hex.pos(), basedst) && hex.owner == owner.leader);
    }

    public boolean isCitadel() {
        return owner != null && (owner.citadel == this || owner.slaves().contains(h -> h.citadel == this));
    }

    public enum HexEnv {
        citadel(citadelLr1, citadelLr2) {
            // there is nothing, because the citadel building will add the necessary buttons
            public void addButtons(Cons3<HexBuild, Integer, Integer> add) {}
        },
        base(baseLr1, baseLr2) {
            public void addButtons(Cons3<HexBuild, Integer, Integer> add) {
                add.get(HexBuilds.base, 0, 0);
            }
        },
        titanium(titaniumLr1, titaniumLr2) {
            public void addButtons(Cons3<HexBuild, Integer, Integer> add) {
                add.get(HexBuilds.compressor, 4, 4);
                add.get(HexBuilds.miner, -6, -3);
            }
        },
        thorium(thoriumLr1, thoriumLr2) {
            public void addButtons(Cons3<HexBuild, Integer, Integer> add) {
                add.get(HexBuilds.thory, 0, 0);
            }
        },
        oil(oilLr1, oilLr2) {
            public void addButtons(Cons3<HexBuild, Integer, Integer> add) {
                add.get(HexBuilds.oil, 7, 2);
            }
        },
        water(waterLr1, waterLr2) {
            public void addButtons(Cons3<HexBuild, Integer, Integer> add) {
                add.get(HexBuilds.water, 0, 0);
            }
        },
        cryo(cryoLr1, cryoLr2) {
            public void addButtons(Cons3<HexBuild, Integer, Integer> add) {
                add.get(HexBuilds.cryo, -3, -6);
            }
        },
        forest(forestLr1, forestLr2) {
            public void addButtons(Cons3<HexBuild, Integer, Integer> add) {
                add.get(HexBuilds.city, -1, 1);
            }
        },
        spore(sporeLr1, sporeLr2) {
            public void addButtons(Cons3<HexBuild, Integer, Integer> add) {
                add.get(HexBuilds.cultivator, -7, -2);
                add.get(HexBuilds.maze, 4, -6);
            }
        },
        canyon(canyonLr1, canyonLr2) {
            public void addButtons(Cons3<HexBuild, Integer, Integer> add) {}
        };

        private final HexSchematic Lr1;
        private final HexSchematic Lr2;

        HexEnv(HexSchematic floor, HexSchematic block) {
            Lr1 = floor;
            Lr2 = block;
        }

        public static HexEnv get() {
            return Mathf.chance(.6f) ? HexEnv.titanium : HexEnv.thorium;
        }

        public void build(Hex hex) {
            hex.clearButtons();
            addButtons((build, x, y) -> hex.buttons.add(new BuildButton(build, hex, hex.cx + x, hex.cy + y)));
        }

        public void terrain(Hex hex) {
            Lr1.floorNet(hex.x, hex.y);
            Lr1.airNet(hex.x, hex.y);

            Lr2.tiles.each(st -> {
                if (st.block instanceof Floor) Generator.set(st.x + hex.x, st.y + hex.y, null, st.block);
                else Generator.set(st.x + hex.x, st.y + hex.y, st.block);
            });
        }

        public abstract void addButtons(Cons3<HexBuild, Integer, Integer> add);
    }
}
