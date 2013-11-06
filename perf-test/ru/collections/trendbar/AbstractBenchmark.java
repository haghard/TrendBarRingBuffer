package ru.collections.trendbar;

abstract class AbstractBenchmark
{
  public static final int RUNS = 10;
  long[] bufferOps = new long[RUNS];

  abstract long runBufferPass() throws InterruptedException;

  public abstract void runBenchmark() throws Exception;

  protected void testImplementation() throws Exception
  {
    System.out.println( "Starting TrendBarBuffer tests" );
    for ( int i = 0; i < RUNS; i++ )
    {
      System.gc();
      bufferOps[i] = runBufferPass();
      System.out.format( "Run %d, TrendBarBuffer=%,d ops/sec%n", i, Long.valueOf( bufferOps[i] ) );
    }
  }
}
