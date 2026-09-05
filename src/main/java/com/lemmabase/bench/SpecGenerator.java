package com.lemmabase.bench;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Procedural Lemma Spec builders for deep / wide / unless / logistics shapes. */
public final class SpecGenerator {
  /**
   * Generated fixture.
   *
   * @param shape generator name
   * @param rules CLI scale N (shape-specific)
   * @param limitScale size hint for {@link BenchLimits#forSize(int)}
   * @param specName terminal Spec name for {@code Engine.run}
   * @param source concatenated Lemma source (may contain many Specs)
   * @param data run inputs
   * @param terminal terminal rule name
   */
  public record GeneratedSpec(
      String shape,
      int rules,
      int limitScale,
      String specName,
      String source,
      Map<String, Object> data,
      String terminal) {
    public int sourceBytes() {
      return source.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }
  }

  /** Research-derived: UPS Ground zones 2–8. */
  public static final int LOGISTICS_ZONES = 7;
  /** Research-derived: 1–150 lb breaks. */
  public static final int LOGISTICS_WEIGHTS = 150;
  /** Research-derived: cells in one Ground matrix. */
  public static final int LOGISTICS_GROUND_CELLS = LOGISTICS_ZONES * LOGISTICS_WEIGHTS; // 1050
  /** Research-derived: services on UPS zone chart header. */
  public static final int LOGISTICS_SERVICES = 6;
  /** Research-derived: ~900 US ZIP3 prefixes. */
  public static final int LOGISTICS_ZIP3_COUNT = 900;
  /** Research-derived: D2C multi-carrier count. */
  public static final int LOGISTICS_D2C_CARRIERS = 3;
  /** Research-derived: Shipsy enterprise low contract count. */
  public static final int LOGISTICS_ENTERPRISE_CONTRACTS = 20;

  private static final String[] SERVICE_IDS = {
    "ground", "select_3day", "air_2day", "air_2day_am", "nda_saver", "nda"
  };

  private SpecGenerator() {}

  /**
   * @param shape deep, wide, unless, or logistics
   * @param rules scale parameter N
   */
  public static GeneratedSpec generate(String shape, int rules) {
    Objects.requireNonNull(shape, "shape");
    if (rules < 1) {
      throw new IllegalArgumentException("--rules must be >= 1, got " + rules);
    }
    return switch (shape.toLowerCase(Locale.ROOT)) {
      case "deep" -> deep(rules);
      case "wide" -> wide(rules);
      case "unless" -> unless(rules);
      case "logistics" -> logistics(rules);
      case "replan" -> replan(rules).asGeneratedSpec();
      default -> throw new IllegalArgumentException(
          "unknown --shape '"
              + shape
              + "' (expected deep|wide|unless|logistics|replan)");
    };
  }

  /**
   * Multi-Spec fixture for scoped-replan benches on a fat catalog.
   *
   * <p>{@code uses} chain tip → mid → base: {@code shop → quote → base_rates}. Sibling {@code
   * unrelated} same arm count. Tree sense: tip has no dependents; base has no dependencies. Primary
   * case: replace mid {@code quote} so dependents ({@code shop}) replan while fat {@code
   * base_rates} is kept. {@code --rules} is catalog arm count (min Ground size 1050).
   */
  public record ReplanFixture(
      int rules,
      int limitScale,
      String fullSource,
      String baseV1,
      String baseV2,
      String quoteV1,
      String quoteV2,
      String unrelatedV1,
      String unrelatedV2,
      Map<String, Object> data,
      String terminalSpec,
      String terminalRule,
      java.math.BigDecimal expectedAfterMidV2,
      java.math.BigDecimal expectedAfterBaseV2) {
    GeneratedSpec asGeneratedSpec() {
      return new GeneratedSpec(
          "replan", rules, limitScale, terminalSpec, fullSource, data, terminalRule);
    }

    int sourceBytes() {
      return fullSource.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }
  }

