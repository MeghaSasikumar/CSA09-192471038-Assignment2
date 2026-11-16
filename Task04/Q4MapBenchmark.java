import java.io.PrintWriter;
import java.util.*;

/**
 * Q4 - Pharmacy stock: HashMap vs TreeMap vs LinkedHashMap (vs Hashtable, vs sorted array + binarySearch).
 *
 * PREDICTION (before running):
 *   put/get: HashMap & LinkedHashMap O(1) avg, TreeMap O(log n), Hashtable O(1) avg but synchronised.
 *   code-order report: TreeMap natural O(n); HashMap/LinkedHashMap need an extra sort O(n log n).
 *   insertion-order report: only LinkedHashMap natural O(n); HashMap/TreeMap iterate in the WRONG order
 *   (need a parallel list of codes -> extra O(n) memory).
 *   sorted array: lookup O(log n), insert O(n) (shifting), code-order report O(n), least memory.
 *   Acceptability criterion chosen: one lookup <= 1 microsecond average (a pharmacist waits ~0 ms; the
 *   100,000 lookups/day cost then < 0.1 s).
 */
public class Q4MapBenchmark {
    static class Medicine { final String code; final String name; int stock;
        Medicine(String c, String n, int s) { code = c; name = n; stock = s; } }
    static PrintWriter raw; static long sink;
    static final int LOOKUPS = 100_000, WARM = 2, REPS = 5;

    interface Job { void run(); }
    static double time(String tag, int n, Job j) {
        for (int w = 0; w < WARM; w++) j.run();
        double tot = 0;
        for (int r = 0; r < REPS; r++) { long t0 = System.nanoTime(); j.run(); double ms = (System.nanoTime() - t0) / 1e6; tot += ms; raw.println(tag + "," + n + "," + r + "," + ms); }
        return tot / REPS;
    }

    static boolean sameOrder(Collection<String> keys, List<String> expected) { return new ArrayList<>(keys).equals(expected); }

