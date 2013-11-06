package ru.collections.trendbar;

import static ru.collections.trendbar.TrendBarRingBuffer.createSinglePCBuffer;

public class BenchmarkRunner
{
  public static void main(String[] args) throws Exception
  {
    final TrendBarRingBuffer trendBarBuffer =
            createSinglePCBuffer( 1 << 4, TrendBarAggregator.instance(),
                    String.class, TrendBar.class, Quote.class );

      new TrendBarBenchmark( trendBarBuffer ).runBenchmark();
  }
}
