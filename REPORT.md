# Assignment 2 (CO2) – Report

> Measurements were taken on one machine with OpenJDK 21 (JIT warm-up, 3–7 repeated runs, averages and standard deviations; raw data in `q*_raw.csv`).
> Absolute numbers will differ on your computer – **re-run `./run_all.sh` and replace the tables with your own numbers**; the shapes (ratios, growth) are what matter.
> Where numbers were noisy this is stated explicitly.

---
## Q1 – String, StringBuilder or StringBuffer? (Task01)

### Prediction and reasoning (made before running)
`String` is immutable. In `s = s + field` every iteration allocates a new String and copies *all* characters built so far. For `n` fields of average length `L` the characters copied are `L·(1+2+…+n) = L·n(n+1)/2` → **O(n²)**, and ~n intermediate Strings (each with its own `byte[]`) become garbage.
`StringBuilder` appends into one growing array (capacity doubles) → amortised **O(n)**. `StringBuffer` is the same but every method is `synchronized`.
Predicted: short ticket – small differences; 50 fields × 10,000 tickets – `+` clearly worse; large ticket – `+` quadratic (×4 per doubling), builders linear (×2).

### Workloads (avg of 5 runs, 2–3 warm-ups)
| Workload | `+` | StringBuilder | StringBuffer | String.join | StringJoiner | String.format |
|---|---|---|---|---|---|---|
| W1: 8 fields × 200,000 tickets (ms) | 27.7 | 39.7 | 27.1 | 13.4 | 11.9 | 67.5 |
| bytes allocated / ticket | 896 | 528 | 536 | 384 | 216 | 1,304 |
| W2: 50 fields × 10,000 tickets (ms) | 12.6 | 4.0 | 4.6 | 3.6 | 5.6 | 16.0 |
| bytes allocated / ticket | 23,160 | 3,296 | 3,304 | 1,960 | 1,456 | 7,712 |
| **W3 (own)**: 2,000 fields × 50 tickets (ms) | 129.1 | 0.79 | 0.90 | 0.82 | 1.10 | 3.49 |
| bytes allocated / ticket | 36,165,688 | 110,640 | 110,648 | 81,440 | 53,248 | 278,944 |

Scaling of one ticket (µs/ticket): n = 250 → 33.9 (+) vs 3.7 (SB); 8000 → 49,923 vs 76. Time ratio per doubling of n: `+` **4.31, 4.05, 4.68, 4.20, 4.29** (≈4 ⇒ quadratic); StringBuilder 1.86, 1.15, 1.97, 3.41, 1.44 (noisy, mean ≈2 ⇒ linear).
Estimated intermediate objects: `+` creates n−1 Strings (≈2(n−1) objects incl. arrays) – 7 / 49 / 1,999 for n = 8 / 50 / 2,000; StringBuilder performs only 3 / 6 / 11 internal array growths + 1 final String.

### Prediction vs observation
* Confirmed: quadratic growth of `+` and linear growth of builders; allocation in W3 is 327× higher for `+`.
* W1 (short ticket): differences are small and **noisy** (stdev up to 15 ms on 27 ms). The JIT optimises a short `+` chain well, so "negligible for short tickets" is supported; StringBuilder was not faster here because its default capacity (16) forces several array growths for a 200-character ticket (3 growths measured).
* Surprise: `String.format` was the slowest in W1/W2 (format parsing), and `String.join`/`StringJoiner` were as fast as or faster than StringBuilder.

### Design decision (multithreaded booking server)
Experiment (Q1Concurrency): 8 threads × 50,000 appends to one shared object, 5 rounds.
| | correct rounds | rounds with exception | worst chars lost |
|---|---|---|---|
| shared StringBuilder | 1 / 5 | 4 | 1,600,000 |
| shared StringBuffer | 5 / 5 | 0 | 0 |
| per-thread StringBuilder merged at the end | 5 / 5 | 0 | 0 |

