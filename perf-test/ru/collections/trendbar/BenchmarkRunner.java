package ru.collections.trendbar;

import static ru.collections.trendbar.TrendBarRingBuffer.createSinglePCBuffer;

public class BenchmarkRunner
{
  public static void main(String[] args) throws Exception
  {
    final TrendBarRingBuffer<String, TrendBar, Quote> trendBarBuffer =
    		createSinglePCBuffer( 1 << 4, 
    				TrendBarMapper.<String, TrendBar, Quote>instance(),
                    String.class, TrendBar.class, Quote.class );

      new TrendBarBenchmark( trendBarBuffer ).runBenchmark();
  }
}
