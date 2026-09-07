# Java SDK benchmark results

Measured 2026-09-07 on this harness against [`com.lemmabase:lemma-engine` 0.9.9](https://central.sonatype.com/artifact/com.lemmabase/lemma-engine/0.9.9) from Maven Central (not a locally installed JAR).

Machine: Intel Core i9-9900K (8C/16T, 3.6 GHz, turbo 5.0 GHz), 62 GiB RAM, Ubuntu 24.04.3 LTS, OpenJDK 21.0.12. Heap `-Xmx2g` for micro shapes; `-Xmx8g` for logistics, replan, and snapshot. Medians over `--loads 3`. Load/eval also used `--warmup 10 --evals 50`.

These are Java SDK timings (`Engine.load` / `run` / `update` / `snapshot` / `fromSnapshot`). They are not the engine’s native `cargo benchmarks engine` table in the [README](README.md#measured-logistics-ladder).

## What the columns mean

**Units:** all timings are **milliseconds**. `src_bytes` / `snap_bytes` are bytes.

### load

Cold compile of Spec text into a new `Engine`. Each trial constructs a fresh engine, times `engine.load(source)`, then closes the engine. The reported value is the **median** of `--loads` trials.

This is parse + plan + bind of the whole workspace, not a query. Enterprise logistics (~10 MiB of Spec) is the expensive case.

### eval

One `engine.run(...)` of the terminal rule **after** the Spec is already loaded. Timed separately from load.

Load/eval shapes discard `--warmup` runs, then time `--evals` runs on the same loaded engine. `eval_med_ms` is the median; `eval_p95_ms` is the 95th percentile of those timed runs.

Replan’s `eval_mid_ms` is a **single** `shop.cheapest` after the mid-Spec update (base still V1), not a warmup/eval loop.

### upd

`Engine.update` on an already-loaded workspace: replace one Spec and replan dependents. Only the **replan** shape.

Workspace: `shop` uses `quote` uses `base_rates`. Sibling `unrelated` is a large catalog off that chain. `--rules` is the arm count on `base_rates` / `unrelated`.

| Column | Call | What it measures |
|--------|------|------------------|
| `upd_mid_ms` | `update(quote)` V2 | Replan `quote` and `shop`. Fat `base_rates` stays loaded. Primary “hot swap a thin mid Spec” number. |
| `upd_base_ms` | `update(base_rates)` | Replan the fat catalog and everything that uses it. |
| `upd_ident_ms` | `update(quote)` with identical source | Fast path when bytes did not change. |
| `upd_unrel_ms` | `update(unrelated)` | Replace a Spec with no dependents on the `shop` chain. |

### snap

`Engine.snapshot()` after a cold load. Serializes the loaded engine to an opaque byte array. `snap_med_ms` is that call; `snap_bytes` is the artefact size.

A snapshot is a restore artefact, not Spec text. Enterprise is ~101 MiB of snapshot vs ~10 MiB of source.

### restore

`Engine.fromSnapshot(bytes)` into a **new** engine. Median of `--loads` trials. Does **not** re-parse Spec text; it reconstructs from the snapshot.

Compare `restore_med_ms` to `load_med_ms` for the same workspace: restore is the “restart from a saved engine” path.

### Other columns

| Column | Meaning |
|--------|---------|
| `src_bytes` | UTF-8 length of generated Spec text |
| `ok` | Sanity: expected numeric total after replan updates, or `rate_shop.cheapest` is a `RuleResult.Number` after snapshot restore |
| `explain` | Whether `RunRequest.explain` was on (`n` in this run) |

## Micro (deep / wide / unless)

```bash
./mvnw -q exec:java -Dexec.args="--shape deep,wide,unless --rules 100,1000 --loads 3 --warmup 10 --evals 50"
```

| shape | rules | src_bytes | load_med_ms | eval_med_ms | eval_p95_ms | explain |
|-------|------:|----------:|------------:|------------:|------------:|---------|
| deep | 100 | 1816 | 1.187 | 0.096 | 0.243 | n |
| deep | 1000 | 19817 | 13.259 | 0.511 | 0.749 | n |
| wide | 100 | 5457 | 2.692 | 0.361 | 0.655 | n |
| wide | 1000 | 60357 | 37.460 | 2.739 | 3.686 | n |
| unless | 100 | 2534 | 0.775 | 0.029 | 0.057 | n |
| unless | 1000 | 26836 | 7.521 | 0.035 | 0.068 | n |

`deep` is a rule chain of length `--rules`. `wide` is data + pair rules + a fold. `unless` is one rule with `--rules` arms.

## Logistics load / eval

```bash
./mvnw -q exec:java -Dexec.jvmArgs="-Xmx8g" \
  -Dexec.args="--shape logistics --rules 1050,6300,18900,126000 --loads 3 --warmup 10 --evals 50"
```

Terminal rule: `rate_shop.cheapest`.

| rules | profile | src_bytes | load_med_ms | eval_med_ms | eval_p95_ms |
|------:|---------|----------:|------------:|------------:|------------:|
| 1050 | ground (1 carrier × Ground) | 90804 | 148.734 | 0.189 | 0.300 |
| 6300 | carrier (1 × 6 services) | 530797 | 1251.903 | 0.598 | 0.980 |
| 18900 | d2c (3 × 6) | 1587119 | 3814.840 | 1.915 | 2.648 |
| 126000 | enterprise (20 × 6) | 10569194 | 28204.639 | 15.708 | 18.293 |

Enterprise cold load is ~28 s. Enterprise eval median is ~16 ms.

## Replan

```bash
./mvnw -q exec:java -Dexec.jvmArgs="-Xmx8g" \
  -Dexec.args="--shape replan --rules 1050,6300,18900,126000 --loads 3"
```

All rows `ok=y`.

| rules | src_bytes | load_med_ms | upd_mid_ms | upd_base_ms | upd_ident_ms | upd_unrel_ms | eval_mid_ms |
|------:|----------:|------------:|-----------:|------------:|-------------:|-------------:|------------:|
| 1050 | 75765 | 51.880 | 30.147 | 45.263 | 0.051 | 7.592 | 19.447 |
| 6300 | 474765 | 356.633 | 201.900 | 304.196 | 0.063 | 50.185 | 0.332 |
| 18900 | 1467980 | 1093.692 | 619.504 | 939.490 | 0.076 | 184.889 | 0.771 |
| 126000 | 10139995 | 9368.305 | 4397.080 | 6690.413 | 0.105 | 3049.221 | 4.384 |

At enterprise, replacing the thin mid Spec (~4.4 s) is cheaper than reloading the workspace (~9.4 s) or replacing `base_rates` (~6.7 s). Identical-source `update(quote)` stays ~0.1 ms.

## Snapshot

```bash
./mvnw -q exec:java -Dexec.jvmArgs="-Xmx8g" \
  -Dexec.args="--shape snapshot --rules 126000 --loads 3"
```

Logistics enterprise workspace. `ok=y` (`rate_shop.cheapest` is a number after restore).

| rules | src_bytes | load_med_ms | snap_med_ms | snap_bytes | restore_med_ms |
|------:|----------:|------------:|------------:|-----------:|---------------:|
| 126000 | 10569194 | 26576.667 | 300.662 | 106347707 | 553.834 |

Cold load ~27 s; snapshot ~0.30 s; restore ~0.55 s. Restore is about 50× faster than re-parsing the same Spec.