  /**
   * Fat catalog for scoped-replan benches. {@code n} is unless-arm count on {@code base_rates} and
   * {@code unrelated}. Small N is rejected: mid update cost only matters when the kept catalog is
   * large (use logistics presets 1050 / 6300 / 18900 / 126000).
   *
   * @param n unless arms on base_rates and unrelated
   */
  public static ReplanFixture replan(int n) {
    if (n < LOGISTICS_GROUND_CELLS) {
      throw new IllegalArgumentException(
          "replan --rules must be >= "
              + LOGISTICS_GROUND_CELLS
              + " (Ground cell count); small catalogs do not demonstrate scoped replan. "
              + "Use "
              + LOGISTICS_GROUND_CELLS
              + ", "
              + (LOGISTICS_GROUND_CELLS * LOGISTICS_SERVICES)
              + ", "
              + (LOGISTICS_GROUND_CELLS * LOGISTICS_SERVICES * LOGISTICS_D2C_CARRIERS)
              + ", or "
              + (LOGISTICS_GROUND_CELLS * LOGISTICS_SERVICES * LOGISTICS_ENTERPRISE_CONTRACTS));
    }
    String baseV1 = emitBaseRates(n, /*priceBase=*/ 10.0);
    String baseV2 = emitBaseRates(n, /*priceBase=*/ 20.0);
    String quoteV1 = emitQuote(/*addend=*/ 1);
    String quoteV2 = emitQuote(/*addend=*/ 100);
    String unrelatedV1 = emitUnrelated(n, /*thenBase=*/ 1);
    String unrelatedV2 = emitUnrelated(n, /*thenBase=*/ 2);
    String shop = emitShop();

    String full = baseV1 + "\n" + quoteV1 + "\n" + shop + "\n" + unrelatedV1 + "\n";

    // base V1 last arm = 10+n; mid V2 adds 100 → (10+n)+100
    java.math.BigDecimal baseV1Rate =
        new java.math.BigDecimal(String.format(Locale.ROOT, "%.2f", 10.0 + n));
    java.math.BigDecimal baseV2Rate =
        new java.math.BigDecimal(String.format(Locale.ROOT, "%.2f", 20.0 + n));
    java.math.BigDecimal expectedAfterMid =
        baseV1Rate.add(java.math.BigDecimal.valueOf(100));
    // after mid V2 then base V2: (20+n)+100
    java.math.BigDecimal expectedAfterBase =
        baseV2Rate.add(java.math.BigDecimal.valueOf(100));

    Map<String, Object> data =
        Map.of(
            "zone", java.math.BigDecimal.ONE,
            "lb", java.math.BigDecimal.valueOf(n),
            "x", java.math.BigDecimal.valueOf(n));

    return new ReplanFixture(
        n,
        n * 4,
        full,
        baseV1,
        baseV2,
        quoteV1,
        quoteV2,
        unrelatedV1,
        unrelatedV2,
        data,
        "shop",
        "cheapest",
        expectedAfterMid,
        expectedAfterBase);
  }

  private static String emitBaseRates(int n, double priceBase) {
    StringBuilder sb = new StringBuilder(64 + n * 48);
    sb.append("spec base_rates\n\n");
    sb.append("data zone: number\n");
    sb.append("data lb: number\n\n");
    sb.append("rule base: 0\n");
    for (int i = 1; i <= n; i++) {
      double price = priceBase + i;
      sb.append("  unless zone is 1 and lb >= ")
          .append(i)
          .append(" then ")
          .append(String.format(Locale.ROOT, "%.2f", price))
          .append('\n');
    }
    return sb.toString();
  }

  private static String emitUnrelated(int n, int thenBase) {
    StringBuilder sb = new StringBuilder(64 + n * 32);
    sb.append("spec unrelated\n\n");
    sb.append("data x: number\n\n");
    sb.append("rule y: 0\n");
    for (int i = 1; i <= n; i++) {
      sb.append("  unless x >= ").append(i).append(" then ").append(thenBase + i).append('\n');
    }
    return sb.toString();
  }

  private static String emitQuote(int addend) {
    return """
        spec quote

        uses r: base_rates
          -> with zone: zone
          -> with lb: lb

        data zone: number
        data lb: number

        rule total: r.base + %d
        """
        .formatted(addend);
  }

  private static String emitShop() {
    return """
        spec shop

        uses q: quote
          -> with zone: zone
          -> with lb: lb

        data zone: number
        data lb: number

        rule cheapest: q.total
        """;
  }

