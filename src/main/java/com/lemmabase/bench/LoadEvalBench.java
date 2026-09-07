package com.lemmabase.bench;

import com.lemmabase.lemma.Engine;
import com.lemmabase.lemma.LemmaException;
import com.lemmabase.lemma.Response;
import com.lemmabase.lemma.RuleResult;
import com.lemmabase.lemma.RunRequest;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/** Timed load vs evaluate harness matching Rust engine bench methodology. */
public final class LoadEvalBench {
  /** One shape/size measurement. */
  public record Result(
      String shape,
      int rules,
      int sourceBytes,
      double loadMedianMs,
      double evalMedianMs,
      double evalP95Ms,
      boolean explain,
      boolean evalSkipped) {}

  private final int loadTrials;
  private final int warmup;
  private final int evals;
  private final boolean explain;
  private final boolean loadOnly;

  /**
   * @param loadTrials timed fresh-engine loads
   * @param warmup discarded evals before timed loop
   * @param evals timed eval iterations
   * @param explain RunRequest.explain
   * @param loadOnly skip eval phase
   */
  public LoadEvalBench(int loadTrials, int warmup, int evals, boolean explain, boolean loadOnly) {
    if (loadTrials < 1) {
      throw new IllegalArgumentException("--loads must be >= 1");
    }
    if (warmup < 0) {
      throw new IllegalArgumentException("--warmup must be >= 0");
    }
    if (!loadOnly && evals < 1) {
      throw new IllegalArgumentException("--evals must be >= 1");
    }
    this.loadTrials = loadTrials;
    this.warmup = warmup;
    this.evals = evals;
    this.explain = explain;
    this.loadOnly = loadOnly;
  }

  /**
   * Runs load (+ optional eval) benches for one generated Spec.
   *
   * @param fixture generated Spec
   * @return timing row
   */
  public Result run(SpecGenerator.GeneratedSpec fixture) {
    long[] loadNs = new long[loadTrials];
    for (int i = 0; i < loadTrials; i++) {
      try (Engine engine = Engine.create(BenchLimits.forSize(fixture.limitScale()))) {
        long t0 = System.nanoTime();
        engine.load(fixture.source());
        loadNs[i] = System.nanoTime() - t0;
      } catch (LemmaException e) {
        throw wrapLemma("load", fixture, e);
      }
    }

    if (loadOnly) {
      return new Result(
          fixture.shape(),
          fixture.rules(),
          fixture.sourceBytes(),
          nsToMs(percentile(loadNs, 0.50)),
          Double.NaN,
          Double.NaN,
          explain,
          true);
    }

    long[] evalNs = new long[evals];
    try (Engine engine = Engine.create(BenchLimits.forSize(fixture.limitScale()))) {
      engine.load(fixture.source());
      RunRequest request =
          RunRequest.of(fixture.specName())
              .data(fixture.data())
              .rules(List.of(fixture.terminal()))
              .explain(explain);
      for (int i = 0; i < warmup; i++) {
        engine.run(request);
      }
      Response last = null;
      for (int i = 0; i < evals; i++) {
        long t0 = System.nanoTime();
        last = engine.run(request);
        evalNs[i] = System.nanoTime() - t0;
      }
      assertEval(fixture, last);
    } catch (LemmaException e) {
      throw wrapLemma("eval", fixture, e);
    }

    return new Result(
        fixture.shape(),
        fixture.rules(),
        fixture.sourceBytes(),
        nsToMs(percentile(loadNs, 0.50)),
        nsToMs(percentile(evalNs, 0.50)),
        nsToMs(percentile(evalNs, 0.95)),
        explain,
        false);
  }

  /** Fail closed if the terminal rule is missing or not a Number (and wrong for known shapes). */
  static void assertEval(SpecGenerator.GeneratedSpec fixture, Response response) {
    BigDecimal got = requireNumber(response, fixture.terminal());
    BigDecimal expected = expectedTotal(fixture);
    if (expected != null && got.compareTo(expected) != 0) {
      throw new IllegalStateException(
          "eval sanity shape="
              + fixture.shape()
              + " rules="
              + fixture.rules()
              + " expected "
              + fixture.terminal()
              + "="
              + expected
              + " got "
              + got);
    }
  }

  static BigDecimal requireNumber(Response response, String rule) {
    RuleResult result = response.results().get(rule);
    if (result instanceof RuleResult.Number n) {
      return n.number();
    }
    throw new IllegalStateException("expected Number for " + rule + ", got " + result);
  }

  /**
   * Known totals for micro-shapes. Logistics/snapshot: Number presence only (synthetic cheapest).
   */
  static BigDecimal expectedTotal(SpecGenerator.GeneratedSpec fixture) {
    int n = fixture.rules();
    return switch (fixture.shape()) {
      case "deep", "unless" -> BigDecimal.valueOf(n);
      case "wide" -> BigDecimal.valueOf(2L * n);
      default -> null;
    };
  }

  private static RuntimeException wrapLemma(
      String phase, SpecGenerator.GeneratedSpec fixture, LemmaException e) {
    StringBuilder msg = new StringBuilder();
    msg.append("LemmaException during ")
        .append(phase)
        .append(" shape=")
        .append(fixture.shape())
        .append(" rules=")
        .append(fixture.rules())
        .append(": ")
        .append(e.getMessage());
    e.errors()
        .forEach(
            err -> {
              if (err.limitName() != null) {
                msg.append(" [limit=")
                    .append(err.limitName())
                    .append(" value=")
                    .append(err.limitValue())
                    .append(" actual=")
                    .append(err.actualValue())
                    .append(']');
              } else {
                msg.append(" [").append(err.kind()).append(": ").append(err.message()).append(']');
              }
            });
    return new RuntimeException(msg.toString(), e);
  }

  static long percentile(long[] samples, double p) {
    long[] copy = Arrays.copyOf(samples, samples.length);
    Arrays.sort(copy);
    if (copy.length == 1) {
      return copy[0];
    }
    double rank = p * (copy.length - 1);
    int lo = (int) Math.floor(rank);
    int hi = (int) Math.ceil(rank);
    if (lo == hi) {
      return copy[lo];
    }
    double w = rank - lo;
    return Math.round(copy[lo] * (1.0 - w) + copy[hi] * w);
  }

  private static double nsToMs(long ns) {
    return ns / 1_000_000.0;
  }
}
