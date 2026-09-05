# How a large logistics rating system might look in Lemma Spec syntax

**Scope:** Structural estimate only — map the catalogs from [`logistics-system-lookup-scale.md`](logistics-system-lookup-scale.md) onto **Lemma Spec language** (Specs, Data, Rules, `unless`, `uses`).  

**Explicitly out of scope here:** engine performance numbers (see repo README Measured), JNI packaging, and how the Java harness generates Specs.

Numbers for catalog size come from that research doc. This file answers: *if you wrote it as Lemma, what would the text shape be?* The harness loads those ladder sizes; timings are in the README.
---

## 1. Lemma constructs ↔ logistics catalogs

| Logistics artifact | Natural Lemma encoding | What scales |
|--------------------|------------------------|-------------|
| Zone × weight × service **rate cell** | One `unless` arm (or one tiny rule) whose condition matches zone+weight (+ service) and whose `then` is the money | **~1,050–10⁶** arms/rules |
| ZIP3 → zone | `unless dest_zip3 is "…" then <zone>` (or `is` / equality on number) | **~900–5,400+** arms per origin×services |
| Accessorial | Small `rule` + few `unless` | **~10–40** rules |
| Fuel bracket table | `rule fuel_pct: …` with `unless diesel >= … then …%` | **~10–40** arms |
| Rating pipeline stage | Named `rule` depending on prior rule | **~10–20** rules (fixed depth) |
| Carrier×service quote | One `rule quote_…` then a fold/`min`-style aggregator | **~6–18** quotes for 1–3 carriers |
| Contract / carrier book | Separate Spec per contract or carrier, composed with `uses` | **20–500** Specs |
| SKU shipping class | Spec of SKU rows, or `unless sku is "…" then class` | **10⁴–10⁶** arms/rows |
| Lane (LTL) | One rule per lane, or unless on origin/dest pair | **50–7,000+** |
| Package on one order | `data` + thin rules; **not** the big catalog | **1–20** |

Lemma has no native “matrix” type. A published rate card becomes either:

1. **One rule, huge `unless` chain** (last-matching-wins tariff), or  
2. **Many small rules** (one per cell / lane / SKU), composed into a rating Spec.

Both are valid Spec shapes; counts below treat **one lookup row ≈ one `unless` arm or one `rule`**.

---

## 2. Tiny idiomatic fragments (shape only)

### 2.1 One Ground-style cell (zone × weight → money)

```lemma
spec ups_ground_rates

data zone: number
data billable_lb: number

rule base_rate: 0
  unless zone is 2 and billable_lb >= 1  then 9.85
  unless zone is 2 and billable_lb >= 2  then 10.50
  # … continue for all zone×lb cells …
  unless zone is 8 and billable_lb >= 150 then 999.00
```

**Arms for one Ground card:** `7 × 150 =` **1,050** `unless` lines under one `rule`.

### 2.2 ZIP3 → zone (one origin, one service)

```lemma
spec ups_origin_630_ground_zones

data dest_zip3: text

rule ground_zone: 0
  unless dest_zip3 is "004" then 5
  unless dest_zip3 is "005" then 5
  unless dest_zip3 is "010" then 5
  # … ~875–900 destination prefixes …
```

**Arms:** **~900** for one service; **~5,400** if six services are separate rules (or six Specs).

### 2.3 Accessorial (small)

```lemma
spec parcel_accessorials

data is_residential: boolean
data is_das: boolean
data billable_lb: number

rule residential_fee: 0
  unless is_residential then 6.50

rule das_fee: 0
  unless is_das and is_residential then 4.20
  unless is_das and not is_residential then 2.80

rule ahs_weight_fee: 0
  unless billable_lb > 50 then 30
```

**Rules:** tens, not thousands.

### 2.4 Deep rating pipeline (fixed)

```lemma
spec rate_shipment

uses zones: ups_origin_630_ground_zones
uses rates: ups_ground_rates
uses acc: parcel_accessorials
uses fuel: ups_ground_fuel

data length_in: number
data width_in: number
data height_in: number
data actual_lb: number
data dest_zip3: text
data is_residential: boolean
data is_das: boolean
data diesel_usd: number
data discount_pct: percent

rule dim_lb: (length_in * width_in * height_in) / 139
rule billable_lb: actual_lb
  unless dim_lb > actual_lb then dim_lb

rule zone: zones.ground_zone
rule base: rates.base_rate
rule accessorials: acc.residential_fee + acc.das_fee + acc.ahs_weight_fee
rule pre_fuel: base + accessorials
rule with_fuel: pre_fuel + (pre_fuel * fuel.fuel_pct)
rule with_discount: with_fuel - (with_fuel * discount_pct)
rule total: with_discount
```

