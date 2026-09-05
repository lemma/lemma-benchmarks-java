# How large is a logistics rating system? (lookup rows / rate facts)

**Scope:** Estimate how many discrete **lookup rows / tariff cells / catalog facts** a real logistics or multi-carrier shipping system may hold.  
**Not in scope:** Lemma Specs, engine limits, or benchmark generators.

**Method:** Prefer published carrier tables and named industry sources. Where a number is a product of published dimensions, the formula is shown. Where a source gives a range, the range is kept (not rounded into a “nice” target).

---

## 1. What we are counting

A large logistics system does not store “one rule per package on an order.” It stores **catalogs** that rating reads at runtime:

| Catalog type | What one row usually is |
|--------------|-------------------------|
| Parcel rate card | One **zone × weight-break × service** price cell |
| Zone chart | One **origin × destination ZIP3 × service → zone** assignment |
| LTL / TL contract | One **lane** (origin–dest) rate, often with class/weight tiers |
| Accessorial schedule | One named surcharge (flat or conditional) |
| Fuel index table | One **fuel-price bracket → surcharge %** row |
| SKU / commodity attributes | One SKU’s shipping class, hazmat, dims, etc. |
| NMFC commodity book | One commodity item / density rule (carrier tariff input) |

**Packages on a single shipment** (after cartonization) are typically **1–20**, not tens of thousands. That is runtime fan-out, not catalog size.

---

## 2. US geography keys used in rating

