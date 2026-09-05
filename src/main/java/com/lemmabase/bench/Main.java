package com.lemmabase.bench;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** CLI entry: generate Specs, optionally write them, measure load/eval or scoped replan. */
public final class Main {
  private Main() {}

  /**
   * @param args CLI flags
   */
  public static void main(String[] args) {
    try {
      Cli cli = Cli.parse(args);
      if (cli.help) {
        printHelp();
        return;
      }
      List<LoadEvalBench.Result> loadRows = new ArrayList<>();
      List<ReplanBench.Result> replanRows = new ArrayList<>();
      List<SnapshotBench.Result> snapshotRows = new ArrayList<>();
      LoadEvalBench loadBench =
          cli.generateOnly
              ? null
              : new LoadEvalBench(cli.loads, cli.warmup, cli.evals, cli.explain, cli.loadOnly);
      ReplanBench replanBench = cli.generateOnly ? null : new ReplanBench(cli.loads);
      SnapshotBench snapshotBench = cli.generateOnly ? null : new SnapshotBench(cli.loads);

      for (String shape : cli.shapes) {
        for (int rules : cli.rules) {
          if ("replan".equals(shape)) {
            SpecGenerator.ReplanFixture fixture = SpecGenerator.replan(rules);
            System.err.printf(
                Locale.ROOT,
                "bench shape=replan rules=%d src_bytes=%d terminal=%s.%s%n",
                fixture.rules(),
                fixture.sourceBytes(),
                fixture.terminalSpec(),
                fixture.terminalRule());
            if (cli.writeDir != null) {
              writeFixture(cli.writeDir, fixture.asGeneratedSpec());
            }
            if (!cli.generateOnly) {
              replanRows.add(replanBench.run(fixture));
            }
          } else if ("snapshot".equals(shape)) {
            SpecGenerator.GeneratedSpec fixture = SpecGenerator.generate("logistics", rules);
            System.err.printf(
                Locale.ROOT,
                "bench shape=snapshot rules=%d src_bytes=%d terminal=%s.%s%n",
                fixture.rules(),
                fixture.sourceBytes(),
                fixture.specName(),
                fixture.terminal());
            if (cli.writeDir != null) {
              writeFixture(cli.writeDir, fixture);
            }
            if (!cli.generateOnly) {
              snapshotRows.add(snapshotBench.run(fixture));
            }
          } else {
            SpecGenerator.GeneratedSpec fixture = SpecGenerator.generate(shape, rules);
            System.err.printf(
                Locale.ROOT,
                "bench shape=%s rules=%d src_bytes=%d terminal=%s.%s%n",
                fixture.shape(),
                fixture.rules(),
                fixture.sourceBytes(),
                fixture.specName(),
                fixture.terminal());
            if (cli.writeDir != null) {
              writeFixture(cli.writeDir, fixture);
            }
            if (!cli.generateOnly) {
              loadRows.add(loadBench.run(fixture));
            }
          }
        }
      }
      if (!cli.generateOnly) {
        if (!loadRows.isEmpty()) {
          BenchReport.print(loadRows);
        }
        if (!replanRows.isEmpty()) {
          BenchReport.printReplan(replanRows);
        }
        if (!snapshotRows.isEmpty()) {
          BenchReport.printSnapshot(snapshotRows);
        }
      }
    } catch (IllegalArgumentException e) {
      System.err.println(e.getMessage());
      printHelp();
      System.exit(2);
    } catch (Exception e) {
      System.err.println(e.getMessage());
      if (e.getCause() != null) {
        e.getCause().printStackTrace(System.err);
      } else {
        e.printStackTrace(System.err);
      }
      System.exit(1);
    }
  }

  private static void writeFixture(Path dir, SpecGenerator.GeneratedSpec fixture) throws Exception {
    Files.createDirectories(dir);
    String file = fixture.shape() + "_" + fixture.rules() + ".lemma";
    Path out = dir.resolve(file);
    Files.writeString(out, fixture.source());
    System.err.printf(Locale.ROOT, "wrote %s (%d bytes)%n", out, fixture.sourceBytes());
  }

