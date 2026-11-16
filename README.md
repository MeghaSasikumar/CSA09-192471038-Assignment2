# CSA09 – Programming in Java – Assignment 2 (CO2)

| | |
|---|---|
| Name | **<YOUR NAME>** |
| Register number | **<YOUR REGISTER NUMBER>** |
| Java version | OpenJDK 21 (any JDK 17+ works) |
| Repository | CSA09-<RegisterNumber>-Assignment2 |

## How to compile and run
Each task folder is self-contained. From inside the folder:

| Task | Compile | Run |
|---|---|---|
| Task01 | `javac *.java` | `java Q1Benchmark` and `java Q1Concurrency` |
| Task02 | `javac *.java` | `java Q2ListBenchmark` |
| Task03 | `javac *.java` ; `cd before && javac Attendance.java && java Attendance` ; `cd ../after && javac Attendance.java && java Attendance` | `java Q3Experiments` |
| Task04 | `javac *.java` | `java -Xmx2g Q4MapBenchmark` |
| Task05 | `javac *.java` | `java LeaderboardDemo` and `java Q5Study` |
| Task06 | `javac *.java` | `java Triage` and `java Q6Study` |
| Task07 | `javac *.java` | `java -Xmx2g Q7Redesign` |

`./run_all.sh` compiles and runs everything and writes `output_*.txt` files. Raw timings are in `q1_raw.csv`, `q2_raw.csv`, `q4_raw.csv`.
The full analysis (predictions, evidence, complexity, decisions, Q7 A–F) is in **REPORT.md**.

## Result summary
| Task | Result |
|---|---|
| Task01 | `+` in a loop is O(n²): doubling fields ⇒ ~4× time; 163× slower and 327× more bytes than StringBuilder at 2,000 fields. Shared StringBuilder lost data/threw in 4 of 5 rounds; StringBuffer and per-thread builders were always correct. |
| Task02 | ArrayList wins get/middle/iterate; front operations cost O(n) (7.8 ms per 1,000 ops at 100k). ArrayDeque is ≈690× faster than ArrayList for the "Up Next" queue at 100k. |
| Task03 | Without `hashCode` the set has **3** students; with it **2**. Mutating a hashed field after insertion breaks `contains`/`remove`. |
| Task04 | LinkedHashMap/HashMap ≈ 33–83 ns per get; TreeMap 330–940 ns. TreeMap is the only map that yields code order natively; LinkedHashMap the only one with insertion order. |
| Task05 | Output `[Eagles(9,6), Bisons(9,4), Panthers(9,4), Cobras(6,1)]`. Subtraction comparator violated transitivity (33 violations). |
| Task06 | Output `Meena(S1) Kumar(S1) Lata(S2) Ravi(S3) John(S3) Asha(S5)`. Strict priority starves S5; deadline-key design serves it after 401 ticks. 5,000 patients/day ≈ 1.5 ms total. |
| Task07 | Cached sorted view + merge of new items: 5–10× faster code-order reports and 7–15× fewer comparisons, identical output. |