  static GeneratedSpec deep(int n) {
    StringBuilder sb = new StringBuilder(64 + n * 24);
    sb.append("spec bench_deep\n\n");
    sb.append("data x0: number\n\n");
    sb.append("rule r1: x0 + 1\n");
    for (int i = 2; i <= n; i++) {
      sb.append("rule r").append(i).append(": r").append(i - 1).append(" + 1\n");
    }
    return new GeneratedSpec(
        "deep", n, n, "bench_deep", sb.toString(), Map.of("x0", BigDecimal.ZERO), "r" + n);
  }

  static GeneratedSpec wide(int n) {
    StringBuilder sb = new StringBuilder(64 + n * 48);
    sb.append("spec bench_wide\n\n");
    for (int i = 0; i < n; i++) {
      sb.append("data d").append(i).append(": number\n");
    }
    sb.append('\n');
    for (int i = 0; i < n; i++) {
      sb.append("rule p").append(i).append(": d").append(i).append(" * 2\n");
    }
    sb.append('\n');
    if (n == 1) {
      sb.append("rule total: p0\n");
    } else {
      sb.append("rule s1: p0 + p1\n");
      for (int i = 2; i < n; i++) {
        sb.append("rule s")
            .append(i)
            .append(": s")
            .append(i - 1)
            .append(" + p")
            .append(i)
            .append('\n');
      }
      sb.append("rule total: s").append(n - 1).append('\n');
    }
    Map<String, Object> data = new LinkedHashMap<>(n);
    for (int i = 0; i < n; i++) {
      data.put("d" + i, BigDecimal.ONE);
    }
    return new GeneratedSpec(
        "wide", n, n * 2, "bench_wide", sb.toString(), Map.copyOf(data), "total");
  }

  static GeneratedSpec unless(int n) {
    StringBuilder sb = new StringBuilder(64 + n * 32);
    sb.append("spec bench_unless\n\n");
    sb.append("data x: number\n\n");
    sb.append("rule result: 0\n");
    for (int i = 1; i <= n; i++) {
      sb.append("  unless x >= ").append(i).append(" then ").append(i).append('\n');
    }
    return new GeneratedSpec(
        "unless",
        n,
        n,
        "bench_unless",
        sb.toString(),
        Map.of("x", BigDecimal.valueOf(n)),
        "result");
  }

  /**
   * Multi-Spec logistics rating model. {@code rateCells} selects a research ladder profile:
   *
   * <ul>
   *   <li>1050 — one carrier, Ground only
   *   <li>6300 — one carrier × 6 services
   *   <li>18900 — 3 carriers × 6 services (D2C)
   *   <li>126000 — 20 contracts × 6 services
   * </ul>
   */
  static GeneratedSpec logistics(int rateCells) {
    LogisticsProfile profile = LogisticsProfile.fromRateCells(rateCells);
    StringBuilder sb = new StringBuilder(Math.max(1 << 20, rateCells * 40));
    // Profile metadata goes to stderr only; Lemma allows commentary only right after a spec line.

    emitAccessorials(sb);
    sb.append('\n');
    emitFuelGround(sb);
    sb.append('\n');
    emitFuelAir(sb);
    sb.append('\n');

    List<String> quoteSpecNames = new ArrayList<>();
    for (String carrier : profile.carriers()) {
      emitZonesSpec(sb, carrier, profile.serviceCount());
      sb.append('\n');
      for (int s = 0; s < profile.serviceCount(); s++) {
        String service = SERVICE_IDS[s];
        emitRatesSpec(sb, carrier, service, s);
        sb.append('\n');
        String quoteName = "quote_" + carrier + "_" + service;
        emitQuotePipeline(sb, quoteName, carrier, service, s);
        sb.append('\n');
        quoteSpecNames.add(quoteName);
      }
    }

    emitRateShop(sb, quoteSpecNames);
    sb.append('\n');

    Map<String, Object> data = shipmentData();
    int limitScale = profile.rateCells() + profile.carriers().size() * LOGISTICS_ZIP3_COUNT * profile.serviceCount() + 256;

    System.err.printf(
        Locale.ROOT,
        "logistics profile=%s carriers=%d services=%d rate_cells=%d zip3=%d quotes=%d src_chars≈%d%n",
        profile.name(),
        profile.carriers().size(),
        profile.serviceCount(),
        profile.rateCells(),
        LOGISTICS_ZIP3_COUNT,
        quoteSpecNames.size(),
        sb.length());

    return new GeneratedSpec(
        "logistics",
        profile.rateCells(),
        limitScale,
        "rate_shop",
        sb.toString(),
        data,
        "cheapest");
  }