  private static void printHelp() {
    System.err.println(
        """
        Lemma Java performance harness

        Usage:
          ./mvnw -q exec:java -Dexec.args="[options]"

        Options:
          --shape deep|wide|unless|logistics|replan|snapshot[,...]  shapes (default: wide)
          --rules N[,N...]                 scale sweep (default: 100,1000)
          --loads N                        timed load/update/snapshot trials (default: 5)
          --warmup N                       discarded evals (default: 50; load-eval only)
          --evals N                        timed evals (default: 500; load-eval only)
          --load-only                      skip eval; measure load only (load-eval only)
          --explain                        enable RunRequest.explain (load-eval only)
          --write DIR                      write generated .lemma files to DIR
          --generate-only                  generate (and optional --write); skip Engine bench
          --help                           this message

        replan: cold load vs Engine.update on tip → mid → base (shop → quote → base_rates)
          tip=shop, mid=quote (thin), base=base_rates (fat --rules arms), sibling=unrelated
          --rules must be >= 1050 (use 1050, 6300, 18900, 126000); small N rejected
          primary: upd_mid = replace quote (dirties quote+shop; fat base kept)
          also: upd_base | upd_ident (quote) | upd_unrelated

        snapshot: cold load logistics → Engine.snapshot → Engine.fromSnapshot
          columns: load_med_ms | snap_med_ms | snap_bytes | restore_med_ms | ok
          showcase: --shape snapshot --rules 126000 --loads 3

        logistics --rules presets (rate cells, research-derived):
          1050    ground     — 1 carrier × Ground only
          6300    carrier    — 1 carrier × 6 services
          18900   d2c        — 3 carriers × 6 services
          126000  enterprise — 20 contracts × 6 services

        Requires com.lemmabase:lemma-engine 0.9.9+ from Maven Central (JDK 21+).
        """);
  }

  /** Parsed CLI. */
  static final class Cli {
    final List<String> shapes;
    final int[] rules;
    final int loads;
    final int warmup;
    final int evals;
    final boolean explain;
    final boolean loadOnly;
    final boolean generateOnly;
    final Path writeDir;
    final boolean help;

    private Cli(
        List<String> shapes,
        int[] rules,
        int loads,
        int warmup,
        int evals,
        boolean explain,
        boolean loadOnly,
        boolean generateOnly,
        Path writeDir,
        boolean help) {
      this.shapes = shapes;
      this.rules = rules;
      this.loads = loads;
      this.warmup = warmup;
      this.evals = evals;
      this.explain = explain;
      this.loadOnly = loadOnly;
      this.generateOnly = generateOnly;
      this.writeDir = writeDir;
      this.help = help;
    }

    static Cli parse(String[] args) {
      List<String> shapes = List.of("wide");
      int[] rules = new int[] {100, 1000};
      int loads = 5;
      int warmup = 50;
      int evals = 500;
      boolean explain = false;
      boolean loadOnly = false;
      boolean generateOnly = false;
      Path writeDir = null;
      boolean help = false;

      for (int i = 0; i < args.length; i++) {
        String a = args[i];
        switch (a) {
          case "--help", "-h" -> help = true;
          case "--explain" -> explain = true;
          case "--load-only" -> loadOnly = true;
          case "--generate-only" -> generateOnly = true;
          case "--shape" -> shapes = splitCsv(requireValue(args, ++i, a));
          case "--rules" -> rules = parseInts(requireValue(args, ++i, a));
          case "--loads" -> loads = parsePositive(requireValue(args, ++i, a), a);
          case "--warmup" -> warmup = parseNonNegative(requireValue(args, ++i, a), a);
          case "--evals" -> evals = parsePositive(requireValue(args, ++i, a), a);
          case "--write" -> writeDir = Path.of(requireValue(args, ++i, a));
          default -> throw new IllegalArgumentException("unknown arg: " + a);
        }
      }
      return new Cli(
          shapes, rules, loads, warmup, evals, explain, loadOnly, generateOnly, writeDir, help);
    }

    private static String requireValue(String[] args, int i, String flag) {
      if (i >= args.length) {
        throw new IllegalArgumentException(flag + " requires a value");
      }
      return args[i];
    }

    private static List<String> splitCsv(String raw) {
      return Arrays.stream(raw.split(","))
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .map(s -> s.toLowerCase(Locale.ROOT))
          .toList();
    }

    private static int[] parseInts(String raw) {
      return Arrays.stream(raw.split(","))
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .mapToInt(
              s -> {
                try {
                  int v = Integer.parseInt(s);
                  if (v < 1) {
                    throw new IllegalArgumentException("--rules values must be >= 1");
                  }
                  return v;
                } catch (NumberFormatException e) {
                  throw new IllegalArgumentException("bad --rules value: " + s);
                }
              })
          .toArray();
    }

    private static int parsePositive(String raw, String flag) {
      int v = Integer.parseInt(raw);
      if (v < 1) {
        throw new IllegalArgumentException(flag + " must be >= 1");
      }
      return v;
    }

    private static int parseNonNegative(String raw, String flag) {
      int v = Integer.parseInt(raw);
      if (v < 0) {
        throw new IllegalArgumentException(flag + " must be >= 0");
      }
      return v;
    }
  }
}
