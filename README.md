# Lemma Java performance harness

This repository is a Maven harness that generates Lemma Specs at representative catalog scales and times cold load, evaluation, scoped updates, and snapshot restore through the Java engine. The logistics ladder fixtures here are byte-identical to those used by the engine’s published snapshot benchmarks. On release builds, an enterprise workspace (126000 rate cells, about 10 MiB of Spec text) cold-loads in about 32 s and restores from a snapshot in about 0.5 s; full tables and methodology are in the [engine benchmarks](https://github.com/lemma/lemma/blob/main/cli/documentation/reference/benchmarks/engine.md) reference. Recreate via the Java SDK with the commands in Run. Catalog sizing notes are in [research/](research/); a short hand-written Spec is in [examples/](examples/).

The work is licensed under Apache-2.0 ([LICENSE](LICENSE)).

## Requirements

- JDK 21 or newer
- Maven Wrapper (`./mvnw`)
- [`com.lemmabase:lemma-engine` 0.9.9](https://central.sonatype.com/artifact/com.lemmabase/lemma-engine) from Maven Central

The default JVM heap is `-Xmx2g`. For the larger presets, pass `-Dexec.jvmArgs="-Xmx8g"`.

## Run

Micro shapes (typically finishes in seconds):

```bash
./mvnw -q exec:java -Dexec.args="--shape deep,wide,unless --rules 100,1000 --loads 3 --warmup 10 --evals 50"
```

Logistics load and eval (about half a minute per cold load at the largest preset; multiply by `--loads`):

```bash
./mvnw -q exec:java -Dexec.jvmArgs="-Xmx8g" \
  -Dexec.args="--shape logistics --rules 1050,6300,18900,126000 --loads 3 --warmup 10 --evals 50"
```

Scoped replan. Uses the same catalog sizes as logistics. `--rules` is the arm count on the base catalog and must be at least 1050:

```bash
./mvnw -q exec:java -Dexec.jvmArgs="-Xmx8g" \
  -Dexec.args="--shape replan --rules 1050,6300,18900,126000 --loads 3"
```

Snapshot: load, snapshot, then restore:

```bash
./mvnw -q exec:java -Dexec.jvmArgs="-Xmx8g" \
  -Dexec.args="--shape snapshot --rules 126000 --loads 3"
```

Write generated Spec text without running the engine:

```bash
./mvnw -q exec:java -Dexec.args="--shape logistics --rules 1050 --generate-only --write target/generated-lemma"
```

In the result tables, `load_*`, `upd_*`, `snap_*`, and `restore_*` are milliseconds. `eval_*` values are microseconds.

## Shapes

| Shape | `--rules N` | Measures |
|-------|-------------|----------|
| `deep` | chain length | load / eval |
| `wide` | data + pair rules + fold | load / eval |
| `unless` | arms on one rule | load / eval |
| `logistics` | rate-cell preset (below) | load / eval of a multi-Spec rating workspace |
| `replan` | arms on `base_rates` / `unrelated` (at least 1050) | cold load vs `Engine.update` |
| `snapshot` | same presets as logistics | load, `snapshot()`, `fromSnapshot()` |

### Logistics and snapshot presets

| `--rules` | Profile | Contents |
|----------:|---------|----------|
| 1050 | ground | 1 carrier, Ground service (`7x150` cells), ZIP3 zones, quote, and rate-shop |
| 6300 | carrier | 1 carrier, 6 services |
| 18900 | d2c | 3 carriers, 6 services |
| 126000 | enterprise | 20 contracts, 6 services |

Workspace Specs include `zones_*`, `rates_*`, `accessorials`, `fuel_*`, `quote_*`, and the terminal rule `rate_shop.cheapest`. See [`research/logistics-system-lookup-scale.md`](research/logistics-system-lookup-scale.md) and [`research/logistics-in-lemma-syntax.md`](research/logistics-in-lemma-syntax.md). Prices in generated Specs are synthetic.

### Replan workspace

The dependency chain is `shop` uses `quote` uses `base_rates`. A sibling Spec `unrelated` sits outside that chain.

| Spec | Role |
|------|------|
| `base_rates` | Large `unless` catalog with `N` arms and no dependencies |
| `quote` | Thin Spec that `uses` `base_rates` |
| `shop` | Tip Spec that `uses` `quote`; terminal rule is `cheapest` |
| `unrelated` | Large catalog not on the dependency chain |

| Column | Operation |
|--------|-----------|
| `load_med_ms` | Cold `Engine.load` of the full workspace |
| `upd_mid_ms` | `update(quote)`: replans `quote` and `shop`; `base_rates` stays loaded |
| `upd_base_ms` | `update(base_rates)`: base and its dependents |
| `upd_ident_ms` | `update(quote)` with identical source |
| `upd_unrel_ms` | `update(unrelated)` |
| `eval_mid_us` | `shop.cheapest` after mid V2 while base is still V1 |
| `ok` | Expected totals after mid and base updates |

### Snapshot columns

| Column | Operation |
|--------|-----------|
| `load_med_ms` | Cold load |
| `snap_med_ms` | `Engine.snapshot()` |
| `snap_bytes` | Snapshot size in bytes |
| `restore_med_ms` | `Engine.fromSnapshot` |
| `ok` | `rate_shop.cheapest` is a `RuleResult.Number` after restore |

## Java API

```java
import com.lemmabase.lemma.Engine;
import com.lemmabase.lemma.Response;
import com.lemmabase.lemma.RuleResult;
import com.lemmabase.lemma.RunRequest;
import java.math.BigDecimal;
import java.util.Map;

try (Engine engine = Engine.create()) {
  engine.load("""
      spec order
      data quantity: number
      data unit_price: number
      rule total: quantity * unit_price
      """);

  Response response =
      engine.run(
          RunRequest.of("order")
              .data(
                  Map.of(
                      "quantity", 3,
                      "unit_price", new BigDecimal("19.99"))));

  RuleResult.Number total = (RuleResult.Number) response.results().get("total");
  BigDecimal amount = total.number();
}
```

Use `BigDecimal` or integers for decimals; `float` and `double` are rejected. Override limits with `Engine.create(ResourceLimits.builder()...)`. `Engine` serializes calls on an internal lock.

## Measured (logistics ladder)

Figures below are from the Lemma engine’s release snapshot bench (`cargo benchmarks engine`), same machine as the engine benchmarks doc, on fixtures byte-identical to `SpecGenerator.logistics`. Source of truth: [engine benchmarks](https://github.com/lemma/lemma/blob/main/cli/documentation/reference/benchmarks/engine.md).

| Profile | Rate cells | Source | Load | Snapshot | Restore |
|---------|-----------:|-------:|-----:|---------:|--------:|
| ground | 1050 | 0.1 MiB | 147 ms | 0.9 MiB | 5.7 ms |
| carrier | 6300 | 0.5 MiB | 1.2 s | 5.0 MiB | 29 ms |
| d2c | 18900 | 1.5 MiB | 3.7 s | 14.9 MiB | 83 ms |
| enterprise | 126000 | 10.1 MiB | 32.3 s | 101.4 MiB | 537 ms |

Run the same ladder through this harness with `--shape logistics` or `--shape snapshot` and the `--rules` presets in Run.
