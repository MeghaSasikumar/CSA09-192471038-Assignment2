import java.util.*;

/**
 * Q5 - Design study around the principle "a ranking is correct only if the comparison is consistent".
 * Consistent = (1) antisymmetric: sign(cmp(a,b)) == -sign(cmp(b,a)); (2) transitive; (3) cmp==0 means
 * a and b are interchangeable for ranking (same sign against every other element).
 *
 * DESIGN DECISION: Team does NOT implement Comparable. A team has no single 'natural' order (points? name?),
 * and the same Team could be ranked differently in another competition. Comparators are separate objects
 * (Comparator.comparing / thenComparing / reversed), so Leaderboard<T> accepts any Comparator<? super T>.
 * Bounded types are only needed for the convenience factory below (T extends Comparable<? super T>).
 * Wildcard List<? extends T> is useful for addAll: a Leaderboard<Team> can accept a List<ChampionTeam>.
 */
public class Q5Study {
    static final Comparator<Team> GOOD = Comparator.comparingInt((Team t) -> t.points).reversed()
            .thenComparing(Comparator.comparingInt((Team t) -> t.goalDiff).reversed()).thenComparing(t -> t.name);
    // faulty 1: subtraction -> overflow for extreme values
    static final Comparator<Team> SUBTRACT = (a, b) -> b.points - a.points != 0 ? b.points - a.points : (b.goalDiff - a.goalDiff != 0 ? b.goalDiff - a.goalDiff : a.name.compareTo(b.name));
    // faulty 2: ignores the name tie-breaker
    static final Comparator<Team> NO_NAME = Comparator.comparingInt((Team t) -> t.points).reversed()
            .thenComparing(Comparator.comparingInt((Team t) -> t.goalDiff).reversed());
    // faulty 3: never returns 0 for ties (violates antisymmetry)
    static final Comparator<Team> NEVER_ZERO = (a, b) -> a.points > b.points ? -1 : 1;

    static class ChampionTeam extends Team { ChampionTeam(String n, int p, int g) { super(n, p, g); } }

    /** brute-force check of the three consistency rules; returns number of violations */
    static int[] violations(List<Team> ts, Comparator<Team> c) {
        int anti = 0, trans = 0, eq = 0;
        for (Team a : ts) for (Team b : ts) {
            if (Integer.signum(c.compare(a, b)) != -Integer.signum(c.compare(b, a))) anti++;
            for (Team x : ts) {
                if (c.compare(a, b) < 0 && c.compare(b, x) < 0 && !(c.compare(a, x) < 0)) trans++;
                if (c.compare(a, b) == 0 && Integer.signum(c.compare(a, x)) != Integer.signum(c.compare(b, x))) eq++;
            }
        }
        return new int[]{anti, trans, eq};
    }

    static <T extends Comparable<? super T>> List<T> rankNatural(Collection<? extends T> in) { List<T> l = new ArrayList<>(in); Collections.sort(l); return l; }