| Fact | Number | Source |
|------|-------:|--------|
| USPS 3-digit ZIP prefixes (total assigned) | **875** | MapOfZipCodes summary of USPS 3-digit codes ([mapofzipcodes.com](https://www.mapofzipcodes.com/blog/how-many-us-area-codes)) |
| Of which used for geographic distribution | **~680** | Same |
| Five-digit ZCTAs tracked in ACS corpus | **33,099** | PlainZIP / Census ACS ZCTA corpus ([plainzip.com/statistics](https://plainzip.com/statistics)) |
| Common industry shorthand for “~900 ZIP3s” | **~900** | Used widely in zone-chart tooling; treat as ≈875–900 |

Carrier zone charts almost always key **destination by ZIP3** (first three digits), not full ZIP5.

---

## 3. Parcel: one published Ground-style rate matrix

**UPS Ground (contiguous US), published structure:**

| Dimension | Value | Notes / source |
|-----------|------:|----------------|
| Zones | **7** | Zones **2–8** (no Zone 1 on published Ground structure) — UPS Ground explainers / rate guides |
| Weight breaks | **150** | **1–150 lb**; domestic parcel max actual weight **150 lb** — UPS Rate and Service Guide / tariff materials |
| **Cells in one Ground matrix** | **1,050** | `7 × 150` |

FedEx Ground / Home Delivery retail materials use the same **zone × weight** pattern (zones 2–8, per-lb breaks). Same order of magnitude per service.

---

## 4. Parcel: services on one carrier (zone chart header)

Official UPS origin zone chart text files (example: origin ZIP prefix **630**, [ups.com zone-txt](https://www.ups.com/media/us/currentrates/zone-txt/630.txt)) list **six** services in the header:

1. Ground  
2. 3 Day Select  
3. 2nd Day Air  
4. 2nd Day Air A.M.  
5. Next Day Air Saver  
6. Next Day Air  

**If** each of those six services is priced with a full Ground-sized zone×weight matrix:

| Aggregate | Formula | Count |
|-----------|---------|------:|
| One carrier, full domestic suite | `6 × 1,050` | **6,300** rate cells |

Caveat: some air services use different zone encodings on the chart (numeric codes like 105/205/…); the **cell count** still scales with weight×zone columns on the **rate** card, not only the zone chart.

---

## 5. Zone charts (distance / transit lookup)

| Fact | Number | Source |
|------|-------:|--------|
| UPS origin charts in a complete download set | **~894** | [hyprlab/zonechart](https://github.com/hyprlab/zonechart) (downloads official per-origin UPS workbooks) |
| Dest ZIP3 keys per chart | **~875–900** | USPS 3-digit set / chart practice |
| Services per chart | **6** | UPS zone-txt headers |
| **Zone assignments, one ship-from origin** | `~900 × 6` ≈ **5,400** | Product of published dimensions |
| **Zone assignments, all UPS origins × all dest × 6 services** | `894 × 900 × 6` ≈ **4.8 million** | Product; this is the **carrier’s national zone dataset**, not what one merchant loads |

A **single warehouse / ship-from** needs on the order of **thousands** of zone rows (one origin chart). A **carrier or multi-node network** that materializes every origin chart approaches **millions** of zone facts.

Charts are often stored as **ZIP3 ranges** (e.g. `010-016`), so physical file row counts can be lower than 900 while still covering all prefixes.

---

## 6. Multi-carrier and contracts

| Fact | Number | Source |
|------|-------:|--------|
| Carriers a typical DTC brand rate-shops day-to-day | **~2–3** | Multi-carrier TMS guidance (e.g. Pango: most DTC brands do well with two or three) |
| Carrier **contracts** at enterprise shippers | **20–500** | [Shipsy — rate card digitization](https://www.shipsy.ai/insights/rate-card-digitization-audit): “Enterprise shippers run 20-500 carrier contracts across lanes, modes, service levels, and regions.” |

**Illustrative products** (full national suite assumed per contract — upper-bound style):

| Scenario | Formula | Rate cells |
|----------|---------|----------:|
| 3 carriers × full 6-service suite | `3 × 6,300` | **18,900** |
| 20 contracts × full suite (Shipsy low end) | `20 × 6,300` | **126,000** |
| 500 contracts × full suite (Shipsy high end) | `500 × 6,300` | **3,150,000** |

**Important:** Many contracts are **not** full national parcel matrices. LTL/FTL contracts are often **lane files** (see below). The 126k–3.1M figures are “if every contract were a full UPS-style suite,” useful as a ceiling for parcel-heavy books, not a universal constant.

---

## 7. LTL / freight lanes (separate from parcel matrices)

| Fact | Number | Source |
|------|-------:|--------|
| Active lanes where “lane-level management” becomes standard | **~50** | [Warp — high-volume freight](https://www.wearewarp.com/enterprise/high-volume-freight) |
| Lane network needing portfolio tooling | **~50–200** | Same |
| Example broker/network marketed LTL lane count | **6,800+** | Warp LTL network marketing |
| Another Warp network figure | **1,500+** Warp LTL lanes | Same site (different product blurb) |
| Hybrid RFP practice | Top **~20** lanes by spend via RFP; rest bilateral | Warp freight negotiation guide |

Each **lane row** may itself expand into class × weight-break × accessorial overlays. Even without expansion, **hundreds to low tens of thousands** of lane rows are normal for serious LTL programs; broker networks advertise **thousands**.

---

## 8. NMFC / commodity classification

| Fact | Number | Source |
|------|-------:|--------|
| Standard LTL freight **classes** | **18** (50–500) | NMFTA / UPS / FedEx Freight glossaries |
| Commodity items reclassified in NMFC Docket 2025-1 | **~2,000** items (~30% of LTL freight) | ParcelPath summary of NMFTA Docket 2025-1 |

A full NMFC commodity book is far larger than 18 classes (classes are buckets; commodity items map into them). Treat **thousands of commodity rows** as the classification catalog scale when density/commodity lookup is in-system.

---

## 9. Accessorials and fuel (small tables, high invoice impact)

**Accessorials:** published parcel/freight schedules list on the order of **tens** of named surcharges (residential, DAS, additional handling, large package, oversize, address correction, signature, Saturday, etc.) — not thousands. Industry writeups note accessorials can be a large **share of spend**, but the **row count** is small ([ParcelPath accessorials](https://parcelpath.com/freight-ltl/freight-freight-quotes-and-costs/accessorial-charges/)).

**Fuel surcharge:**

- Adjusted **weekly** from EIA diesel / jet indices ([UPS fuel surcharges](https://www.ups.com/us/en/support/shipping-support/shipping-costs-rates/fuel-surcharges.page)).
- Published bracket tables are on the order of **~10–40 rows** visible at a time (price band → %), with extension rules beyond the printed range (UPS fuel flyer PDFs).
- Operational systems may also keep **~13 weeks** (90-day history) of weekly percentages — still **O(10²)**, not O(10⁵).

---

## 10. SKU / product shipping attributes (merchant-side catalog)

If shipping class, hazmat, dims, or “must ship alone” live **per SKU**, catalog size follows product master data:

| Scale discussed in ops / PIM literature | Order of magnitude | Source examples |
|----------------------------------------|-------------------:|-----------------|
| High-SKU ops threshold | **50,000+** SKUs | [nventory high-SKU guide](https://nventory.io/bv/blog/high-sku-catalog-operations-guide) |
| Global brand / dealer catalogs | **10,000–200,000+** | Inriver case mentions (e.g. 20k+, 200k+) |
| Large wholesale / B2B | **100,000–800,000+** active SKUs | Inriver / enterprise commerce writeups |
| Extreme commerce catalogs | **~1,000,000+** SKUs | Enterprise WooCommerce case claims (e.g. ~1.2M) |

Not every SKU needs a unique rating rule; many map to a small set of **shipping classes**. Worst case for a rules/lookup engine: **one shipping-attribute row per SKU** → **10⁵–10⁶** rows at large merchants.

---

## 11. Rating pipeline depth (stages, not rows)

Typical deterministic flow in TMS / rate-engine writeups:

1. Validate units / dims  
2. DIM weight  
3. Billable weight = max(actual, DIM)  
4. Zone lookup  
5. Base rate from matrix / lane  
6. Accessorials  
7. Fuel %  
8. Contract discount / minimums  
9. Tax / total  
10. (Optional) compare candidates / select carrier  

→ about **10–20 sequential stages**. This is **deep but short**. It does **not** produce 10⁵ rules by itself.

---

## 12. Putting it together: catalogs in a “large” system

Counts below are **rows / cells**, counting lookup tables in full. Ranges reflect source ranges and whether contracts are full matrices vs lane files.

### A. Single national parcel carrier, one ship-from (merchant loads one origin)

| Catalog | Approx rows |
|---------|------------:|
| Zone chart (one origin × ZIP3 × 6 services) | **~5,400** |
| Rate cards (6 services × 1,050) | **~6,300** |
| Accessorials + fuel brackets | **~50–100** |
| Pipeline / eligibility rules | **~20–100** |
| **Subtotal** | **~12,000–12,000+** |

### B. Multi-carrier DTC (3 carriers, one ship-from)

| Catalog | Approx rows |
|---------|------------:|
| Zone charts × 3 (if not shared) | **~16,000** |
| Rate cells `3 × 6,300` | **~19,000** |
| Accessorials / fuel / pipeline | **~100–300** |
| **Subtotal** | **~35,000–40,000** |

### C. Enterprise shipper (Shipsy-scale contracts)

| Catalog | Approx rows |
|---------|------------:|
| Contracts | **20–500** (meta) |
| If each is a full national parcel suite | **126,000 – 3,150,000** rate cells |
| Plus LTL lane files | **50 – 200** shipper lanes typical; broker nets **1,500 – 7,000+** |
| Plus SKU shipping attributes (optional) | **10⁴ – 10⁶** |
| Plus full multi-origin zone materialization | up to **~10⁵ – 10⁶** per carrier network; **~10⁷** if all origins × carriers |

### D. Carrier / platform that stores the full UPS-like zone universe

| Catalog | Approx rows |
|---------|------------:|
| All origins × dest ZIP3 × 6 services | **~4.8 million** |

---

## 13. Bottom line

| Question | Answer from sources |
|----------|---------------------|
| How big is **one** Ground rate table? | **~1,050** cells |
| How big is **one carrier’s** domestic service suite? | **~6,300** cells (6 × 1,050) |
| How big is **one origin** zone chart? | **~5,400** service×ZIP3 facts |
| How big is a **3-carrier** merchant rating brain? | **~O(10⁴)** rate+zone rows |
| How big can **enterprise contract books** get if full matrices? | **~10⁵ – 10⁶+** rate cells (20–500 contracts) |
| How big is the **national zone dataset**? | **~10⁶ – 10⁷** assignments |
| How big can **SKU shipping attribute** tables get? | **~10⁵ – 10⁶** at large catalogs |
| How many **pipeline** steps? | **~10–20** |
| How many **packages** on one order? | **~1–20** (not the catalog) |

**Large logistics systems are large because of lookup catalogs (rates, zones, lanes, SKUs, contracts), not because a single shipment has thousands of packages.**

This repo’s Java harness uses those parcel ladder sizes as `--shape logistics --rules` presets: **1050** (ground), **6300** (carrier), **18900** (d2c), **126000** (enterprise). See the repo README for how to run load/eval timings.

---

## 14. Primary references

- UPS Ground structure / 150 lb max / zones 2–8 — UPS rate guides and Ground explainers (e.g. ClickPost UPS Ground overview; UPS Rate and Service Guide PDFs).  
- UPS origin zone chart services — [UPS zone-txt example (630)](https://www.ups.com/media/us/currentrates/zone-txt/630.txt).  
- UPS zone chart corpus size — [hyprlab/zonechart](https://github.com/hyprlab/zonechart) (~894 origins).  
- ZIP3 counts — USPS 3-digit discussions ([MapOfZipCodes](https://www.mapofzipcodes.com/blog/how-many-us-area-codes)); ZCTA count [PlainZIP](https://plainzip.com/statistics).  
- Enterprise contract counts — [Shipsy rate-card digitization](https://www.shipsy.ai/insights/rate-card-digitization-audit) (20–500 contracts).  
- LTL lane scale — [Warp high-volume freight / LTL](https://www.wearewarp.com/enterprise/high-volume-freight) (50–200 active lanes; network lane marketing figures).  
- NMFC — 18 classes; Docket 2025-1 ~2,000 commodities reclassified (ParcelPath / NMFTA reporting).  
- Fuel — [UPS Fuel Surcharges](https://www.ups.com/us/en/support/shipping-support/shipping-costs-rates/fuel-surcharges.page) + fuel flyer bracket tables.  
- SKU catalog scale — high-SKU ops / PIM case literature (50k+, 100k+, 800k+, ~1M+).

---

## 15. Honesty labels

| Label | Meaning |
|-------|---------|
| **Measured** | Stated directly by a primary or secondary source (e.g. 150 lb max, 6 services on chart, 20–500 contracts, ~894 origins). |
| **Derived** | Arithmetic from measured dimensions (e.g. 7×150=1,050; 6×1,050=6,300; 894×900×6≈4.8M). |
| **Illustrative ceiling** | Assumes every contract is a full national suite; real books mix thinner lane cards — use as upper bound, not average. |
