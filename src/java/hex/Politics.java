package hex;

import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Time;
import hex.components.MenuListener;
import hex.content.Fractions;
import hex.content.Weapons;
import hex.types.Hex;
import hex.types.Human;
import hex.types.Weapon;
import mindustry.gen.Player;

import java.util.Locale;

import static hex.Main.hexes;
import static hex.components.Bundle.get;
import static hex.components.Bundle.findLocale;
import static hex.components.MenuListener.*;

public class Politics {

    public static final Seq<Offer> offers = new Seq<>();
    public static final ObjectMap<Human, Hex> attacked = new ObjectMap<>();
    public static final ObjectMap<String, Human> left = new ObjectMap<>();

    public static void join(Player player) {
        player.name(player.name.replace(Human.prefix, ""));
        Human human = left.get(player.con.uuid);
        if (human != null) {
            human.player(player);
            left.remove(player.con.uuid);

            return;
        } else if (hexes.count(Hex::isClosed) == 0) return;

        Locale loc = findLocale(player);
        MenuListener.menu(player, fractionChoose, get("fract.title", loc), get("fract.text", loc),
                Fractions.names(loc, true), option -> Fractions.from(option).desc(loc));
    }

    public static void leave(Player player) {
        Human human = Human.from(player);
        if (human == null) return;

        left.put(player.con.uuid, human);
        human.unoffer();
        human.lose = Time.runTask(7200f, () -> {
            human.lose();
            left.remove(player.con.uuid);
        });
    }

    public static void spectate(Player player) {
        Human human = Human.from(player);
        if (human == null) join(player);
        else human.lose();
    }

    public static void attack(Hex hex, Human human) {
        attacked.put(human, hex);
        if (Weapon.attackable(human)) MenuListener.menu(human.player, weaponChoose, get("weapon.title", human.locale), get("weapon.text", human.locale),
                Weapons.names(human), option -> Weapons.from(human.weapons).get(option).desc(human));
    }

    public static void join(String[] args, Player player) {
        Human from = Human.from(player); // you can accept last offer without a nickname
        Human to = args.length == 0 ? findLast(from) : Human.from(args[0]);

        if (from == null) player.sendMessage(get("offer.spectator", findLocale(player)));
        else if (to == null || from == to) player.sendMessage(get("offer.notfound", from.locale));
        else if (contains(to, from)) { // a bit of code that is hard to understand, but I don't care :D
            player.sendMessage(get("offer.accepted", from.locale));
            to.player.sendMessage(player.coloredName() + get("offer.accept", to.locale));

            to.teamup(from);
            from.lead();
        } else {
            if (from.leader != from || !from.slaves().isEmpty()) player.sendMessage(get("offer.notfree", from.locale));
            else if (to.leader != to) player.sendMessage(to.player.coloredName() + get("offer.notleader", from.locale));
            else if (contains(from, to)) player.sendMessage(get("offer.already", from.locale));
            else {
                player.sendMessage(get("offer.sent", from.locale));
                to.player.sendMessage(player.coloredName() + get("offer.join", to.locale));
                offers.add(new Offer(from, to));
            }
        }
    }

    private static boolean contains(Human from, Human to) {
        return offers.contains(offer -> offer.from == from && offer.to == to);
    }

    private static Human findLast(Human to) {
        Offer last = offers.reverse().find(offer -> offer.to == to);
        offers.reverse();
        return last == null ? null : last.from;
    }

    public static class Offer {
        public Human from;
        public Human to;

        public Offer(Human from, Human to) {
            this.from = from;
            this.to = to;
        }
    }
}