    public static void main(String[] args) {
        // --- data set 1: several ties at each level
        List<Team> d1 = new ArrayList<>(List.of(new Team("Panthers", 9, 4), new Team("Eagles", 9, 6), new Team("Cobras", 6, 1), new Team("Bisons", 9, 4),
                new Team("Aces", 6, 1), new Team("Dragons", 6, 3), new Team("Falcons", 3, 0), new Team("Hawks", 3, 0), new Team("Lions", 9, 6), new Team("Tigers", 3, -2)));
        System.out.println("DATA SET 1 (ties on points, on points+goalDiff, and on all three levels' prefixes)");
        System.out.println("expected by hand: [Eagles(9,6), Lions(9,6), Bisons(9,4), Panthers(9,4), Dragons(6,3), Aces(6,1), Cobras(6,1), Falcons(3,0), Hawks(3,0), Tigers(3,-2)]");
        List<Team> r1 = new ArrayList<>(d1); r1.sort(GOOD);
        System.out.println("observed         : " + r1);
        List<Team> shuffled = new ArrayList<>(d1); Collections.shuffle(shuffled, new Random(9)); shuffled.sort(GOOD);
        System.out.println("same after shuffling the input (input-order independent): " + shuffled.equals(r1));

        // --- data set 2: designed to break faulty comparators
        List<Team> d2 = new ArrayList<>(List.of(new Team("A", Integer.MAX_VALUE, 0), new Team("B", -5, 0), new Team("C", 0, 0), new Team("D", Integer.MIN_VALUE + 3, 0),
                new Team("E", 7, 2), new Team("F", 7, 2), new Team("G", 7, 2)));
        System.out.println("\nDATA SET 2 (extreme points + identical teams) - violations (antisymmetry, transitivity, equal-consistency):");
        Object[][] cs = {{"GOOD (comparingInt.reversed.thenComparing)", GOOD}, {"SUBTRACT (b.points - a.points)", SUBTRACT}, {"NO_NAME (ignores name tie-break)", NO_NAME}, {"NEVER_ZERO (ties return 1)", NEVER_ZERO}};
        for (Object[] c : cs) {
            @SuppressWarnings("unchecked") Comparator<Team> cmp = (Comparator<Team>) c[1];
            System.out.println(String.format("  %-45s -> %s", c[0], Arrays.toString(violations(d2, cmp))));
        }
        System.out.println("  Why SUBTRACT breaks: compare(MAX_VALUE, -5): -5 - MAX_VALUE = -(MAX+5) overflows to a positive number, so A looks WORSE than B,"
                + " while B vs C, C vs A are fine -> cycle. 'Equal teams E,F,G' under NO_NAME are ordered by whichever came first (not input independent):");
        List<Team> t1 = new ArrayList<>(List.of(new Team("Panthers", 9, 4), new Team("Bisons", 9, 4))), t2 = new ArrayList<>(List.of(new Team("Bisons", 9, 4), new Team("Panthers", 9, 4)));
        t1.sort(NO_NAME); t2.sort(NO_NAME);
        System.out.println("  NO_NAME: input [Panthers,Bisons] -> " + t1 + " ; input [Bisons,Panthers] -> " + t2 + "   (different result for the same data)");
        // can TimSort detect a broken comparator?
        Random rnd = new Random(1); List<Team> big = new ArrayList<>();
        for (int i = 0; i < 200; i++) big.add(new Team("T" + i, rnd.nextInt(), rnd.nextInt(5)));
        for (Object[] c : cs) {
            @SuppressWarnings("unchecked") Comparator<Team> cmp = (Comparator<Team>) c[1];
            try { List<Team> l = new ArrayList<>(big); l.sort(cmp); System.out.println("  sort of 200 random-int-points teams with " + c[0] + ": no exception (result may still be wrong)"); }
            catch (IllegalArgumentException e) { System.out.println("  sort of 200 teams with " + c[0] + ": IllegalArgumentException: " + e.getMessage()); }
        }

        // --- Leaderboard<T> reuse and wildcard
        Leaderboard<Team> board = new Leaderboard<>(GOOD);
        List<ChampionTeam> champs = List.of(new ChampionTeam("Ravens", 12, 9), new ChampionTeam("Owls", 12, 9));
        for (Team t : champs) board.add(t);        // elements of a subtype accepted
        System.out.println("\nLeaderboard<Team> with subtype elements: " + board.ranking());
        Leaderboard<String> names = new Leaderboard<>(Comparator.comparing(String::length).thenComparing(Comparator.naturalOrder()));
        for (String s : List.of("kiwi", "fig", "banana", "apple", "pear")) names.add(s);
        System.out.println("Same Leaderboard<T> class reused for Strings: " + names.ranking());
        System.out.println("Bounded generic helper (T extends Comparable<? super T>): " + rankNatural(List.of(5, 3, 9, 1)));

        // --- alternatives: Comparable vs 3 stable sorts vs comparator chain, n = 400,000 with many ties
        Random r = new Random(5); List<Team> big2 = new ArrayList<>();
        for (int i = 0; i < 400_000; i++) big2.add(new Team("Team" + r.nextInt(50_000), r.nextInt(30), r.nextInt(20) - 10));
        long sorts = 0;
        List<Team> a = null, b = null, c = null; double[] tm = new double[3];
        for (int rep = 0; rep < 6; rep++) {
            List<Team> x = new ArrayList<>(big2); long n0 = System.nanoTime(); x.sort(GOOD); long n1 = System.nanoTime();
            List<Team> y = new ArrayList<>(big2); y.sort(Comparator.comparing((Team t) -> t.name)); y.sort(Comparator.comparingInt((Team t) -> t.goalDiff).reversed()); y.sort(Comparator.comparingInt((Team t) -> t.points).reversed()); long n2 = System.nanoTime();
            List<Team> z = new ArrayList<>(big2); long n3 = System.nanoTime(); z.sort(COMPARABLE_ORDER); long n4 = System.nanoTime();
            if (rep >= 2) { tm[0] += (n1 - n0) / 1e6 / 4; tm[1] += (n2 - n1) / 1e6 / 4; tm[2] += (n4 - n3) / 1e6 / 4; }
            a = x; b = y; c = z;
        }
        System.out.printf("%nn=400,000 teams (avg of 4 runs): comparator chain = %.1f ms (1 sort) | three stable sorts = %.1f ms (3 sorts) | Comparable.compareTo = %.1f ms (1 sort)%n", tm[0], tm[1], tm[2]);
        System.out.println("identical result from all three approaches: " + (same(a, b) && same(a, c)));
    }
    static boolean same(List<Team> x, List<Team> y) { for (int i = 0; i < x.size(); i++) if (x.get(i) != y.get(i)) return false; return true; }
    /** what 'Team implements Comparable<Team>' would look like (written as a comparator over the same logic) */
    static final Comparator<Team> COMPARABLE_ORDER = (p, q) -> { int c = Integer.compare(q.points, p.points); if (c != 0) return c; c = Integer.compare(q.goalDiff, p.goalDiff); return c != 0 ? c : p.name.compareTo(q.name); };
}