Single thread, 3,000,000 appends: StringBuilder 16.6 ms vs StringBuffer 62.9 ms (≈3.8× – the cost of synchronisation).
* **Per-ticket text → StringBuilder** (local variable, never shared: no lock cost, no thread-safety need).
* **Shared booking log → a thread-safe design.** `StringBuffer` is the minimal correct drop-in (each `append` is atomic), but it only makes *single calls* atomic: build the whole log line in a local StringBuilder and add it with **one** `append`, otherwise parts of different lines interleave. A better design avoids contention altogether (per-thread builders, or a logging queue/`ConcurrentLinkedQueue`).

### Alternative concept and complexity
| Approach | Time for n fields | Extra space |
|---|---|---|
| repeated `+` | O(n²) chars copied (`L·n(n+1)/2`) | O(n²) total garbage allocated (36 MB per 2,000-field ticket measured); 2 live copies at once |
| StringBuilder / StringBuffer | O(n) amortised (doubling: total copy ≤ 2·final length) | O(nL) live + up to 2× slack capacity; ~log n array growths |
| `String.join` | O(n): one pass to size, one to copy | one result array (+ array of parts) |
| StringJoiner | O(n) | internal array of parts + result |
| `String.format` | O(n) but largest constant (format parsing) | most temporary objects (7.7 KB vs 3.3 KB at n = 50) |

---
## Q2 – When is ArrayList appropriate? (Task02)

### Prediction
ArrayList: add-end O(1) amortised, get O(1), insert/remove front or middle O(N) (shift), iterate O(N) fastest (contiguous). LinkedList: front O(1), middle/get O(N) with pointer chasing. ArrayDeque: both ends O(1), no index access.

### Evidence – ms for K = 1,000 operations (average of 5 runs)
| N = 100,000 | ArrayList | LinkedList | ArrayDeque | int[] manual |
|---|---|---|---|---|
| add at end | 0.028 | 0.031 | 0.023 | 0.014 |
| insert at front | 7.763 | 0.032 | 0.024 | 18.989 |
| remove at front | 7.754 | 0.025 | 0.016 | 17.890 |
| insert in middle | 3.888 | 135.699 | n/a | 14.216 |
| remove from middle | 4.342 | 116.963 | n/a | 10.592 |
| get k-th | 0.094 | 70.806 | n/a | 0.017 |
| iterate (one pass) | 0.900 | 1.598 | 1.732 | 0.207 |

N = 10,000: front insert 0.317 (ArrayList) vs 0.025 (LinkedList); middle insert 0.162 vs 7.407; get 0.029 vs 3.584. N = 1,000: front insert 0.541 vs 0.117, middle insert 0.069 vs 1.607 (small-N numbers are close to timer/JIT noise).
ArrayList front-insert cost grows ≈ linearly with N (0.32 → 7.76 ms for ×10 size ≈ ×24, cache effects included); LinkedList get grows ≈ linearly too (3.6 → 70.8 ms).

### Operating boundary
ArrayList is a sound choice for a playlist: users append, jump to the k-th song and iterate, and edits are interactive (one insert at the front of a 100,000-song list costs ≈ 8 µs, invisible to a human). Even the middle insert is 35× **faster** in ArrayList than LinkedList, because shifting is a fast `memmove` whereas LinkedList must walk the nodes. **LinkedList never won anything that matters** (only front operations, where ArrayDeque is better still).
I would stop recommending ArrayList when operations at the *head* happen at machine rates on long lists, i.e. a queue/deque usage pattern.

### Changed condition – "Up Next" queue (200,000 mixed addFirst/addLast/removeFirst)
| N | ArrayList | LinkedList | ArrayDeque |
|---|---|---|---|
| 1,000 | 318 ms | 7.9 ms | 2.6 ms |
| 10,000 | 380 ms | 3.4 ms | 1.8 ms |
| 100,000 | 1,412 ms | 11.4 ms | 2.1 ms |
ArrayDeque is the right structure (O(1) at both ends, circular array, no node objects). Keep the *playlist* in an ArrayList and the *Up Next* in an ArrayDeque.

