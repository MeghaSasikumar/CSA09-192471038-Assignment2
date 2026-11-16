import java.lang.management.ManagementFactory;
import java.util.*;

/**
 * Q7 - Diagnose, redesign, validate.  Chosen solution: Q4 pharmacy inventory.
 *
 * A. DIAGNOSIS (from Q4 evidence, N=200,000): lookups are cheap (LinkedHashMap ~57 ns/get) and the insertion-order report is free,
 *    BUT every code-order report costs ~134 ms vs 9.7 ms when the data is already ordered, because the work
 *    "copy keys/values + sort" (O(n log n) comparisons, an extra n-element array) is REPEATED for every report even though
 *    stock updates never change the order and only a few new medicines appear between reports.
 *    Cause: a hash-based map has no order on its keys, so order has to be rebuilt from scratch each time.
 * B. OBJECTIVE: report cost proportional to the CHANGE since the last report, not to n log n; a code-order report with k new
 *    medicines should cost O(n + k log k) comparisons ~ linear, at least 5x faster than re-sorting when k << n; identical output.
 * C. REDESIGN: keep LinkedHashMap (O(1) lookup, insertion order). Add a cached sorted view (ArrayList<Medicine> of the SAME objects,
 *    so stock updates are visible automatically) + a small 'pending' list of medicines added since the last report.
 *    At report time: sort only pending (k log k) and MERGE with the cached view (n + k comparisons). No pending => zero extra work.
 *    Properties exploited: (1) stock updates do not change the key order; (2) merge of two sorted runs is linear.
 *    Trade-offs: n extra references (8 bytes each), first report after a bulk load still costs a full sort, deletions need a
 *    tombstone/removal step (not implemented here; remove from the view with binarySearch = O(n) shift).
 * D. PREDICTION: with k new medicines per report and n = 50,000: report time drops >= 5x for k <= 100 and comparisons drop from
 *    ~n log2 n (~0.8M) to ~n+k log k (~50-55k); advantage shrinks as k approaches n; for k >= n/2 about equal.
 */
public class Q7Redesign {
    static class Medicine { final String code; final String name; int stock; Medicine(String c, String n, int s) { code = c; name = n; stock = s; } }
    static class CountingCmp implements Comparator<Medicine> { long count; public int compare(Medicine a, Medicine b) { count++; return a.code.compareTo(b.code); } }

    interface Inventory { void add(Medicine m); Medicine get(String code); long codeOrderReport(); long comparisons(); }

    /** original: LinkedHashMap + sort a copy of the values on every code-order report */
    static class Original implements Inventory {
        final Map<String, Medicine> map = new LinkedHashMap<>(); final CountingCmp cmp = new CountingCmp();
        public void add(Medicine m) { map.put(m.code, m); }
        public Medicine get(String c) { return map.get(c); }
        public long codeOrderReport() { List<Medicine> l = new ArrayList<>(map.values()); l.sort(cmp); return checksum(l); }
        public long comparisons() { return cmp.count; }
    }
    /** redesign: LinkedHashMap + cached sorted view + pending list merged at report time */
    static class Redesigned implements Inventory {
        final Map<String, Medicine> map = new LinkedHashMap<>(); final CountingCmp cmp = new CountingCmp();
        List<Medicine> view = new ArrayList<>(); final List<Medicine> pending = new ArrayList<>();
        public void add(Medicine m) { map.put(m.code, m); pending.add(m); }
        public Medicine get(String c) { return map.get(c); }
        public long codeOrderReport() {
            if (!pending.isEmpty()) {
                pending.sort(cmp);                                   // k log k
                List<Medicine> merged = new ArrayList<>(view.size() + pending.size());
                int i = 0, j = 0;
                while (i < view.size() && j < pending.size()) merged.add(cmp.compare(view.get(i), pending.get(j)) <= 0 ? view.get(i++) : pending.get(j++));
                while (i < view.size()) merged.add(view.get(i++));
                while (j < pending.size()) merged.add(pending.get(j++));
                view = merged; pending.clear();
            }
            return checksum(view);
        }
        public long comparisons() { return cmp.count; }
    }
    static long checksum(List<Medicine> l) { long h = 1; for (Medicine m : l) h = h * 31 + m.code.hashCode() + m.stock; return h; }

