package com.lemmabase.bench;

import com.lemmabase.lemma.Engine;
import com.lemmabase.lemma.LemmaException;
import com.lemmabase.lemma.RunRequest;
import java.util.List;

/**
 * Cold load → {@code Engine.snapshot} → {@code Engine.fromSnapshot} on a logistics workspace.
 * Primary showcase: logistics 126000 (enterprise).
 */
public final class SnapshotBench {
  /**
   * @param rules logistics rate-cell preset
   * @param sourceBytes workspace source bytes
   * @param loadMedianMs cold full load
   * @param snapshotMedianMs {@code Engine.snapshot()}
   * @param snapshotBytes opaque artefact length
   * @param restoreMedianMs {@code Engine.fromSnapshot}
   * @param resultOk last-trial eval on restored engine succeeded
   */
  public record Result(
      int rules,
      int sourceBytes,
      double loadMedianMs,
      double snapshotMedianMs,
      int snapshotBytes,
      double restoreMedianMs,
      boolean resultOk) {}

  private final int trials;

  /**
   * @param trials timed load/snapshot/restore repetitions
   */
  public SnapshotBench(int trials) {
    if (trials < 1) {
      throw new IllegalArgumentException("--loads must be >= 1");
    }
    this.trials = trials;
  }

  /**
   * @param fixture logistics (or other) generated Spec
   * @return timing row
   */
  public Result run(SpecGenerator.GeneratedSpec fixture) {
    long[] loadNs = new long[trials];
    long[] snapNs = new long[trials];
    long[] restoreNs = new long[trials];
    int snapshotBytes = 0;
    boolean resultOk = false;

    RunRequest request =
        RunRequest.of(fixture.specName())
            .data(fixture.data())
            .rules(List.of(fixture.terminal()));

    for (int i = 0; i < trials; i++) {
      byte[] bytes;
      try (Engine engine = Engine.create(BenchLimits.forSize(fixture.limitScale()))) {
        long t0 = System.nanoTime();
        engine.load(fixture.source());
        loadNs[i] = System.nanoTime() - t0;

        t0 = System.nanoTime();
        bytes = engine.snapshot();
        snapNs[i] = System.nanoTime() - t0;
        snapshotBytes = bytes.length;
      } catch (LemmaException e) {
        throw wrap(fixture, "load/snapshot", e);
      }

      long t0 = System.nanoTime();
      try (Engine restored = Engine.fromSnapshot(bytes)) {
        restoreNs[i] = System.nanoTime() - t0;
        if (i == trials - 1) {
          LoadEvalBench.requireNumber(restored.run(request), fixture.terminal());
          resultOk = true;
        }
      } catch (LemmaException e) {
        throw wrap(fixture, "fromSnapshot", e);
      }
    }

    return new Result(
        fixture.rules(),
        fixture.sourceBytes(),
        nsToMs(percentile(loadNs, 0.50)),
        nsToMs(percentile(snapNs, 0.50)),
        snapshotBytes,
        nsToMs(percentile(restoreNs, 0.50)),
        resultOk);
  }

  private static RuntimeException wrap(
      SpecGenerator.GeneratedSpec fixture, String phase, LemmaException e) {
    StringBuilder msg = new StringBuilder();
    msg.append("LemmaException during ")
        .append(phase)
        .append(" snapshot rules=")
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
}
