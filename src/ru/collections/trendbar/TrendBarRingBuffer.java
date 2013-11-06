package ru.collections.trendbar;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * @param <K>  key
 * @param <V>  stored value
 * @param <V2> incoming value
 */
public class TrendBarRingBuffer<K, V extends TrendBar, V2 extends Quote> implements TrendBarBuffer<K, V, V2>
{
  private final int mask;
  private final int capacity;

  // producer write position index
  private final AtomicLong writePosition = new PaddedAtomicLong( 1 );

  // the prev produces transaction clean position
  private volatile long prevClean = 0;

  // consumer read position
  private final AtomicLong readPosition = new PaddedAtomicLong( 1 );

  // the prev consumer transaction read position
  private final AtomicLong prevReadPosition = new PaddedAtomicLong( 0 );

  private long rejectionCount = 0;

  private final K[] keys;
  private final AtomicReferenceArray<V> currentTrends;

  private final TrendBarAggregator<K, V, V2> trendBarAggregator;

  public static <K, V extends TrendBar, V2 extends Quote> TrendBarRingBuffer<K, V, V2> createSinglePCBuffer(
          final int capacity, TrendBarAggregator<K, V, V2> trendBarAggregator,
          Class<String> keyClass, Class<TrendBar> valueCLass, Class<Quote> itemClass )
  {
    return new TrendBarRingBuffer<K, V, V2>( capacity, trendBarAggregator );
  }

  private TrendBarRingBuffer( final int capacity, TrendBarAggregator<K, V, V2> trendBarAggregator )
  {
    this.capacity = findNextPositivePowerOfTwo( capacity );
    this.mask = capacity - 1;
    this.trendBarAggregator = trendBarAggregator;
    this.keys = ( K[] ) new Object[this.capacity];
    this.currentTrends = new AtomicReferenceArray<V>( this.capacity );
  }

  public static int findNextPositivePowerOfTwo( final int value )
  {
    return 1 << ( 32 - Integer.numberOfLeadingZeros( value - 1 ) );
  }

  /**
   * This is check-act-recheck strategy
   * <p/>
   * if after update we observe what reader index was changed
   * do add for prevent lost current value.
   */
  @Override
  public boolean offer( K key, V2 value )
  {
    if ( key == null || value == null )
      throw new NullPointerException( "Null is not a valid element" );

    final long writePosition = this.writePosition.get();

    //iterate from reader pos to write pos to find matched key
    for ( long readPosition = this.readPosition.get(); readPosition < writePosition; readPosition++ )
    {
      final int keyIndex = mask( readPosition );
      if ( key.equals( keys[keyIndex] ) )
      {
        V mergedTrendBar = ( V ) trendBarAggregator.tryAggregate( currentTrends.get( keyIndex ), value );
        if ( mergedTrendBar != null )
        {
          //accept update
          currentTrends.lazySet( keyIndex, mergedTrendBar );
          if ( this.readPosition.get() <= readPosition ) //check that the reader has not read it yet
          {
            return true;
          }
          else
          {
            /*
             * Since consumer transaction was started we try to add current (key,value) to prevent data lost,
             * this may be a reason for duplicate in consumer.
             */
            break;
          }
        }
        else
        {
          return true;
        }
      }
    }

    return add( key, value );
  }

  /**
   * Add new key and  value
   *
   * @param key
   * @param value
   * @return
   */
  private boolean add( K key, V2 value )
  {
    if ( isFull() )
    {
      rejectionCount++;
      return false;
    }

    cleanUp();
    store( key, value );

    return true;
  }

  private void cleanUp()
  {
    final long prevRead = this.prevReadPosition.get();

    if ( prevRead == prevClean )
    {
      return;
    }

    while ( prevClean < prevRead )
    {
      int index = mask( ++prevClean );
      this.keys[index] = null;
      this.currentTrends.lazySet( index, null );
    }
  }

  /**
   * @param key
   * @param value
   */
  private void store( K key, V2 value )
  {
    final long nextWrite = this.writePosition.get();
    final int index = mask( nextWrite );
    this.keys[index] = key;
    this.currentTrends.lazySet( index, trendBarAggregator.produceBaseOn( key, value ) );
    this.writePosition.lazySet( nextWrite + 1 );
  }

  private boolean isFull()
  {
    return size() == capacity;
  }

  public int size()
  {
    return ( int ) ( writePosition.get() - prevReadPosition.get() - 1 );
  }

  @Override
  public int getCapacity()
  {
    return capacity;
  }

  /**
   * Consumer interface
   *
   * @param bucket
   * @return
   */
  public int poll( Collection<? super V> bucket )
  {
    this.readPosition.lazySet( this.writePosition.get() );
    return fill( bucket );
  }

  private int fill( Collection<? super V> bucket )
  {
    final long readPosition = this.readPosition.get();
    final long prevReadPosition = this.prevReadPosition.get();

    for ( long readIndex = prevReadPosition + 1; readIndex < readPosition; readIndex++ )
    {
      final int index = mask( readIndex );
      bucket.add( currentTrends.get( index ) );
    }

    int readCount = ( int ) ( readPosition - prevReadPosition - 1 );
    this.prevReadPosition.lazySet( readPosition - 1 );

    return readCount;
  }

  private int mask( long value )
  {
    return ( ( int ) value ) & mask;
  }

  public long rejectionCount()
  {
    return rejectionCount;
  }

  @Override
  public long writePosition()
  {
    return this.writePosition.get();
  }

  @Override
  public long readPosition()
  {
    return this.readPosition.get();
  }
}