    static final com.sun.management.ThreadMXBean TMX = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    static long alloc() { return TMX.getThreadAllocatedBytes(Thread.currentThread().getId()); }
    static Medicine med(int id) { return new Medicine(String.format("MED%07d", id), "drug", 100 + id % 50); }

    /** returns {report ms (total), comparisons, allocated bytes, checksum xor} for one run */
    static double[] scenario(Inventory inv, int n, int reports, int addsPerReport, int updatesPerReport) {
        Random rnd = new Random(11);
        List<Integer> ids = new ArrayList<>(); for (int i = 0; i < n + reports * addsPerReport; i++) ids.add(i * 2 + 1);
        Collections.shuffle(ids, rnd);
        for (int i = 0; i < n; i++) inv.add(med(ids.get(i)));
        inv.codeOrderReport();                                 // evening report #0 (both pay a full sort/first build)
        long c0 = inv.comparisons(); double ms = 0; long bytes = 0, sum = 0; int next = n;
        for (int r = 0; r < reports; r++) {
            for (int u = 0; u < updatesPerReport; u++) { Medicine m = inv.get(String.format("MED%07d", ids.get(rnd.nextInt(n)))); m.stock++; }
            for (int a = 0; a < addsPerReport; a++) inv.add(med(ids.get(next++)));
            long a0 = alloc(), t0 = System.nanoTime(); sum ^= inv.codeOrderReport(); ms += (System.nanoTime() - t0) / 1e6; bytes += alloc() - a0;
        }
        return new double[]{ms, inv.comparisons() - c0, bytes, sum};
    }

    public static void main(String[] args) {
        int n = 50_000, reports = 10, updates = 500;
        System.out.printf("N=%,d medicines, %d code-order reports, %d stock updates between reports (updates do not change order)%n", n, reports, updates);
        System.out.println("per-report averages over the " + reports + " reports, best of 3 runs after 2 warm-ups\n");
        System.out.printf("%12s | %10s %12s %12s | %10s %12s %12s | %8s %8s %9s%n", "new meds per", "ORIG ms", "ORIG cmp", "ORIG KB", "NEW ms", "NEW cmp", "NEW KB", "speed-up", "cmp x", "same out");
        System.out.printf("%12s |%n", "report (k)");
        for (int k : new int[]{0, 1, 10, 100, 1_000, 10_000}) {
            double[] bo = null, bn = null;
            for (int rep = 0; rep < 5; rep++) {
                double[] o = scenario(new Original(), n, reports, k, updates), w = scenario(new Redesigned(), n, reports, k, updates);
                if (rep >= 2) { if (bo == null || o[0] < bo[0]) bo = o; if (bn == null || w[0] < bn[0]) bn = w; }
            }
            System.out.printf("%,12d | %10.2f %12.0f %12.0f | %10.2f %12.0f %12.0f | %7.1fx %7.1fx %9s%n", k, bo[0] / reports, bo[1] / reports, bo[2] / reports / 1024,
                    bn[0] / reports, bn[1] / reports, bn[2] / reports / 1024, bo[0] / Math.max(bn[0], 1e-9), bo[1] / Math.max(bn[1], 1), bo[3] == bn[3]);
        }
        System.out.println("\n(speed-up = ORIG ms / NEW ms for the report step only; k=0 means nothing was added so the cached view is reused as-is)");
        System.out.printf("memory trade-off: cached view = %,d references ~ %.1f MB extra at n=%,d (8 bytes/ref compressed oops: 4)%n", n, n * 4 / 1e6, n);
    }
}
