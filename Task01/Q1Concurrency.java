import java.util.*;
import java.util.concurrent.*;

/** Q1 design decision: shared booking log appended by several threads. */
public class Q1Concurrency {
    static final int THREADS = 8, LINES = 50_000;
    static final String LINE = "PNR12345|booked\n";

    interface Appender { void append(String s); long length(); Appender fresh(); }
    static Appender sb() { StringBuilder b = new StringBuilder();
        return new Appender() { public void append(String s) { b.append(s); } public long length() { return b.length(); } public Appender fresh() { return sb(); } }; }
    static Appender sbuf() { StringBuffer b = new StringBuffer();
        return new Appender() { public void append(String s) { b.append(s); } public long length() { return b.length(); } public Appender fresh() { return sbuf(); } }; }
    static Appender perThread() {
        ConcurrentLinkedQueue<StringBuilder> all = new ConcurrentLinkedQueue<>();
        ThreadLocal<StringBuilder> tl = ThreadLocal.withInitial(() -> { StringBuilder b = new StringBuilder(); all.add(b); return b; });
        return new Appender() { public void append(String s) { tl.get().append(s); }
            public long length() { long n = 0; for (StringBuilder b : all) n += b.length(); return n; } public Appender fresh() { return perThread(); } };
    }

    static void test(String name, Appender shared) throws Exception {
        int ok = 0, exceptions = 0; long worstMissing = 0;
        for (int round = 0; round < 5; round++) {
            Appender log = shared.fresh();
            ExecutorService pool = Executors.newFixedThreadPool(THREADS);
            List<Future<?>> fs = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) fs.add(pool.submit(() -> { for (int i = 0; i < LINES; i++) log.append(LINE); }));
            boolean thrown = false;
            for (Future<?> f : fs) try { f.get(); } catch (ExecutionException e) { thrown = true; }
            pool.shutdown();
            long missing = (long) THREADS * LINES * LINE.length() - log.length();
            if (thrown) exceptions++;
            if (!thrown && missing == 0) ok++;
            worstMissing = Math.max(worstMissing, missing);
        }
        System.out.printf("%-28s correct rounds: %d/5, rounds with exception: %d, worst chars lost: %d%n", name, ok, exceptions, worstMissing);
    }

    public static void main(String[] a) throws Exception {
        System.out.println("8 threads x 50,000 appends to ONE shared log, 5 rounds each");
        test("shared StringBuilder", sb());
        test("shared StringBuffer", sbuf());
        test("per-thread StringBuilder", perThread());
        int N = 3_000_000; StringBuilder b = new StringBuilder(); StringBuffer bf = new StringBuffer();
        for (int w = 0; w < 3; w++) { b.setLength(0); bf.setLength(0); for (int i = 0; i < N; i++) { b.append("abc"); bf.append("abc"); } }
        long t0 = System.nanoTime(); b.setLength(0); for (int i = 0; i < N; i++) b.append("abc"); long t1 = System.nanoTime();
        bf.setLength(0); for (int i = 0; i < N; i++) bf.append("abc"); long t2 = System.nanoTime();
        System.out.printf("single thread, %d appends: StringBuilder %.1f ms, StringBuffer %.1f ms%n", N, (t1 - t0) / 1e6, (t2 - t1) / 1e6);
    }
}
