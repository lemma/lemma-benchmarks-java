package com.lemmabase.bench;

import com.lemmabase.lemma.ResourceLimits;

/**
 * Raised {@link ResourceLimits} for generated Spec stress sizes. Defaults reject huge Specs (5 MB
 * source, 30k normalized nodes, 50 MB loaded). Unset keys keep engine defaults.
 */
public final class BenchLimits {
  private BenchLimits() {}

  /**
   * Named overrides sized for scale parameter {@code n} (rules / arms / rate cells).
   *
   * @param n generator scale
   * @return builder for {@link com.lemmabase.lemma.Engine#create(ResourceLimits.Builder)}
   */
  public static ResourceLimits.Builder forSize(int n) {
    if (n < 1) {
      throw new IllegalArgumentException("n must be >= 1");
    }
    long scale = Math.max(1_000L, (long) n);
    long sourceBytes = Math.max(16L * 1024 * 1024, scale * 256);
    long exprCount = Math.max(65_536L, scale * 64);
    long normalized = Math.max(100_000L, scale * 128);
    long loaded = Math.max(100L * 1024 * 1024, sourceBytes * 4);
    return ResourceLimits.builder()
        .maxSourceSizeBytes(sourceBytes)
        .maxExpressionCount(exprCount)
        .maxNormalizedExpressionNodes(normalized)
        .maxLoadedBytes(loaded);
  }
}
