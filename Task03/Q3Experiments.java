import java.util.*;

/**
 * Q3 - equals/hashCode contract experiments.
 *
 * PREDICTIONS (written before running):
 *  T1 same Student OBJECT scanned twice + 1 other student, no hashCode override -> size 2
 *     (same reference => same identity hash code, equals true).
 *  T2 two DIFFERENT objects with the same regNo + 1 other, no hashCode override -> equals says 2 but
 *     HashSet will most likely say 3: identity hash codes differ, so they land in different buckets
 *     and equals() is never even called.
 *  T3 (own) mutate regNo AFTER insertion (hashCode uses regNo, correct class) -> size stays 1 but contains()
 *     fails and a 'duplicate' can be added, because the element sits in the bucket of the OLD hash.
 *  T4 (own) TreeSet with comparator on name only while equals uses regNo -> two different students with
 *     the same name collapse into one entry (size 1).
 *  T5 fixed class (hashCode from regNo) -> T2 data gives size 2, T3 still breaks unless regNo is final.
 */
public class Q3Experiments {
    static class BuggyStudent {                      // exactly the junior developer's class
        String regNo, name;
        BuggyStudent(String r, String n) { regNo = r; name = n; }
        public boolean equals(Object o) { if (!(o instanceof BuggyStudent)) return false; return regNo.equals(((BuggyStudent) o).regNo); }
        public String toString() { return regNo + " " + name; }
    }
    static final class FixedStudent {                // corrected: final fields, hashCode consistent with equals
        private final String regNo, name;
        FixedStudent(String r, String n) { regNo = Objects.requireNonNull(r); name = n; }
        @Override public boolean equals(Object o) { if (this == o) return true; if (!(o instanceof FixedStudent)) return false; return regNo.equals(((FixedStudent) o).regNo); }
        @Override public int hashCode() { return regNo.hashCode(); }
        String reg() { return regNo; }
        String nm() { return name; }
        @Override public String toString() { return regNo + " " + name; }
    }
    static class MutableStudent {                    // correct contract but mutable key field
        String regNo, name;
        MutableStudent(String r, String n) { regNo = r; name = n; }
        @Override public boolean equals(Object o) { return o instanceof MutableStudent m && regNo.equals(m.regNo); }
        @Override public int hashCode() { return regNo.hashCode(); }
        @Override public String toString() { return regNo + " " + name; }
    }

