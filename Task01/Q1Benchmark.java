import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.util.*;

/**
 * Q1 - Railway e-ticket generation: String (+) vs StringBuilder vs StringBuffer
 * (plus the alternatives String.join, StringJoiner, String.format).
 *
 * PREDICTION (written before running):
 *  - Short ticket (8 fields): all approaches within a small constant factor.
 *  - 50 fields x 10,000 tickets: '+' clearly slower and allocates far more; SB ~ SBuf (uncontended lock is cheap).
 *  - Large ticket (thousands of fields): '+' grows ~quadratically (doubling n -> ~4x time),
 *    StringBuilder/StringBuffer grow ~linearly (doubling n -> ~2x time).
 *  Reason: String is immutable, so every s = s + x allocates a new String and copies ALL chars so far.
 */
public class Q1Benchmark {
    static final com.sun.management.ThreadMXBean TMX =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    static long alloc() { return TMX.getThreadAllocatedBytes(Thread.currentThread().getId()); }

    interface Builder { String build(String[] f); }

    static String[] fields(int n) {
        String[] f = new String[n];
        for (int i = 0; i < n; i++) f[i] = "field" + i + "=VALUE" + (i * 7919 % 1000);
        return f;
    }
    static String viaPlus(String[] f) { String s = ""; for (String x : f) s = s + x + "|"; return s; }
    static String viaSB(String[] f) { StringBuilder b = new StringBuilder(); for (String x : f) b.append(x).append('|'); return b.toString(); }
    static String viaSBuf(String[] f) { StringBuffer b = new StringBuffer(); for (String x : f) b.append(x).append('|'); return b.toString(); }
    static String viaJoin(String[] f) { return String.join("|", f) + "|"; }
    static String viaJoiner(String[] f) { StringJoiner j = new StringJoiner("|", "", "|"); for (String x : f) j.add(x); return j.toString(); }
    static String viaFormat(String[] f) { return String.format("%s|".repeat(f.length), (Object[]) f); }

    static long sink = 0;
    static PrintWriter raw;

    /** returns {avgMs, stdevMs, bytesPerTicket} over reps measured runs after warm-up */
    static double[] run(String workload, String name, Builder b, String[] f, int tickets, int warm, int reps) {
        for (int w = 0; w < warm; w++) for (int t = 0; t < tickets; t++) sink += b.build(f).length();
        double[] ms = new double[reps]; long bytes = 0;
        for (int r = 0; r < reps; r++) {
            long a0 = alloc(), t0 = System.nanoTime();
            for (int t = 0; t < tickets; t++) sink += b.build(f).length();
            long t1 = System.nanoTime(), a1 = alloc();
            ms[r] = (t1 - t0) / 1e6; bytes = (a1 - a0) / tickets;
            raw.println(workload + "," + name + "," + r + "," + ms[r] + "," + bytes);
        }
        double avg = Arrays.stream(ms).average().orElse(0);
        double sd = Math.sqrt(Arrays.stream(ms).map(x -> (x - avg) * (x - avg)).sum() / reps);
        return new double[]{avg, sd, bytes};
    }

    static void workload(String label, int nFields, int tickets, int warm, int reps) {
        String[] f = fields(nFields);
        System.out.printf("%n== %s: %d fields x %d tickets (avg of %d runs after %d warm-ups) ==%n", label, nFields, tickets, reps, warm);
        System.out.printf("%-14s %12s %10s %14s %16s%n", "approach", "avg ms", "stdev", "us/ticket", "bytes/ticket");
        Object[][] approaches = {
            {"+ operator", (Builder) Q1Benchmark::viaPlus}, {"StringBuilder", (Builder) Q1Benchmark::viaSB},
            {"StringBuffer", (Builder) Q1Benchmark::viaSBuf}, {"String.join", (Builder) Q1Benchmark::viaJoin},
            {"StringJoiner", (Builder) Q1Benchmark::viaJoiner}, {"String.format", (Builder) Q1Benchmark::viaFormat}};
        for (Object[] a : approaches) {
            double[] r = run(label, (String) a[0], (Builder) a[1], f, tickets, warm, reps);
            System.out.printf("%-14s %12.2f %10.2f %14.3f %16.0f%n", a[0], r[0], r[1], r[0] * 1000 / tickets, r[2]);
        }
    }

    /** number of internal array growths StringBuilder performs while building one ticket */
    static int sbResizes(int n) {
        String[] f = fields(n); StringBuilder b = new StringBuilder(); int cap = b.capacity(), c = 0;
        for (String x : f) { b.append(x).append('|'); if (b.capacity() != cap) { c++; cap = b.capacity(); } }
        return c;
    }

    public static void main(String[] args) throws Exception {
        raw = new PrintWriter("q1_raw.csv"); raw.println("workload,approach,run,ms,bytes_per_ticket");
        String[] f = fields(50);
        String ref = viaPlus(f);
        boolean same = ref.equals(viaSB(f)) && ref.equals(viaSBuf(f)) && ref.equals(viaJoin(f)) && ref.equals(viaJoiner(f)) && ref.equals(viaFormat(f));
        System.out.println("All six approaches produce identical text: " + same);

        workload("W1-short", 8, 200_000, 3, 5);
        workload("W2-50fields", 50, 10_000, 3, 5);
        workload("W3-own-large", 2_000, 50, 2, 5);

        System.out.println("\n== Scaling of ONE ticket with n fields (us per ticket) ==");
        System.out.printf("%8s %14s %8s %14s %8s %12s%n", "n", "plus us", "x", "SB us", "x", "plus/SB");
        double pp = 0, ps = 0;
        for (int n = 250; n <= 8000; n *= 2) {
            String[] g = fields(n); int tk = Math.max(2, 400_000 / n);
            double[] a = run("scale-" + n, "+ operator", Q1Benchmark::viaPlus, g, tk, 2, 5);
            double[] b = run("scale-" + n, "StringBuilder", Q1Benchmark::viaSB, g, tk, 2, 5);
            double ua = a[0] * 1000 / tk, ub = b[0] * 1000 / tk;
            System.out.printf("%8d %14.1f %8s %14.1f %8s %12.1f%n", n, ua, pp == 0 ? "-" : String.format("%.2f", ua / pp), ub, ps == 0 ? "-" : String.format("%.2f", ub / ps), ua / ub);
            pp = ua; ps = ub;
        }
        System.out.println("(x = ratio to previous row; ~4 means quadratic, ~2 means linear)");

        System.out.println("\n== Estimated intermediate objects per ticket ==");
        for (int n : new int[]{8, 50, 2000})
            System.out.printf("n=%5d: '+' creates ~%d intermediate Strings (each with a byte[] => ~%d objects); StringBuilder: %d internal array growths + 1 final String%n",
                    n, n - 1, 2 * (n - 1), sbResizes(n));
        raw.close();
        System.out.println("sink=" + sink);
    }
}