**Pipeline rules:** **~8–15** local rules + `uses` of catalogs. Depth is the dependency chain `dim → billable → zone → base → … → total`, not catalog size.

### 2.5 Wide rate-shop (few candidates)

```lemma
spec rate_shop

uses ground: quote_ups_ground
uses twoday: quote_ups_2da
uses next: quote_ups_nda
uses fxg: quote_fedex_ground
# … ~6–18 service quotes …

rule cheapest: ground.total
  unless twoday.total < cheapest then twoday.total
  # … or explicit min-fold over named quotes …
```

**Wide axis:** **O(10)** Specs/rules (carriers × services), not O(catalog).

### 2.6 Enterprise: one Spec per contract

```lemma
spec contract_ups_acct_12345
"""Negotiated UPS daily rates effective 2026-01-01"""
# 6,300 unless arms / cells for full suite

spec contract_fedex_acct_99
# …

spec shipper_rating

uses ups: contract_ups_acct_12345
uses fedex: contract_fedex_acct_99
# … 20–500 uses lines in a large book …
```

**Specs:** **20–500** contract Specs (Shipsy range), each holding thousands of tariff cells.

---

## 3. Size estimate if the research catalogs were Spec text

Treat **1 lookup row = 1 `unless` arm or 1 `rule`**. Pipeline/`uses` overhead is negligible next to catalogs.

### 3.1 One Ground matrix Spec

| Piece | Lemma shape | Count |
|-------|-------------|------:|
| `rule base_rate` + arms | one rule, **1,050** `unless` | **1,050** |
| Supporting `data` | zone, billable_lb | **2** |

**≈ 1,050 tariff lines** in one Spec.

### 3.2 One carrier full domestic suite

| Piece | Count |
|-------|------:|
| 6 services × 1,050 cells | **6,300** arms (6 rules × 1,050, or 1 flat rule × 6,300) |
| Zone chart one origin × 6 services | **~5,400** arms |
| Accessorials + fuel | **~50–100** |
| **Spec lines of “business fact”** | **~12,000** |

Composition: e.g. Specs `ups_zones_origin_630`, `ups_rates_ground`, … `ups_rates_nda`, `ups_accessorials`, then `rate_ups` with `uses` + **~12** pipeline rules.

### 3.3 Three-carrier DTC rating brain

| Piece | Count |
|-------|------:|
| Rate cells `3 × 6,300` | **18,900** |
| Zone charts `3 × ~5,400` (if not shared) | **~16,200** |
| Accessorials / fuel / pipeline / quotes | **~100–300** |
| **Total lookup-ish Lemma lines** | **~35,000–40,000** |

Plus **~18** `quote_*` rules and **1** `cheapest` fold (wide, small).

### 3.4 Enterprise contract book (ceiling: every contract = full suite)

| Piece | Count |
|-------|------:|
| 20 contracts × 6,300 | **126,000** rate arms |
| 500 contracts × 6,300 | **3,150,000** rate arms |
| Plus lane Specs (50–200 shipper / 1.5k–7k broker) | **+50 … +7,000** rules |
| Plus SKU shipping Spec | **+10⁴ … +10⁶** arms/rules |

Lemma layout:

```
repositories/ or many files:
  contracts/ups_….lemma      # each file: one spec, thousands of unless arms
  contracts/fedex_….lemma
  lanes/ltl_primary.lemma    # 50–200 rule lane_…
  catalog/sku_ship_class.lemma
  rating/rate_shipment.lemma # uses + deep pipeline only
```

### 3.5 Carrier-scale zone universe (all origins)

| Piece | Lemma shape | Count |
|-------|-------------|------:|
| 894 origins × ~900 ZIP3 × 6 services | Spec per origin, or one mega Spec | **~4.8 million** arms/rules |

In Lemma terms that is **~894 Specs** (`ups_zones_origin_###`) averaging **~5,400** arms each, composed by a router Spec that picks the origin chart — still **~4.8M** facts total.

### 3.6 SKU shipping-class catalog

```lemma
spec sku_shipping

data sku: text

rule ship_class: "standard"
  unless sku is "SKU-000001" then "oversize"
  unless sku is "SKU-000002" then "hazmat"
  # … one arm per SKU that needs an override …
```

