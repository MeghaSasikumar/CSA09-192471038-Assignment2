import java.util.*;

/**
 * Q6 - Emergency triage. Designed WITHOUT starting from the supplied code (Triage.java is the Complete-the-Code part).
 *
 * PREDICTIONS (before running):
 *  - PriorityQueue (binary heap): add O(log n), poll O(log n), peek O(1); n adds ~ n log n total.
 *  - Printing the PriorityQueue shows the HEAP ARRAY order (parent <= children only), not sorted order.
 *  - sorted LinkedList: admit O(n) (walk to the position), serve O(1).
 *  - five FIFO queues (one per severity): admit O(1), serve O(1) (check at most 5 queues).
 *  - Strict priority starves severity-5 patients when severe patients keep arriving faster than they are served.
 *  - Fair rule 'deadline key' = arrival + waitBudget[severity]: still O(log n), and every patient is served after a bounded wait.
 */
public class Q6Study {
    static final Comparator<Patient> STRICT = Comparator.comparingInt((Patient p) -> p.severity).thenComparingLong(p -> p.arrival);

    // ---------- design 1: PriorityQueue with the strict rule
    // ---------- design 2: sorted LinkedList
    static class SortedListTriage {
        final LinkedList<Patient> list = new LinkedList<>(); long c;
        void admit(String n, int s) { Patient p = new Patient(n, s, c++); ListIterator<Patient> it = list.listIterator();
            while (it.hasNext()) { if (STRICT.compare(it.next(), p) > 0) { it.previous(); break; } } it.add(p); }
        Patient next() { return list.pollFirst(); }
    }
    // ---------- design 3: array of 5 FIFO queues
    static class BucketTriage {
        @SuppressWarnings("unchecked") final ArrayDeque<Patient>[] q = new ArrayDeque[5]; long c;
        BucketTriage() { for (int i = 0; i < 5; i++) q[i] = new ArrayDeque<>(); }
        void admit(String n, int s) { q[s - 1].addLast(new Patient(n, s, c++)); }
        Patient next() { for (ArrayDeque<Patient> d : q) if (!d.isEmpty()) return d.pollFirst(); return null; }
    }
    // ---------- fair design: deadline key = arrival + waitBudget[severity]
    static final long[] BUDGET = {0, 20, 50, 100, 200};           // 'ticks' a patient of that severity may be overtaken
    static class Entry { final Patient p; final long deadline; Entry(Patient p, long d) { this.p = p; deadline = d; } }
    static class AgingTriage {
        final PriorityQueue<Entry> pq = new PriorityQueue<>(Comparator.comparingLong((Entry e) -> e.deadline).thenComparingInt(e -> e.p.severity).thenComparingLong(e -> e.p.arrival));
        long clock;                                   // advanced by the caller (one tick per arrival time unit)
        void admit(String n, int s) { Patient p = new Patient(n, s, clock); pq.add(new Entry(p, clock + BUDGET[s - 1])); }
        Patient next() { Entry e = pq.poll(); return e == null ? null : e.p; }
    }

