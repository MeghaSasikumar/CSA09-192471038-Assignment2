#!/usr/bin/env bash
# Compiles and runs every task. Output of each program goes to output_<Program>.txt inside its Task folder.
set -e
root=$(pwd)
run() { dir=$1; shift; mkdir -p /tmp/ass2/$dir; (cd $root/$dir && javac -d /tmp/ass2/$dir "$@" && for m in ${MAINS}; do java -Xmx2g -cp /tmp/ass2/$dir $m > output_$m.txt 2>&1; echo "$dir/$m done"; done); }
MAINS="Q1Benchmark Q1Concurrency" run Task01 Q1Benchmark.java Q1Concurrency.java
MAINS="Q2ListBenchmark" run Task02 Q2ListBenchmark.java
MAINS="Q3Experiments" run Task03 Q3Experiments.java
mkdir -p /tmp/ass2/t3b /tmp/ass2/t3a
(cd Task03/before && javac -d /tmp/ass2/t3b Attendance.java && java -cp /tmp/ass2/t3b Attendance > ../output_Attendance_before.txt)
(cd Task03/after  && javac -d /tmp/ass2/t3a Attendance.java && java -cp /tmp/ass2/t3a Attendance > ../output_Attendance_after.txt)
MAINS="Q4MapBenchmark" run Task04 Q4MapBenchmark.java
MAINS="LeaderboardDemo Q5Study" run Task05 LeaderboardDemo.java Q5Study.java
MAINS="Triage Q6Study" run Task06 Triage.java Q6Study.java
MAINS="Q7Redesign" run Task07 Q7Redesign.java
