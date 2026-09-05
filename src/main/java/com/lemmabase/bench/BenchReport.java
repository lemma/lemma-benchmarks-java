package com.lemmabase.bench;

import java.util.List;
import java.util.Locale;

/** Stdout table for bench rows. */
public final class BenchReport {
  private BenchReport() {}

  /**
   * Prints a fixed-width table.
   *
   * @param rows measurement rows
   */
  public static void print(List<LoadEvalBench.Result> rows) {
    System.out.printf(
        Locale.ROOT,
        "%-8s %8s %12s %14s %14s %12s %8s%n",
        "shape",
        "rules",
        "src_bytes",
        "load_med_ms",
        "eval_med_us",
        "eval_p95_us",
        "explain");
    System.out.printf(
        Locale.ROOT,
        "%-8s %8s %12s %14s %14s %12s %8s%n",
        "--------",
        "--------",
        "------------",
        "--------------",
        "--------------",
        "------------",
        "--------");
    for (LoadEvalBench.Result r : rows) {
      if (r.evalSkipped()) {
        System.out.printf(
            Locale.ROOT,
            "%-8s %8d %12d %14.3f %14s %12s %8s%n",
            r.shape(),
            r.rules(),
            r.sourceBytes(),
            r.loadMedianMs(),
            "skip",
            "skip",
            r.explain() ? "y" : "n");
      } else {
        System.out.printf(
            Locale.ROOT,
            "%-8s %8d %12d %14.3f %14.3f %12.3f %8s%n",
            r.shape(),
            r.rules(),
            r.sourceBytes(),
            r.loadMedianMs(),
            r.evalMedianUs(),
            r.evalP95Us(),
            r.explain() ? "y" : "n");
      }
    }
  }

  /**
   * Prints snapshot load/restore bench rows.
   *
   * @param rows snapshot measurements
   */
  public static void printSnapshot(List<SnapshotBench.Result> rows) {
    System.out.printf(
        Locale.ROOT,
        "%-8s %8s %12s %14s %14s %12s %14s %6s%n",
        "shape",
        "rules",
        "src_bytes",
        "load_med_ms",
        "snap_med_ms",
        "snap_bytes",
        "restore_med_ms",
        "ok");
    System.out.printf(
        Locale.ROOT,
        "%-8s %8s %12s %14s %14s %12s %14s %6s%n",
        "--------",
        "--------",
        "------------",
        "--------------",
        "--------------",
        "------------",
        "--------------",
        "------");
    for (SnapshotBench.Result r : rows) {
      System.out.printf(
          Locale.ROOT,
          "%-8s %8d %12d %14.3f %14.3f %12d %14.3f %6s%n",
          "snapshot",
          r.rules(),
          r.sourceBytes(),
          r.loadMedianMs(),
          r.snapshotMedianMs(),
          r.snapshotBytes(),
          r.restoreMedianMs(),
          r.resultOk() ? "y" : "n");
    }
  }

  /**
   * Prints scoped-replan bench rows.
   *
   * @param rows replan measurements
   */
  public static void printReplan(List<ReplanBench.Result> rows) {
    System.out.printf(
        Locale.ROOT,
        "%-8s %8s %12s %14s %14s %14s %14s %14s %14s %6s%n",
        "shape",
        "rules",
        "src_bytes",
        "load_med_ms",
        "upd_mid_ms",
        "upd_base_ms",
        "upd_ident_ms",
        "upd_unrel_ms",
        "eval_mid_us",
        "ok");
    System.out.printf(
        Locale.ROOT,
        "%-8s %8s %12s %14s %14s %14s %14s %14s %14s %6s%n",
        "--------",
        "--------",
        "------------",
        "--------------",
        "--------------",
        "--------------",
        "--------------",
        "--------------",
        "--------------",
        "------");
    for (ReplanBench.Result r : rows) {
      System.out.printf(
          Locale.ROOT,
          "%-8s %8d %12d %14.3f %14.3f %14.3f %14.3f %14.3f %14.3f %6s%n",
          "replan",
          r.rules(),
          r.sourceBytes(),
          r.loadMedianMs(),
          r.updateMidMedianMs(),
          r.updateBaseMedianMs(),
          r.updateMidIdenticalMedianMs(),
          r.updateUnrelatedMedianMs(),
          r.evalAfterMidUs(),
          r.resultOk() ? "y" : "n");
    }
  }
}
