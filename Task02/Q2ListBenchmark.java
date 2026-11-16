import java.io.PrintWriter;
import java.util.*;
import java.util.function.*;

/**
 * Q2 - Playlist editor: ArrayList vs LinkedList vs ArrayDeque (vs plain array with manual shifting).
 *
 * PREDICTION (before running), K = 1000 operations on a list that already holds N songs:
 *   add at end            : ArrayList O(1) amortised, LinkedList O(1), ArrayDeque O(1)  -> all similar
 *   insert/remove at front: ArrayList O(N) (shift), LinkedList O(1), ArrayDeque O(1)
 *   insert/remove middle  : ArrayList O(N) memmove (fast), LinkedList O(N) pointer chasing (slower per element)
 *   get k-th              : ArrayList O(1), LinkedList O(N), ArrayDeque not supported
 *   iterate               : all O(N); ArrayList fastest (contiguous memory, cache friendly)
 */
public class Q2ListBenchmark {
    static final int[] SIZES = {1_000, 10_000, 100_000};
    static final int K = 1000, WARM = 2, REPS = 5;
    static PrintWriter raw;
    static long sink;

    static <T> double ms(String op, String ds, int n, Supplier<T> setup, Consumer<T> body) {
        for (int w = 0; w < WARM; w++) body.accept(setup.get());
        double total = 0;
        for (int r = 0; r < REPS; r++) {
            T s = setup.get();
            long t0 = System.nanoTime(); body.accept(s); long t1 = System.nanoTime();
            total += (t1 - t0) / 1e6; raw.println(op + "," + ds + "," + n + "," + r + "," + (t1 - t0) / 1e6);
        }
        return total / REPS;
    }
    static List<Integer> filled(List<Integer> l, int n) { for (int i = 0; i < n; i++) l.add(i); return l; }

    // plain array with manual shifting
    static class Arr { int[] a; int size; Arr(int n) { a = new int[n + K + 1]; size = n; for (int i = 0; i < n; i++) a[i] = i; }
        void insertAt(int pos, int v) { for (int i = size; i > pos; i--) a[i] = a[i - 1]; a[pos] = v; size++; }
        void removeAt(int pos) { for (int i = pos; i < size - 1; i++) a[i] = a[i + 1]; size--; } }