  private static Map<String, Object> shipmentData() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("length_in", new BigDecimal("12"));
    data.put("width_in", new BigDecimal("10"));
    data.put("height_in", new BigDecimal("8"));
    data.put("actual_lb", new BigDecimal("5"));
    data.put("dest_zip3", "100");
    data.put("is_residential", true);
    data.put("is_das", false);
    data.put("diesel_usd", new BigDecimal("4.50"));
    data.put("jet_usd", new BigDecimal("2.40"));
    data.put("discount_pct", new BigDecimal("10"));
    return Map.copyOf(data);
  }

  private static void emitAccessorials(StringBuilder sb) {
    sb.append("spec accessorials\n\n");
    sb.append("data is_residential: boolean\n");
    sb.append("data is_das: boolean\n");
    sb.append("data billable_lb: number\n");
    sb.append("data longest_side_in: number\n\n");
    sb.append("rule residential_fee: 0\n");
    sb.append("  unless is_residential then 6.50\n\n");
    sb.append("rule das_fee: 0\n");
    sb.append("  unless is_das and is_residential then 4.20\n");
    sb.append("  unless is_das and not is_residential then 2.80\n\n");
    sb.append("rule ahs_weight_fee: 0\n");
    sb.append("  unless billable_lb > 50 then 30\n\n");
    sb.append("rule ahs_length_fee: 0\n");
    sb.append("  unless longest_side_in > 48 then 30\n\n");
    sb.append("rule large_package_fee: 0\n");
    sb.append("  unless longest_side_in >= 96 then 95\n\n");
    sb.append("rule accessorial_total:\n");
    sb.append("  residential_fee + das_fee + ahs_weight_fee + ahs_length_fee + large_package_fee\n");
  }

  private static void emitFuelGround(StringBuilder sb) {
    sb.append("spec fuel_ground\n\n");
    sb.append("data diesel_usd: number\n\n");
    sb.append("rule fuel_pct: 20%\n");
    double price = 2.50;
    double pct = 20.0;
    for (int i = 0; i < 24; i++) {
      sb.append("  unless diesel_usd >= ")
          .append(String.format(Locale.ROOT, "%.2f", price))
          .append(" then ")
          .append(String.format(Locale.ROOT, "%.2f", pct))
          .append("%\n");
      price += 0.27;
      pct += 0.25;
    }
  }

  private static void emitFuelAir(StringBuilder sb) {
    sb.append("spec fuel_air\n\n");
    sb.append("data jet_usd: number\n\n");
    sb.append("rule fuel_pct: 18%\n");
    double price = 1.50;
    double pct = 18.0;
    for (int i = 0; i < 24; i++) {
      sb.append("  unless jet_usd >= ")
          .append(String.format(Locale.ROOT, "%.2f", price))
          .append(" then ")
          .append(String.format(Locale.ROOT, "%.2f", pct))
          .append("%\n");
      price += 0.05;
      pct += 0.25;
    }
  }

  private static void emitZonesSpec(StringBuilder sb, String carrier, int serviceCount) {
    sb.append("spec zones_").append(carrier).append("\n\n");
    sb.append("data dest_zip3: text\n\n");
    for (int s = 0; s < serviceCount; s++) {
      String service = SERVICE_IDS[s];
      sb.append("rule ").append(service).append("_zone: 2\n");
      for (int z = 0; z < LOGISTICS_ZIP3_COUNT; z++) {
        int zone = 2 + (z + s) % LOGISTICS_ZONES; // 2..8
        sb.append("  unless dest_zip3 is \"")
            .append(String.format(Locale.ROOT, "%03d", z))
            .append("\" then ")
            .append(zone)
            .append('\n');
      }
      sb.append('\n');
    }
  }

  private static void emitRatesSpec(StringBuilder sb, String carrier, String service, int serviceIndex) {
    sb.append("spec rates_").append(carrier).append('_').append(service).append("\n\n");
    sb.append("data zone: number\n");
    sb.append("data billable_lb: number\n\n");
    sb.append("rule base_rate: 0\n");
    // zones stored as 2..8
    for (int zi = 0; zi < LOGISTICS_ZONES; zi++) {
      int zone = 2 + zi;
      for (int lb = 1; lb <= LOGISTICS_WEIGHTS; lb++) {
        double price = 8.0 + zone * 1.15 + lb * 0.42 + serviceIndex * 0.75;
        sb.append("  unless zone is ")
            .append(zone)
            .append(" and billable_lb >= ")
            .append(lb)
            .append(" then ")
            .append(String.format(Locale.ROOT, "%.2f", price))
            .append('\n');
      }
    }
  }

  private static void emitQuotePipeline(
      StringBuilder sb, String quoteName, String carrier, String service, int serviceIndex) {
    boolean air = serviceIndex >= 2;
    String fuelSpec = air ? "fuel_air" : "fuel_ground";
    String fuelData = air ? "jet_usd" : "diesel_usd";
    String zoneRule = service + "_zone";

    sb.append("spec ").append(quoteName).append("\n\n");

    sb.append("uses z: zones_").append(carrier).append('\n');
    sb.append("  -> with dest_zip3: dest_zip3\n\n");
    sb.append("uses r: rates_").append(carrier).append('_').append(service).append('\n');
    sb.append("  -> with zone: zone\n");
    sb.append("  -> with billable_lb: billable_lb\n\n");
    sb.append("uses a: accessorials\n");
    sb.append("  -> with is_residential: is_residential\n");
    sb.append("  -> with is_das: is_das\n");
    sb.append("  -> with billable_lb: billable_lb\n");
    sb.append("  -> with longest_side_in: length_in\n\n");
    sb.append("uses f: ").append(fuelSpec).append('\n');
    sb.append("  -> with ").append(fuelData).append(": ").append(fuelData).append("\n\n");

    sb.append("data length_in: number\n");
    sb.append("data width_in: number\n");
    sb.append("data height_in: number\n");
    sb.append("data actual_lb: number\n");
    sb.append("data dest_zip3: text\n");
    sb.append("data is_residential: boolean\n");
    sb.append("data is_das: boolean\n");
    sb.append("data diesel_usd: number\n");
    sb.append("data jet_usd: number\n");
    sb.append("data discount_pct: number\n\n");

    sb.append("rule dim_lb: (length_in * width_in * height_in) / 139\n");
    sb.append("rule billable_lb: actual_lb\n");
    sb.append("  unless dim_lb > actual_lb then dim_lb\n");
    sb.append("rule zone: z.").append(zoneRule).append('\n');
    sb.append("rule base: r.base_rate\n");
    sb.append("rule accessorials_total: a.accessorial_total\n");
    sb.append("rule pre_fuel: base + accessorials_total\n");
    sb.append("rule fuel_amount: pre_fuel * f.fuel_pct\n");
    sb.append("rule with_fuel: pre_fuel + fuel_amount\n");
    sb.append("rule discount_amount: with_fuel * (discount_pct / 100)\n");
    sb.append("rule with_discount: with_fuel - discount_amount\n");
    sb.append("rule total: with_discount\n");
  }

  private static void emitRateShop(StringBuilder sb, List<String> quoteSpecNames) {
    sb.append("spec rate_shop\n\n");

    for (int i = 0; i < quoteSpecNames.size(); i++) {
      String q = quoteSpecNames.get(i);
      String alias = "q" + i;
      sb.append("uses ").append(alias).append(": ").append(q).append('\n');
      sb.append("  -> with length_in: length_in\n");
      sb.append("  -> with width_in: width_in\n");
      sb.append("  -> with height_in: height_in\n");
      sb.append("  -> with actual_lb: actual_lb\n");
      sb.append("  -> with dest_zip3: dest_zip3\n");
      sb.append("  -> with is_residential: is_residential\n");
      sb.append("  -> with is_das: is_das\n");
      sb.append("  -> with diesel_usd: diesel_usd\n");
      sb.append("  -> with jet_usd: jet_usd\n");
      sb.append("  -> with discount_pct: discount_pct\n\n");
    }

    sb.append("data length_in: number\n");
    sb.append("data width_in: number\n");
    sb.append("data height_in: number\n");
    sb.append("data actual_lb: number\n");
    sb.append("data dest_zip3: text\n");
    sb.append("data is_residential: boolean\n");
    sb.append("data is_das: boolean\n");
    sb.append("data diesel_usd: number\n");
    sb.append("data jet_usd: number\n");
    sb.append("data discount_pct: number\n\n");

    // Expose each quote then progressive min (no self-ref on cheapest).
    for (int i = 0; i < quoteSpecNames.size(); i++) {
      sb.append("rule quote_").append(i).append(": q").append(i).append(".total\n");
    }
    sb.append('\n');
    if (quoteSpecNames.size() == 1) {
      sb.append("rule cheapest: quote_0\n");
    } else {
      sb.append("rule min_1: quote_0\n");
      sb.append("  unless quote_1 < quote_0 then quote_1\n");
      for (int i = 2; i < quoteSpecNames.size(); i++) {
        sb.append("rule min_")
            .append(i)
            .append(": min_")
            .append(i - 1)
            .append('\n');
        sb.append("  unless quote_")
            .append(i)
            .append(" < min_")
            .append(i - 1)
            .append(" then quote_")
            .append(i)
            .append('\n');
      }
      sb.append("rule cheapest: min_").append(quoteSpecNames.size() - 1).append('\n');
    }
  }

  /** Research ladder profile. */
  record LogisticsProfile(String name, List<String> carriers, int serviceCount, int rateCells) {
    static LogisticsProfile fromRateCells(int rateCells) {
      return switch (rateCells) {
        case LOGISTICS_GROUND_CELLS -> new LogisticsProfile(
            "ground", List.of("ups"), 1, LOGISTICS_GROUND_CELLS);
        case LOGISTICS_GROUND_CELLS * LOGISTICS_SERVICES -> new LogisticsProfile(
            "carrier", List.of("ups"), LOGISTICS_SERVICES, LOGISTICS_GROUND_CELLS * LOGISTICS_SERVICES);
        case LOGISTICS_GROUND_CELLS * LOGISTICS_SERVICES * LOGISTICS_D2C_CARRIERS -> new LogisticsProfile(
            "d2c",
            List.of("ups", "fedex", "regional"),
            LOGISTICS_SERVICES,
            LOGISTICS_GROUND_CELLS * LOGISTICS_SERVICES * LOGISTICS_D2C_CARRIERS);
        case LOGISTICS_GROUND_CELLS
            * LOGISTICS_SERVICES
            * LOGISTICS_ENTERPRISE_CONTRACTS -> {
          List<String> contracts = new ArrayList<>(LOGISTICS_ENTERPRISE_CONTRACTS);
          for (int i = 1; i <= LOGISTICS_ENTERPRISE_CONTRACTS; i++) {
            contracts.add(String.format(Locale.ROOT, "contract_%02d", i));
          }
          yield new LogisticsProfile(
              "enterprise",
              contracts,
              LOGISTICS_SERVICES,
              LOGISTICS_GROUND_CELLS * LOGISTICS_SERVICES * LOGISTICS_ENTERPRISE_CONTRACTS);
        }
        default -> throw new IllegalArgumentException(
            "logistics --rules must be one of "
                + LOGISTICS_GROUND_CELLS
                + " (ground), "
                + (LOGISTICS_GROUND_CELLS * LOGISTICS_SERVICES)
                + " (carrier), "
                + (LOGISTICS_GROUND_CELLS * LOGISTICS_SERVICES * LOGISTICS_D2C_CARRIERS)
                + " (d2c), "
                + (LOGISTICS_GROUND_CELLS * LOGISTICS_SERVICES * LOGISTICS_ENTERPRISE_CONTRACTS)
                + " (enterprise)");
      };
    }
  }
}