### Complexity table
| Operation | ArrayList | LinkedList | ArrayDeque | plain array, manual shift |
|---|---|---|---|---|
| add at end | O(1) amortised | O(1) | O(1) amortised | O(1) (O(N) if full → copy) |
| insert at front | O(N) | O(1) | O(1) | O(N) |
| insert in middle | O(N) (memmove) | O(N) (find node) | not supported | O(N) |
| get k-th | O(1) | O(N) | not supported | O(1) |
| iterate | O(N) | O(N) | O(N) | O(N) |
| space per element | 1 array slot (4–8 B), up to 50 % slack | one node (≈24 B: header + prev/next/item) + the element | 1 slot, up to 2× slack (power-of-two array) | 1 slot, fixed size |

The manual array has the same complexity as ArrayList but is ≈2.4× slower for shifting in my run (hand-written loop vs `System.arraycopy`) and cannot grow automatically.

---
## Q3 – The hashCode–equals contract (Task03)

### Predictions and results (buggy class has `equals` on `regNo`, no `hashCode`)
| Test | Prediction (and why) | Observed |
|---|---|---|
| T1 same *object* scanned twice + another student | size 2 – same reference ⇒ same identity hash, `equals` true | **2** |
| T2 two *different objects* with the same regNo + another | `equals` says 2 unique, but HashSet size **3** – different identity hashes ⇒ different buckets, `equals` never called | **3**; over 10,000 fresh runs size 2 occurred **0** times |
| T3 (own) change `regNo` after insertion (hashCode uses regNo) | size 1 but `contains`/`remove` fail; a second "A9" can be added | size 1, `contains(m)=false`, `remove(m)=false`, then **size 2** with two equal elements |
| T4 (own) TreeSet with comparator on *name* only | two students with different regNo but same name collapse: size 1 | **1** (`equals` = false, but `compare` = 0) |
| T5 fixed class (hashCode from regNo) | 2 | **2** |

### Complete the Code (Attendance)
* Before TODO 1: **`Unique students: 3`**. After TODO 1 (`return regNo.hashCode();`): **`Unique students: 2`** – matches expected output.

### Explanation
* `HashSet.add(x)` computes `x.hashCode()`, picks a bucket from it, and compares only elements whose **stored hash equals** the new hash and then calls `equals`. Without an overridden `hashCode`, `Object.hashCode()` is identity-based, so equal students get different hashes and `equals` is never consulted → duplicates.
* Contract: if `a.equals(b)` then `a.hashCode() == b.hashCode()`; the hash must be stable while the object is in a hash collection and should be computed from the same fields as `equals`.
* Changing a hashed field after insertion leaves the element in the bucket of its *old* hash: lookups use the new hash and miss it (T3) – so key fields must be `final`/immutable.
* Fix: `regNo` and `name` final, `hashCode` = `regNo.hashCode()` (or `Objects.hash(regNo)`), verified by T2 data (size 2), a contract check (equal objects ⇒ equal hash) and the Attendance output.

### Alternative concept and complexity (n unique students, 2n scans; ns per scan / retained MB)
| n | HashSet | TreeSet (Comparator on regNo) | HashMap<String,Student> |
|---|---|---|---|
| 100,000 | 243 ns, 6.6 MB | 817 ns, 6.4 MB | 270 ns, 6.6 MB |
| 500,000 | 162 ns, 32.2 MB | 1,896 ns, 32.0 MB | 131 ns, 32.2 MB |
| 1,000,000 | 233 ns, 64.4 MB | 2,440 ns, 64.0 MB | 163 ns, 64.4 MB |
HashSet/HashMap: O(1) average per scan, O(n) space (table + node per entry). TreeSet: O(log n) per scan (≈3–12× slower here, growing with n, because every comparison is a String compare plus a cache-missing tree walk), O(n) space (one node with 3 references per entry). HashMap keyed by regNo is the most robust because the key is a `String` (well-behaved `hashCode`/`equals`) and the student can carry extra data. The "MB" column is measured with `System.gc()` and is approximate; it is dominated by the Student/String objects themselves, so structure overhead differences are within noise.

---
## Q4 – Map for the pharmacy inventory (Task04)

