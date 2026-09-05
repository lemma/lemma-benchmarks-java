package com.lemmabase.bench;

import com.lemmabase.lemma.Engine;
import com.lemmabase.lemma.LemmaException;
import com.lemmabase.lemma.Response;
import com.lemmabase.lemma.RuleResult;
import com.lemmabase.lemma.RunRequest;
import java.math.BigDecimal;
import java.util.List;

/**
 * Scoped {@code Engine.update} on tip → mid → base ({@code shop → quote → base_rates}). Primary:
 * replace mid {@code quote} (dependents replan; base kept).
 */
public final class ReplanBench {
  /**
   * @param rules base_rates / unrelated arm count
   * @param sourceBytes workspace bytes
   * @param loadMedianMs cold full load
   * @param updateMidMedianMs replace quote (replans quote+shop; base kept)
   * @param updateBaseMedianMs replace base_rates (replans base+quote+shop)
   * @param updateMidIdenticalMedianMs same quote bytes
   * @param updateUnrelatedMedianMs replace unrelated only
   * @param evalAfterMidUs eval after mid V2 (base still V1)
   * @param resultOk mid then base sanity checks passed
   */
  public record Result(
      int rules,
      int sourceBytes,
      double loadMedianMs,
      double updateMidMedianMs,
      double updateBaseMedianMs,
      double updateMidIdenticalMedianMs,
      double updateUnrelatedMedianMs,
      double evalAfterMidUs,
      boolean resultOk) {}

  private final int trials;

  /**
   * @param trials timed load/update repetitions
   */
  public ReplanBench(int trials) {
    if (trials < 1) {
      throw new IllegalArgumentException("--loads must be >= 1");
    }
    this.trials = trials;
  }

  /**
   * @param fixture replan workspace
   * @return timing row
   */
  public Result run(SpecGenerator.ReplanFixture fixture) {
    long[] loadNs = new long[trials];
    long[] midNs = new long[trials];
    long[] baseNs = new long[trials];
    long[] midIdentNs = new long[trials];
    long[] unrelatedNs = new long[trials];
    long evalAfterMidNs = -1L;
    boolean resultOk = false;

    RunRequest request =
        RunRequest.of(fixture.terminalSpec())
            .data(fixture.data())
            .rules(List.of(fixture.terminalRule()));

    for (int i = 0; i < trials; i++) {
      try (Engine engine = Engine.create(BenchLimits.forSize(fixture.limitScale()))) {
        long t0 = System.nanoTime();
        engine.load(fixture.fullSource());
        loadNs[i] = System.nanoTime() - t0;

        // Primary: replace mid Spec in tip → mid → base
        t0 = System.nanoTime();
        engine.update(null, fixture.quoteV2(), null);
        midNs[i] = System.nanoTime() - t0;

        if (i == trials - 1) {
          long e0 = System.nanoTime();
          BigDecimal afterMid = requireNumber(engine.run(request), fixture.terminalRule());
          evalAfterMidNs = System.nanoTime() - e0;
          if (afterMid.compareTo(fixture.expectedAfterMidV2()) != 0) {
            throw new IllegalStateException(
                "mid update sanity: expected shop.cheapest="
                    + fixture.expectedAfterMidV2()
                    + " got "
                    + afterMid);
          }
        }

        t0 = System.nanoTime();
        engine.update(null, fixture.baseV2(), null);
        baseNs[i] = System.nanoTime() - t0;

        if (i == trials - 1) {
          BigDecimal afterBase = requireNumber(engine.run(request), fixture.terminalRule());
          if (afterBase.compareTo(fixture.expectedAfterBaseV2()) != 0) {
            throw new IllegalStateException(
                "base update sanity: expected shop.cheapest="
                    + fixture.expectedAfterBaseV2()
                    + " got "
                    + afterBase);
          }
          resultOk = true;
        }

        t0 = System.nanoTime();
        engine.update(null, fixture.quoteV2(), null);
        midIdentNs[i] = System.nanoTime() - t0;

        t0 = System.nanoTime();
        engine.update(null, fixture.unrelatedV2(), null);
        unrelatedNs[i] = System.nanoTime() - t0;
      } catch (LemmaException e) {
        throw wrap(fixture, e);
      }
    }

    return new Result(
        fixture.rules(),
        fixture.sourceBytes(),
        nsToMs(percentile(loadNs, 0.50)),
        nsToMs(percentile(midNs, 0.50)),
        nsToMs(percentile(baseNs, 0.50)),
        nsToMs(percentile(midIdentNs, 0.50)),
        nsToMs(percentile(unrelatedNs, 0.50)),
        nsToUs(evalAfterMidNs),
        resultOk);
  }

  private static BigDecimal requireNumber(Response response, String rule) {
    RuleResult result = response.results().get(rule);
    if (result instanceof RuleResult.Number n) {
      return n.number();
    }
    throw new IllegalStateException("expected Number for " + rule + ", got " + result);
  }

  private static RuntimeException wrap(SpecGenerator.ReplanFixture fixture, LemmaException e) {
    StringBuilder msg = new StringBuilder();
    msg.append("LemmaException during replan rules=")
        .append(fixture.rules())
        .append(": ")
        .append(e.getMessage());
    e.errors()
        .forEach(
            err ->
                msg.append(" [").append(err.kind()).append(": ").append(err.message()).append(']'));
    return new RuntimeException(msg.toString(), e);
  }

  private static long percentile(long[] samples, double p) {
    return LoadEvalBench.percentile(samples, p);
  }

  private static double nsToMs(long ns) {
    return ns / 1_000_000.0;
  }

  private static double nsToUs(long ns) {
    return ns / 1_000.0;
  }
}