    public static void main(String[] args) {
        // ---- hand-checkable instance (8 patients)
        String[] names = {"Ana", "Bala", "Chitra", "Dev", "Esha", "Farid", "Gita", "Hari"};
        int[] sev = {3, 1, 3, 5, 1, 2, 3, 1};
        PriorityQueue<Patient> pq = new PriorityQueue<>(STRICT); long arrival = 0;
        for (int i = 0; i < 8; i++) pq.add(new Patient(names[i], sev[i], arrival++));
        System.out.println("Arrival order : Ana(S3) Bala(S1) Chitra(S3) Dev(S5) Esha(S1) Farid(S2) Gita(S3) Hari(S1)");
        System.out.println("Expected by hand (S1 by arrival, then S2, S3 by arrival, S5): Bala Esha Hari Farid Ana Chitra Gita Dev");
        System.out.println("System.out.println(pq) shows the HEAP ARRAY, not treatment order: " + pq);
        StringBuilder sb = new StringBuilder(); Patient p; while ((p = pq.poll()) != null) sb.append(p.name).append(' ');
        String got = sb.toString().trim();
        System.out.println("poll() order observed                                        : " + got);
        System.out.println("matches hand-computed order: " + got.equals("Bala Esha Hari Farid Ana Chitra Gita Dev"));

        // ---- global JIT warm-up so that the smallest size is not penalised
        for (int w = 0; w < 5; w++) { PriorityQueue<Patient> wq = new PriorityQueue<>(STRICT); for (int i = 0; i < 200_000; i++) wq.add(new Patient("w", 1 + i % 5, i)); while (wq.poll() != null) ; }
        // ---- growth
        System.out.println("\nGrowth of PriorityQueue (average of 5 runs after 2 warm-ups): ns per admit and ns per serve");
        System.out.printf("%12s %14s %14s %16s%n", "n", "ns/admit", "ns/serve", "serve ratio vs prev");
        double prev = 0; Random rnd = new Random(3);
        for (int n : new int[]{10_000, 100_000, 1_000_000}) {
            int[] sv = new int[n]; for (int i = 0; i < n; i++) sv[i] = 1 + rnd.nextInt(5);
            double add = 0, srv = 0;
            for (int rep = 0; rep < 7; rep++) {
                PriorityQueue<Patient> q = new PriorityQueue<>(STRICT);
                long t0 = System.nanoTime(); for (int i = 0; i < n; i++) q.add(new Patient("p", sv[i], i)); long t1 = System.nanoTime();
                long z = 0; while (!q.isEmpty()) z += q.poll().arrival; long t2 = System.nanoTime();
                if (rep >= 2) { add += (t1 - t0) / (double) n / 5; srv += (t2 - t1) / (double) n / 5; } if (z == 7) System.out.print("");
            }
            System.out.printf("%,12d %14.0f %14.0f %16s%n", n, add, srv, prev == 0 ? "-" : String.format("%.2f", srv / prev)); prev = srv;
        }
        System.out.println("(ns/serve should grow ~log n: x10 in n adds only a small constant, not x10)");

        // ---- alternatives
        System.out.println("\nAlternatives: total time (ms) to admit n random-severity patients then serve all, single run after warm-up");
        System.out.printf("%10s %16s %16s %16s%n", "n", "PriorityQueue", "sorted LinkedList", "5 FIFO queues");
        for (int n : new int[]{2_000, 10_000, 40_000}) {
            int[] sv = new int[n]; for (int i = 0; i < n; i++) sv[i] = 1 + rnd.nextInt(5);
            double a = 0, b = 0, c = 0;
            for (int rep = 0; rep < 3; rep++) {
                long t0 = System.nanoTime(); PriorityQueue<Patient> q = new PriorityQueue<>(STRICT); for (int i = 0; i < n; i++) q.add(new Patient("p", sv[i], i)); while (q.poll() != null) ;
                long t1 = System.nanoTime(); SortedListTriage s = new SortedListTriage(); for (int i = 0; i < n; i++) s.admit("p", sv[i]); while (s.next() != null) ;
                long t2 = System.nanoTime(); BucketTriage bt = new BucketTriage(); for (int i = 0; i < n; i++) bt.admit("p", sv[i]); while (bt.next() != null) ;
                long t3 = System.nanoTime(); if (rep == 2) { a = (t1 - t0) / 1e6; b = (t2 - t1) / 1e6; c = (t3 - t2) / 1e6; }
            }
            System.out.printf("%,10d %16.2f %16.2f %16.2f%n", n, a, b, c);
        }

        // ---- starvation: each tick 2 critical (S1) patients arrive and 1 patient is served
        System.out.println("\nStarvation simulation: tick 0 a severity-5 patient (Zed) arrives; then every tick 2 new severity-1 patients arrive and the doctor serves 1 patient.");
        for (String mode : new String[]{"strict", "aging"}) {
            long served = -1; int ticks = 3000;
            if (mode.equals("strict")) {
                PriorityQueue<Patient> q = new PriorityQueue<>(STRICT); long clock = 0; q.add(new Patient("Zed", 5, clock));
                for (int t = 1; t <= ticks && served < 0; t++) { clock = t; q.add(new Patient("c" + t + "a", 1, clock)); q.add(new Patient("c" + t + "b", 1, clock)); Patient x = q.poll(); if (x.name.equals("Zed")) served = t; }
            } else {
                AgingTriage a = new AgingTriage(); a.admit("Zed", 5);
                for (int t = 1; t <= ticks && served < 0; t++) { a.clock = t; a.admit("c" + t + "a", 1); a.admit("c" + t + "b", 1); Patient x = a.next(); if (x.name.equals("Zed")) served = t; }
            }
            System.out.println(String.format("  %-6s: Zed %s", mode, served < 0 ? "STILL WAITING after " + ticks + " ticks (starved)" : "served after waiting " + served + " ticks"));
        }
        System.out.println("  aging also keeps severity order when no one has waited long: next 3 of a fresh AgingTriage ->");
        AgingTriage a = new AgingTriage(); a.admit("m", 5); a.admit("k", 1); a.admit("l", 3); System.out.println("  " + a.next() + " " + a.next() + " " + a.next());

        // ---- deployment: 5,000 patients/day
        int day = 5_000; PriorityQueue<Patient> q = new PriorityQueue<>(STRICT);
        long t0 = System.nanoTime(); for (int i = 0; i < day; i++) q.add(new Patient("p", 1 + rnd.nextInt(5), i)); while (q.poll() != null) ; long t1 = System.nanoTime();
        System.out.printf("%nDeployment check: admitting and serving %,d patients took %.3f ms in total (%.0f us per day-total, i.e. %.2f us per patient)%n", day, (t1 - t0) / 1e6, (t1 - t0) / 1e3, (t1 - t0) / 1e3 / day);
    }
}