### Design reasoning (before implementation)
| | put | get | iteration | code-order report | insertion-order report |
|---|---|---|---|---|---|
| HashMap | O(1) avg | O(1) avg | O(n + capacity), arbitrary order | needs sort: O(n log n) + extra list | impossible without side list |
| LinkedHashMap | O(1) avg | O(1) avg | O(n), insertion order | needs sort O(n log n) | **natural** O(n) |
| TreeMap | O(log n) | O(log n) | O(n), sorted order | **natural** O(n) | needs side list of codes |
| Hashtable | O(1) avg, synchronised | same | arbitrary | needs sort | impossible |

### Evidence (avg of 5 runs; 100,000 lookups; order columns compare the map's own iteration order to the requirement)
| N | structure | put-all ms | 100k gets ms (ns/get) | code-order report ms | order = code? | order = added? |
|---|---|---|---|---|---|---|
| 1,000 | HashMap | 0.11 | 3.30 (33) | 2.52 | no | no |
| | LinkedHashMap | 1.05 | 4.04 (40) | 1.50 | no | **yes** |
| | TreeMap | 0.36 | 45.46 (455) | **0.15** | **yes** | no |
| | Hashtable | 0.21 | 4.67 (47) | 0.99 | no | no |
| 20,000 | HashMap | 2.91 | 5.00 (50) | 13.52 | no | no |
| | LinkedHashMap | 4.49 | 5.41 (54) | 14.88 | no | **yes** |
| | TreeMap | 11.86 | 32.87 (329) | **3.76** | **yes** | no |
| | Hashtable | 1.82 | 2.03 (20) | 9.86 | no | no |
| 200,000 | HashMap | 15.09 | 8.30 (83) | 93.40 | no | no |
| | LinkedHashMap | 10.63 | 5.75 (57) | 133.79 | no | **yes** |
| | TreeMap | 194.35 | 93.90 (939) | **9.70** | **yes** | no |
| | Hashtable | 13.03 | 6.32 (63) | 73.74 | no | no |