    public static void main(String[] args) {
        System.out.println("== T1: same object scanned twice (buggy class) ==");
        BuggyStudent arun = new BuggyStudent("192311001", "Arun");
        Set<BuggyStudent> s1 = new HashSet<>(); s1.add(arun); s1.add(new BuggyStudent("192311002", "Divya")); s1.add(arun);
        System.out.println("prediction: 2 | observed: " + s1.size() + "   (works only because identity hash code is the same for the same reference)");

        System.out.println("\n== T2: two different objects, same regNo (buggy class) ==");
        int worked = 0, trials = 10_000;
        for (int i = 0; i < trials; i++) {
            Set<BuggyStudent> s = new HashSet<>();
            s.add(new BuggyStudent("192311001", "Arun")); s.add(new BuggyStudent("192311002", "Divya")); s.add(new BuggyStudent("192311001", "Arun"));
            if (s.size() == 2) worked++;
        }
        Set<BuggyStudent> s2 = new HashSet<>();
        s2.add(new BuggyStudent("192311001", "Arun")); s2.add(new BuggyStudent("192311002", "Divya")); s2.add(new BuggyStudent("192311001", "Arun"));
        System.out.println("equals() says unique students = 2 | prediction (HashSet): 3 | observed: " + s2.size() + " -> " + s2);
        System.out.println("over " + trials + " fresh runs the set had the correct size 2 in " + worked + " runs (a hash-bucket coincidence is not enough: HashMap compares the full hash first)");
        System.out.println("a.equals(b) = " + new BuggyStudent("1", "x").equals(new BuggyStudent("1", "y")) + ", but hashCodes differ: "
                + (new BuggyStudent("1", "x").hashCode() != new BuggyStudent("1", "y").hashCode()));

        System.out.println("\n== T3: regNo changed AFTER insertion (hashCode uses regNo) ==");
        MutableStudent m = new MutableStudent("A1", "Kiran");
        Set<MutableStudent> s3 = new HashSet<>(); s3.add(m);
        System.out.println("before change: contains(A1) = " + s3.contains(new MutableStudent("A1", "?")));
        m.regNo = "A9";
        System.out.println("prediction: size 1, contains(m) false, a second A9 can be added");
        System.out.println("after change : size=" + s3.size() + ", contains(m)=" + s3.contains(m) + ", contains(new A9)=" + s3.contains(new MutableStudent("A9", "?"))
                + ", remove(m)=" + s3.remove(m) + ", still iterable: " + s3);
        s3.add(new MutableStudent("A9", "Kiran-again"));
        System.out.println("after adding another A9: size=" + s3.size() + "  (two elements that are equals() to each other -> duplicate in a Set)");

        System.out.println("\n== T4: TreeSet whose comparator is inconsistent with equals ==");
        TreeSet<FixedStudent> ts = new TreeSet<>(Comparator.comparing(FixedStudent::nm)); // compare by NAME only
        FixedStudent p = new FixedStudent("R1", "Anand"), q = new FixedStudent("R2", "Anand");
        ts.add(p); ts.add(q);
        System.out.println("p.equals(q) = " + p.equals(q) + " | prediction TreeSet size 1 | observed: " + ts.size() + " (TreeSet uses compare()==0 as 'same', never equals())");

        System.out.println("\n== T5: corrected class (hashCode from regNo) ==");
        Set<FixedStudent> s5 = new HashSet<>();
        s5.add(new FixedStudent("192311001", "Arun")); s5.add(new FixedStudent("192311002", "Divya")); s5.add(new FixedStudent("192311001", "Arun"));
        System.out.println("prediction 2 | observed " + s5.size());
        // contract check
        FixedStudent a = new FixedStudent("5", "x"), b = new FixedStudent("5", "y");
        System.out.println("contract: equals -> same hashCode ? " + (!a.equals(b) || a.hashCode() == b.hashCode()) + " ; hashCode stable across calls ? " + (a.hashCode() == a.hashCode()));

        System.out.println("\n== Alternatives: TreeSet<Student>(Comparator on regNo) and HashMap<String,Student> ==");
        TreeSet<FixedStudent> byReg = new TreeSet<>(Comparator.comparing(FixedStudent::reg));
        Map<String, FixedStudent> map = new HashMap<>();
        for (FixedStudent st : List.of(new FixedStudent("192311001", "Arun"), new FixedStudent("192311002", "Divya"), new FixedStudent("192311001", "Arun"))) {
            byReg.add(st); map.putIfAbsent(st.reg(), st);
        }
        System.out.println("TreeSet size = " + byReg.size() + ", HashMap size = " + map.size());

        System.out.println("\n== Scan benchmark: 2n scans of n unique students (each id scanned twice) ==");
        System.out.printf("%10s | %-22s | %-22s | %-22s%n", "n", "HashSet (ns/scan, MB)", "TreeSet (ns/scan, MB)", "HashMap (ns/scan, MB)");
        for (int n : new int[]{100_000, 500_000, 1_000_000}) {
            String[] ids = new String[n]; for (int i = 0; i < n; i++) ids[i] = String.format("%09d", 192000000 + i * 7919L % 10_000_000);
            Collections.shuffle(Arrays.asList(ids), new Random(1));
            System.out.printf("%,10d | %-22s | %-22s | %-22s%n", n, bench(0, ids), bench(1, ids), bench(2, ids));
        }
    }

    static String bench(int kind, String[] ids) {
        double best = Double.MAX_VALUE; double mb = 0;
        for (int rep = 0; rep < 4; rep++) {
            System.gc(); long before = used();
            Object keep; long t0 = System.nanoTime();
            if (kind == 0) { Set<FixedStudent> s = new HashSet<>(); for (int pass = 0; pass < 2; pass++) for (String id : ids) s.add(new FixedStudent(id, "n")); keep = s; }
            else if (kind == 1) { TreeSet<FixedStudent> s = new TreeSet<>(Comparator.comparing(FixedStudent::reg)); for (int pass = 0; pass < 2; pass++) for (String id : ids) s.add(new FixedStudent(id, "n")); keep = s; }
            else { Map<String, FixedStudent> s = new HashMap<>(); for (int pass = 0; pass < 2; pass++) for (String id : ids) s.putIfAbsent(id, new FixedStudent(id, "n")); keep = s; }
            long t1 = System.nanoTime(); System.gc(); long after = used();
            if (rep > 0) { best = Math.min(best, (t1 - t0) / (2.0 * ids.length)); mb = (after - before) / 1e6; }
            if (keep.hashCode() == 42) System.out.print("");
        }
        return String.format("%.0f ns, %.1f MB", best, mb);
    }
    static long used() { Runtime r = Runtime.getRuntime(); return r.totalMemory() - r.freeMemory(); }
}