    public static void main(String[] args) throws Exception {
        raw = new PrintWriter("q4_raw.csv"); raw.println("measure,n,run,ms");
        for (int n : new int[]{1_000, 20_000, 200_000}) {
            Random rnd = new Random(7);
            List<Integer> ids = new ArrayList<>(); for (int i = 0; i < n; i++) ids.add(i * 3 + 1);
            Collections.shuffle(ids, rnd);
            String[] codes = new String[n]; Medicine[] meds = new Medicine[n];
            for (int i = 0; i < n; i++) { codes[i] = String.format("MED%07d", ids.get(i)); meds[i] = new Medicine(codes[i], "drug" + i, 100 + i % 50); }
            List<String> insertionOrder = Arrays.asList(codes);
            List<String> codeOrder = new ArrayList<>(insertionOrder); Collections.sort(codeOrder);
            String[] probe = new String[LOOKUPS]; for (int i = 0; i < LOOKUPS; i++) probe[i] = codes[rnd.nextInt(n)];

            Map<String, Medicine> hm = new HashMap<>(), lhm = new LinkedHashMap<>(), tm = new TreeMap<>(), ht = new Hashtable<>();
            Object[][] maps = {{"HashMap", hm}, {"LinkedHashMap", lhm}, {"TreeMap", tm}, {"Hashtable", ht}};
            System.out.printf("%n======== N = %,d medicines ========%n", n);
            System.out.printf("%-14s %10s %14s %14s %16s %16s %12s %12s%n", "structure", "put all ms", "100k get ms", "ns per get", "native iter ms", "code-order rpt ms", "order=code?", "order=added?");
            for (Object[] mm : maps) {
                @SuppressWarnings("unchecked") Map<String, Medicine> m = (Map<String, Medicine>) mm[1]; String nm = (String) mm[0];
                double put = time(nm + "-put", n, () -> { m.clear(); for (Medicine x : meds) m.put(x.code, x); });
                double get = time(nm + "-get", n, () -> { long s = 0; for (String c : probe) s += m.get(c).stock; sink += s; });
                double it = time(nm + "-iter", n, () -> { long s = 0; for (Medicine x : m.values()) s += x.stock; sink += s; });
                // code-order report: natural for TreeMap, otherwise copy keys + sort + lookup
                double rpt = time(nm + "-codereport", n, () -> {
                    long s = 0;
                    if (m instanceof TreeMap) { for (Medicine x : m.values()) s += x.stock; }
                    else { List<String> ks = new ArrayList<>(m.keySet()); Collections.sort(ks); for (String k : ks) s += m.get(k).stock; }
                    sink += s; });
                System.out.printf("%-14s %10.2f %14.2f %14.0f %16.2f %16.2f %12s %12s%n", nm, put, get, get * 1e6 / LOOKUPS, it, rpt,
                        sameOrder(m.keySet(), codeOrder), sameOrder(m.keySet(), insertionOrder));
            }
            System.out.println("(order= columns compare the map's OWN iteration order with the required order; Hashtable also iterates in hash order, not insertion/code order)");

            // insertion-order report from TreeMap/HashMap needs an extra list of codes (extra memory + upkeep)
            List<String> side = new ArrayList<>(); for (Medicine x : meds) side.add(x.code);
            double extra = time("TreeMap-insertionreport-with-sidelist", n, () -> { long s = 0; for (String k : side) s += tm.get(k).stock; sink += s; });
            System.out.printf("insertion-order report from TreeMap using a side ArrayList of codes: %.2f ms (log n lookups per entry) + n extra references%n", extra);

            // sorted array + binarySearch
            Medicine[] sorted = meds.clone(); Arrays.sort(sorted, Comparator.comparing(x -> x.code));
            Comparator<Medicine> byCode = Comparator.comparing(x -> x.code);
            double sortMs = time("array-build-sort", n, () -> { Medicine[] c = meds.clone(); Arrays.sort(c, byCode); sink += c.length; });
            double aget = time("array-get", n, () -> { long s = 0; Medicine key; for (String c : probe) { key = new Medicine(c, "", 0); s += sorted[Arrays.binarySearch(sorted, key, byCode)].stock; } sink += s; });
            double ains = time("array-insert1000", n, () -> { Medicine[] a = new Medicine[n + 1001]; System.arraycopy(sorted, 0, a, 0, n); int size = n;
                for (int i = 0; i < 1000; i++) { Medicine nw = new Medicine(String.format("MED%07d", 10_000_000 + i * 977 % 1000003), "new", 1);
                    int pos = Arrays.binarySearch(a, 0, size, nw, byCode); if (pos < 0) pos = -pos - 1; System.arraycopy(a, pos, a, pos + 1, size - pos); a[pos] = nw; size++; } sink += size; });
            double tins = time("TreeMap-insert1000", n, () -> { Map<String, Medicine> t = new TreeMap<>(tm); for (int i = 0; i < 1000; i++) t.put(String.format("MED%07d", 10_000_000 + i * 977 % 1000003), new Medicine("x", "n", 1)); sink += t.size(); });
            double hins = time("HashMap-insert1000", n, () -> { Map<String, Medicine> t = new HashMap<>(hm); for (int i = 0; i < 1000; i++) t.put(String.format("MED%07d", 10_000_000 + i * 977 % 1000003), new Medicine("x", "n", 1)); sink += t.size(); });
            System.out.printf("sorted array: build(sort)=%.2f ms, 100k binarySearch gets=%.2f ms (%.0f ns each), 1000 inserts (incl. copying the table once)= %.2f ms; TreeMap copy+1000 inserts=%.2f ms; HashMap copy+1000 inserts=%.2f ms%n",
                    sortMs, aget, aget * 1e6 / LOOKUPS, ains, tins, hins);
        }
        raw.close(); System.out.println("sink=" + sink);
    }
}