Extra work when the "wrong" map is used: HashMap/LinkedHashMap need copy + sort for the code-order report (≈10–14× slower than TreeMap at N = 200,000, and an extra n-element list); TreeMap needs an extra `ArrayList` of codes for insertion order (199 ms to walk 200,000 codes through the TreeMap vs 1.2 ms for LinkedHashMap's native iteration).
Sorted array + `binarySearch` (N = 200,000): build/sort 139 ms; lookup ≈ 1,203 ns (log₂ n steps, includes a temporary key object); insert O(n) because of shifting; code-order report O(n) (already sorted); smallest memory (one reference per element). Complexities: lookup O(log n), insertion O(n), report O(n), space O(n). Compared with HashMap (O(1) lookup, O(n log n) report) and TreeMap (O(log n) lookup/insert, O(n) report) it is the cheapest in memory but only suitable for mostly-static data.

### Scalability inquiry
**Acceptability criterion:** one lookup ≤ 1 µs on average (100,000 lookups ≈ 0.1 s, imperceptible; pharmacists do hundreds per day).
Measured: HashMap/LinkedHashMap 33–83 ns (pass at every size); TreeMap 329–939 ns (passes only narrowly at 200,000; it grows with log n and with cache misses).
**Decision: LinkedHashMap** – the lookup is O(1) and the insertion-order report is free; the remaining cost is the evening code-order report (copy + sort, ≈134 ms at 200,000 medicines), which comes from re-sorting an unordered structure every time – this is exactly what Q7 improves. (If code order were the *main* report and lookups were not critical, TreeMap would be the single-structure choice.)
**Why not Hashtable:** it is a legacy class with every method synchronised (a lock on every operation even in single-threaded code), no `null` keys/values, and its iteration order is no more useful than HashMap's; use `HashMap` (single thread) or `ConcurrentHashMap` (concurrent).

---
## Q5 – Generic ranking component (Task05)

### Design derived from the principle
A ranking comparator must be (1) antisymmetric `sgn(cmp(a,b)) = −sgn(cmp(b,a))`, (2) transitive, (3) return 0 only for items that are interchangeable for ranking.
* **Comparator, not Comparable:** `Team` has no single natural order (points? name?) and the same class must be ranked differently for different competitions; separate `Comparator<Team>` objects keep `Team` unchanged and let `Leaderboard<T>` work for any type (players, colleges, quiz teams).
* **Comparator expression:** `Comparator.comparingInt((Team t)->t.points).reversed().thenComparing(Comparator.comparingInt((Team t)->t.goalDiff).reversed()).thenComparing(t->t.name)`. Each level only runs when all earlier levels returned 0, and `Integer.compare` (inside `comparingInt`) never overflows.
* **Generic class:** `Leaderboard<T>` holds `Comparator<? super T>` (a comparator for a supertype can rank subtypes – e.g. a `Comparator<Team>` ranks `ChampionTeam`s). The bounded type `<T extends Comparable<? super T>>` is used only when elements have a natural order (`rankNatural`). The wildcard `List<? extends T>` / `Collection<? extends T>` is useful for *reading* from a list of subtypes.

### Complete the Code
Output: `[Eagles(9,6), Bisons(9,4), Panthers(9,4), Cobras(6,1)]` – matches the expected output. **Bisons is above Panthers** because points (9) and goal difference (4) are equal, so the third level (name A–Z) decides and "Bisons" < "Panthers". `ranking()` copies the list first, so `items` is never modified.

### Tests
* Data set 1 (10 teams with ties at every level): observed order equals the order worked out by hand and is identical after shuffling the input.
* Data set 2 (extreme points, identical teams), violations found by brute force [antisymmetry, transitivity, equal-consistency]: GOOD [0,0,0]; **SUBTRACT** (`b.points − a.points`) [0, **33**, 0] – `MAX_VALUE` vs `−5` overflows, giving a cycle; **NEVER_ZERO** (ties return 1) [**13**, 0, 0]; **NO_NAME** (ignores tie-breaker) is internally consistent but incomplete: `[Panthers,Bisons]` and `[Bisons,Panthers]` produce *different* rankings of the same teams, i.e. ties are resolved by input order (violates "equal items compare as equal / deterministic ranking").
* The sort of 200 random teams did not throw `IllegalArgumentException` for the broken comparators (TimSort detects violations only occasionally) – a broken comparator can silently produce a wrong order, which is why the explicit check is needed.

### Correctness argument (reasoning, not output)
Level 1 compares integers with `Integer.compare` on `points`, reversed → a total preorder. Level 2 does the same on `goalDiff`; level 3 compares `name` (String total order). The comparator is the **lexicographic composition** of total preorders: if all earlier levels tie it defers to the next. A lexicographic product of total preorders is a total preorder, so it is antisymmetric and transitive, and the final level is a total order on names; it returns 0 only when `points`, `goalDiff` and `name` are all equal, i.e. exactly the items that are interchangeable.

### Alternative concept and complexity (n = 400,000, many ties, avg of 4 runs)
| Approach | Number of O(n log n) sorts | Time | Extra space |
|---|---|---|---|
| Comparator chain | 1 | 541 ms | O(n) copy for TimSort merge buffer |
| `Team implements Comparable` (same logic in `compareTo`) | 1 | 282 ms | same |
| three stable sorts (name, goalDiff, points) | 3 | 505 ms | same, reused per sort |
All three give the identical result (verified). Time complexity of all is O(n log n) comparisons, the three-sort version has 3× the sorts but each is cheaper (single key; and TimSort is fast on partly ordered input). The chain/Comparable version is the *only* one whose correctness does not depend on the order of the sorts. **Reuse:** the comparator chain – `Comparable` would bake one ordering into `Team`, and the stable-sort trick forces callers to remember the reverse order of keys.

---
## Q6 – Priority queue for triage (Task06)

### Design
A plain queue (FIFO) serves in arrival order, so a critical patient waits behind minor ones. `PriorityQueue<Patient>` with `Comparator.comparingInt(severity).thenComparingLong(arrival)` serves the lowest severity number first; the **arrival counter** (strictly increasing, assigned in `admit`) is the second key, so equal severities are served in arrival order (PriorityQueue alone is *not* stable). `poll()` removes the next patient; `peek()` shows the next without removing.

### Correctness check (8 patients, worked out by hand)
Arrival: Ana S3, Bala S1, Chitra S3, Dev S5, Esha S1, Farid S2, Gita S3, Hari S1 → expected **Bala Esha Hari Farid Ana Chitra Gita Dev**; observed identical (`true`).
`System.out.println(pq)` printed `[Bala(S1), Esha(S1), Farid(S2), Hari(S1), Ana(S3), Chitra(S3), Gita(S3), Dev(S5)]` – this is the **internal heap array** (only guarantees parent ≤ children), not treatment order; to display the waiting list in order, copy the queue and `poll()` it, or sort a copy.

### Complete the Code
Output `Meena(S1) Kumar(S1) Lata(S2) Ravi(S3) John(S3) Asha(S5)` – matches. Ravi and John both have severity 3, but Ravi's `arrival` value (0) is smaller than John's (2), so the second comparator key puts Ravi first.

### Growth (prediction: add and poll O(log n))
| n | ns / admit | ns / serve |
|---|---|---|
| 10,000 | 139 | 576 |
| 100,000 | 33 | 223 |
| 1,000,000 | 93 | 364 |
Per-operation cost stays in the same order of magnitude (hundreds of ns) while n grows ×100 – consistent with O(log n) (log₂ grows 13 → 20) plus cache effects; the 10,000-row is dominated by JIT/GC noise (run-to-run variation), so I rely on the 100,000→1,000,000 trend (×1.6 per serve for ×10 size).
Alternatives, total ms to admit and serve n patients: n = 2,000: PQ 0.48 / sorted LinkedList 4.67 / 5 queues 1.59; n = 10,000: 1.63 / **158.5** / 2.84; n = 40,000: 7.59 / **4,505** / 0.98 (O(n²) total for the sorted list as predicted).

### Starvation and a fair rule
Strict priority always prefers a newly arrived S1 patient over a waiting S5 patient; if S1 patients arrive faster than they are served, an S5 patient is never reached. Simulation (2 S1 arrivals and 1 service per tick): **strict – Zed still waiting after 3,000 ticks**.
Fair rule (**deadline key**): key = `arrival + waitBudget[severity]` with budgets {0, 20, 50, 100, 200}; the smallest key is served first (ties: severity, then arrival). It is still a PriorityQueue (O(log n), no rescanning) but an S5 patient's key eventually becomes smaller than that of every *newly* arriving patient. Result: **aging – Zed served after 401 ticks**; with no long waits the order is still severity-first (`k(S1) l(S3) m(S5)`).
(Clinical safety note: budgets must be set by clinicians – a patient with S1 must never be overtaken by many S5 patients.)

### Deployment decision
5,000 patients/day ≈ one every 17 seconds on average. Measured: admitting and serving all 5,000 took **≈1.5 ms in total (0.29 µs per patient)**; even 1,000,000 queued patients cost < 1 µs per operation. The data structure is far from being a bottleneck: **yes, the design can serve a city hospital**. The real constraints are not the queue (persistence, concurrency – PriorityQueue is not thread-safe, use `PriorityBlockingQueue` or a lock – and clinical rules).

### Alternative concept and complexity
| Design | admit | serve next | space |
|---|---|---|---|
| PriorityQueue (binary heap) | O(log n) | O(log n) | O(n) array |
| sorted LinkedList | O(n) (walk to position) | O(1) | O(n), node per patient (≈24 B overhead) |
| array of 5 FIFO queues | O(1) | O(1) (at most 5 checks) | O(n) + 5 queue headers |
The 5-bucket design is the fastest (0.98 ms at n = 40,000) because the number of severities is fixed; the heap is more general (any number of keys, such as the deadline key above).

---
## Q7 – Diagnose, redesign and validate (Task07; chosen solution: Q4 pharmacy)

### A. Diagnosis (evidence from Q4)
At N = 200,000 the LinkedHashMap lookup (57 ns) and insertion-order report (1.2 ms) are cheap, but the code-order report costs **133.8 ms**, versus **9.7 ms** when the data is already in order (TreeMap). Time is spent in copying keys and sorting – ≈ n·log₂ n ≈ 3.5 million String comparisons – and *this work is repeated for every report* although stock updates never change the order and only a few medicines are added between reports. The limitation comes from the hash-based structure: it has no key order, so order is rebuilt from scratch each time. (Replacing it by TreeMap would speed up the report but make every lookup 5–15× slower – 939 ns at 200,000 – and put the insertion-order report at a 199 ms cost.)

### B. Design objective
Report cost proportional to the **change** since the last report, not to n log n; keep O(1) lookup and the insertion-order report; identical output. Target: ≥ 5× faster reports and ≥ 5× fewer comparisons when k ≪ n new medicines arrive between reports.

### C. Revised design
```
class Inventory:
    LinkedHashMap<code, Medicine> map        // unchanged: O(1) get/put, insertion order
    ArrayList<Medicine> view                 // cached, sorted by code (same Medicine objects)
    ArrayList<Medicine> pending              // added since last report
    add(m):    map.put(m.code, m); pending.add(m)
    report():  if pending not empty:
                   sort(pending)                          // k log k
                   view = merge(view, pending)            // n + k comparisons
                   pending.clear()
               return iterate(view)                       // O(n), no sort
```
*Properties exploited:* (1) stock updates change a field that is not the sort key, and `view` holds references to the same objects, so no maintenance is needed; (2) merging two sorted runs is linear; (3) only the small delta is sorted. *Avoided:* re-sorting all n medicines, copying all values on every report. *Cost:* one extra reference per medicine (≈4–8 bytes), the first report still performs a full build, and **removals** are not handled (would need a tombstone or an O(n) removal from `view`).

### D. Prediction (before running)
With n = 50,000 and k ≤ 100 new medicines per report: report time ≥ 5× lower; comparisons fall from ≈ n·log₂ n (≈ 0.7–0.8 million) to roughly n + k log k (≈ 50–55 thousand), i.e. ≈ 15×; the advantage shrinks as k approaches n; output identical.

### E. Validation (n = 50,000; 10 reports per run; 500 stock updates between reports; best of 3 runs after warm-ups; per-report averages)
| new meds per report (k) | ORIG ms | ORIG comparisons | NEW ms | NEW comparisons | speed-up | comparison ratio | same output |
|---|---|---|---|---|---|---|---|
| 0 | 34.43 | 717,309 | 0.51 | 0 | 67× | – | true |
| 1 | 23.81 | 717,491 | 3.42 | 19,959 | 7.0× | 36× | true |
| 10 | 23.22 | 718,479 | 3.39 | 46,542 | 6.8× | 15× | true |
| 100 | 21.71 | 725,634 | 2.18 | 50,715 | 9.9× | 14× | true |
| 1,000 | 28.33 | 804,013 | 4.45 | 64,118 | 6.4× | 12.5× | true |
| 10,000 | 60.78 | 1,625,166 | 11.68 | 225,373 | 5.2× | 7.2× | true |
Allocated memory per report: 616 KB (original, copy of all references) vs 0–220 KB (new) for k ≤ 1,000.

### F. Defence
* **Prediction supported:** ≥ 5× faster for every k tested, 12–36× fewer comparisons for k ≤ 1,000 (merge stops comparing once the pending run is exhausted, so comparisons can be below n); the time speed-up (≈7–10×) is smaller than the comparison reduction because the final O(n) pass over the report and memory allocation remain.
* **Magnitude:** from ≈22–34 ms to ≈2–4 ms per report at n = 50,000; 67× when nothing changed (cached view reused as is). The numbers at small k are noisy (k = 1 slower than k = 100) – the ranking of designs is robust, not the exact ratios.
* **New costs/trade-offs:** extra memory for the view, more code and a consistency invariant to maintain (pending/view), a latency spike for the first report after a bulk load (≈ full sort: at k = 10,000 the gain falls to 5.2× and at k ≈ n it would approach 1×), removals not yet supported.
* **When the original is still preferable:** when the code-order report is requested rarely (once an evening) – a single sort of 50,000 items costs ≈ 25 ms, the cache saves nothing and the extra state is not worth it – or when the inventory is mostly rebuilt between reports (k ≈ n).