    public static void main(String[] args) throws Exception {
        raw = new PrintWriter("q2_raw.csv"); raw.println("operation,structure,n,run,ms");
        Random rnd = new Random(42);
        System.out.println("Time in ms for K=" + K + " operations (iterate: one full pass), average of " + REPS + " runs. n/a = not supported.");
        for (int n : SIZES) {
            int[] idx = new int[K]; for (int i = 0; i < K; i++) idx[i] = rnd.nextInt(n);
            System.out.printf("%n---- playlist size N = %,d ----%n", n);
            System.out.printf("%-26s %12s %12s %12s %12s%n", "operation", "ArrayList", "LinkedList", "ArrayDeque", "int[] manual");
            Supplier<List<Integer>> al = () -> filled(new ArrayList<>(), n), ll = () -> filled(new LinkedList<>(), n);
            Supplier<ArrayDeque<Integer>> ad = () -> { ArrayDeque<Integer> d = new ArrayDeque<>(); for (int i = 0; i < n; i++) d.addLast(i); return d; };
            Supplier<Arr> ar = () -> new Arr(n);

            row("add at end", ms("addEnd", "ArrayList", n, al, l -> { for (int i = 0; i < K; i++) l.add(i); }),
                ms("addEnd", "LinkedList", n, ll, l -> { for (int i = 0; i < K; i++) l.add(i); }),
                ms("addEnd", "ArrayDeque", n, ad, d -> { for (int i = 0; i < K; i++) d.addLast(i); }),
                ms("addEnd", "int[]", n, ar, a -> { for (int i = 0; i < K; i++) a.a[a.size++] = i; }));
            row("insert at front", ms("insFront", "ArrayList", n, al, l -> { for (int i = 0; i < K; i++) l.add(0, i); }),
                ms("insFront", "LinkedList", n, ll, l -> { for (int i = 0; i < K; i++) l.add(0, i); }),
                ms("insFront", "ArrayDeque", n, ad, d -> { for (int i = 0; i < K; i++) d.addFirst(i); }),
                ms("insFront", "int[]", n, ar, a -> { for (int i = 0; i < K; i++) a.insertAt(0, i); }));
            row("remove at front", ms("remFront", "ArrayList", n, al, l -> { for (int i = 0; i < K; i++) l.remove(0); }),
                ms("remFront", "LinkedList", n, ll, l -> { for (int i = 0; i < K; i++) l.remove(0); }),
                ms("remFront", "ArrayDeque", n, ad, d -> { for (int i = 0; i < K; i++) d.pollFirst(); }),
                ms("remFront", "int[]", n, ar, a -> { for (int i = 0; i < K; i++) a.removeAt(0); }));
            row("insert in middle", ms("insMid", "ArrayList", n, al, l -> { for (int i = 0; i < K; i++) l.add(l.size() / 2, i); }),
                ms("insMid", "LinkedList", n, ll, l -> { for (int i = 0; i < K; i++) l.add(l.size() / 2, i); }),
                Double.NaN, ms("insMid", "int[]", n, ar, a -> { for (int i = 0; i < K; i++) a.insertAt(a.size / 2, i); }));
            row("remove from middle", ms("remMid", "ArrayList", n, al, l -> { for (int i = 0; i < K; i++) l.remove(l.size() / 2); }),
                ms("remMid", "LinkedList", n, ll, l -> { for (int i = 0; i < K; i++) l.remove(l.size() / 2); }),
                Double.NaN, ms("remMid", "int[]", n, ar, a -> { for (int i = 0; i < K; i++) a.removeAt(a.size / 2); }));
            row("get k-th (random k)", ms("get", "ArrayList", n, al, l -> { long s = 0; for (int i = 0; i < K; i++) s += l.get(idx[i]); sink += s; }),
                ms("get", "LinkedList", n, ll, l -> { long s = 0; for (int i = 0; i < K; i++) s += l.get(idx[i]); sink += s; }),
                Double.NaN, ms("get", "int[]", n, ar, a -> { long s = 0; for (int i = 0; i < K; i++) s += a.a[idx[i]]; sink += s; }));
            row("iterate whole list", ms("iterate", "ArrayList", n, al, l -> { long s = 0; for (int x : l) s += x; sink += s; }),
                ms("iterate", "LinkedList", n, ll, l -> { long s = 0; for (int x : l) s += x; sink += s; }),
                ms("iterate", "ArrayDeque", n, ad, d -> { long s = 0; for (int x : d) s += x; sink += s; }),
                ms("iterate", "int[]", n, ar, a -> { long s = 0; for (int i = 0; i < a.size; i++) s += a.a[i]; sink += s; }));
        }

        // Changed condition: "Up Next" queue - add at both ends, remove from the front
        System.out.println("\n---- Changed condition: 'Up Next' queue, 200,000 mixed ops (addFirst, addLast, removeFirst) on a queue holding N songs ----");
        System.out.printf("%12s %12s %12s %12s%n", "N", "ArrayList", "LinkedList", "ArrayDeque");
        for (int n : new int[]{1_000, 10_000, 100_000}) {
            int ops = 200_000;
            double a = ms("upNext", "ArrayList", n, () -> filled(new ArrayList<>(), n), l -> { for (int i = 0; i < ops; i++) { int m = i % 3; if (m == 0) l.add(0, i); else if (m == 1) l.add(i); else l.remove(0); } });
            double b = ms("upNext", "LinkedList", n, () -> filled(new LinkedList<>(), n), l -> { for (int i = 0; i < ops; i++) { int m = i % 3; if (m == 0) l.add(0, i); else if (m == 1) l.add(i); else l.remove(0); } });
            double c = ms("upNext", "ArrayDeque", n, () -> { ArrayDeque<Integer> d = new ArrayDeque<>(); for (int i = 0; i < n; i++) d.addLast(i); return d; },
                    d -> { for (int i = 0; i < ops; i++) { int m = i % 3; if (m == 0) d.addFirst(i); else if (m == 1) d.addLast(i); else d.pollFirst(); } });
            System.out.printf("%,12d %12.2f %12.2f %12.2f%n", n, a, b, c);
        }
        raw.close(); System.out.println("sink=" + sink);
    }
    static void row(String op, double a, double l, double d, double arr) {
        System.out.printf("%-26s %12s %12s %12s %12s%n", op, f(a), f(l), f(d), f(arr));
    }
    static String f(double x) { return Double.isNaN(x) ? "n/a" : String.format("%.3f", x); }
}
