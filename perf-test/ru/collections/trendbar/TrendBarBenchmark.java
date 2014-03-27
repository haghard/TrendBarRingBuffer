package ru.collections.trendbar;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public class TrendBarBenchmark extends AbstractBenchmark
{
  private final TrendBarRingBuffer<String, TrendBar, Quote> trendBarBuffer;
  private static final long ITERATIONS = 1000L * 1000L * 50L;

  TrendBarBenchmark( TrendBarRingBuffer<String, TrendBar, Quote> trendBarBuffer )
  {
    this.trendBarBuffer = trendBarBuffer;
  }

  @Override
  public void runBenchmark() throws Exception
  {
    testImplementation();
  }

  @Override
  long runBufferPass() throws InterruptedException
  {
    final AtomicBoolean flag = new AtomicBoolean( true );
    final Thread consumerThread = new Consumer( trendBarBuffer, flag );
    final Producer producerThread = new Producer( trendBarBuffer, ITERATIONS );

    producerThread.start();
    consumerThread.start();
    long start = System.currentTimeMillis();

    producerThread.join();
    flag.set( false );

    long opsPerSecond = ( ITERATIONS * 1000L ) / ( System.currentTimeMillis() - start );
    System.out.format("failes number %d, gap %d ", producerThread.failes, trendBarBuffer.gap );
    trendBarBuffer.gap = 0;
    return opsPerSecond;
  }

  /**
   * Every iteration close TrendBar
   */
  static class Consumer extends Thread
  {
    final TrendBarRingBuffer<String, TrendBar, Quote> trendBarBuffer;
    final AtomicBoolean flag;
    int pollNumber = 0;

    public Consumer( TrendBarRingBuffer<String, TrendBar, Quote> trendBarBuffer, AtomicBoolean flag )
    {
      this.trendBarBuffer = trendBarBuffer;
      this.flag = flag;
    }

    @Override
    public void run()
    {
      final List<TrendBar> trendBars = new ArrayList<TrendBar>( 8 );
      while ( flag.get() )
      {
        pollNumber += trendBarBuffer.poll( trendBars );
        //run 100 times in second
        LockSupport.parkNanos( 10000 );
        trendBars.clear();
      }
    }
  }


  static class Producer extends Thread
  {
    private final long iterations;
    int failes = 0;
    private final TrendBarRingBuffer<String, TrendBar, Quote> trendBarBuffer;
    private final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    private final ThreadLocalRandom priceRnd = ThreadLocalRandom.current();
    private final String[] symbols = { "RUS-USA", "RUS-EURO", "RUS-GBP", "USA-GBP", "EURO-USA", "EURO-RUS", "EURO-GBP", "CHINA-RUS" };

    public Producer( TrendBarRingBuffer<String, TrendBar, Quote> trendBarBuffer, long iterations )
    {
      this.trendBarBuffer = trendBarBuffer;
      this.iterations = iterations;
    }

    @Override
    public void run()
    {
      int i = 0;
      double newPrice = 0;
      while ( i < iterations )
      {
        final int index = rnd.nextInt( 0, 8 );
        final String symbol = symbols[index];

        final boolean success = trendBarBuffer.offer( symbol, new Quote( newPrice, System.currentTimeMillis(), symbol ) );
        if ( !success )
        {
          failes++;
        }

        newPrice = 100 * priceRnd.nextDouble();
        i++;
      }
    }
  }
}