| Merchant scale | Arms / rows |
|----------------|------------:|
| Mid high-SKU | **~50,000** |
| Large wholesale | **~10⁵–10⁶** |

---

## 4. How the three stress “shapes” appear (conceptually)

Not a bench design — just where deep / wide / unless show up in this domain:

| Shape | In a logistics Lemma model | How large? |
|-------|----------------------------|------------|
| **unless** | Rate cards, zone charts, fuel brackets, SKU overrides, lane matches | **Dominates** — **10³ → 10⁶+** arms |
| **wide** | Rate-shop across carrier×service quotes; multi-parcel sum | **Small** — **O(10)** quotes; **O(1–20)** packages |
| **deep** | `dim → billable → zone → base → accessorials → fuel → discount → total` | **Fixed** — **~10–20** rule chain |

So a “realistic large system in Lemma” is mostly **enormous unless/catalog Specs** (or enormous numbers of tiny rules) hanging off a **short deep pipeline**, with a **narrow wide** rate-shop — not 500 deep package clones.

---

## 5. Worked “whole system” sketch (3-carrier DTC, one ship-from)

```
ups_zones_630.lemma          ~5,400 unless arms
ups_rates_*.lemma            ~6,300 unless arms  (6 services)
fedex_zones_….lemma          ~5,400
fedex_rates_*.lemma          ~6,300
regional_rates.lemma         ~6,300
accessorials.lemma           ~30 rules
fuel_ground.lemma            ~30 unless arms
fuel_air.lemma               ~30 unless arms
rate_ups_ground.lemma        uses + ~12 pipeline rules
rate_ups_2da.lemma           …
rate_fedex_ground.lemma      …
rate_shop.lemma              uses quotes + cheapest
sku_ship_class.lemma         optional 10k–100k unless arms
```

| Rollup | Approx Lemma “rows” |
|--------|--------------------:|
| Without SKU catalog | **~35k–40k** |
| With 50k SKU overrides | **~85k–90k** |
| With 500k SKU overrides | **~0.5M+** |

---

## 6. Worked “whole system” sketch (enterprise ceiling)

```
contracts/   × 20 … 500 Specs × ~6,300 arms each
lanes/       × 50 … 7,000 lane rules
zones/       × 1 … 894 origin Specs × ~5,400 arms
sku/         × 10⁴ … 10⁶
rating/      × ~12 pipeline + ~O(10) quotes
```

| Rollup | Approx Lemma “rows” |
|--------|--------------------:|
| 20 full parcel contracts only | **~126,000** |
| 20 contracts + 5k lanes + 50k SKUs | **~180,000** |
| 500 full contracts (ceiling) | **~3.2M** |
| + full national zone universe | **+~4.8M** |

---

## 7. Bench presets and what we measured

This repo’s `--shape logistics` generator uses the §3 cell counts as presets:

| `--rules` | Profile | Rough match to §3 |
|----------:|---------|-------------------|
| **1050** | ground | one Ground matrix (§3.1) |
| **6300** | carrier | one carrier domestic suite (§3.2 rates) |
| **18900** | d2c | 3-carrier rate cells (§3.3) |
| **126000** | enterprise | 20 full contracts (§3.4 low end) |

Cold load and eval timings for these presets live in the repo [`README.md`](../README.md). Enterprise (126000 rate cells, ~10 MB source) is the top ladder step the Java harness loads.

Short readable Spec (pipeline + a few `unless` arms, not the full catalog): [`examples/quote_ups_ground.lemma`](../examples/quote_ups_ground.lemma).

---

## 8. What this is *not*

- Not published UPS / FedEx prices — generated prices are synthetic.  
- Not a claim that one `unless` arm is the only encoding (many `rule cell_z2_w5: 10.50` would be the same row count).  
- Not sizing from “packages on an order.”  

It is: **if the real catalogs were written as Lemma Specs, the text would be dominated by unless/tariff/zone/SKU rows at the scales in the research doc, behind a short `uses`+pipeline spine** — and those ladder sizes are what the Java harness loads.

---

## 9. Pointers

- Catalog sizes and sources: [`logistics-system-lookup-scale.md`](logistics-system-lookup-scale.md)  
- Lemma `unless` / `uses` idioms: Lemma docs *Conditional logic*, *Composing Specs*  
- Timing: [`README.md`](../README.md)
