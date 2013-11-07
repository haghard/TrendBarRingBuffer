package ru.collections.trendbar;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;

class LPad
{
  public long p00, p01, p02, p03, p04, p05, p06, p07;
  public long p10, p11, p12, p13, p14, p15, p16;
}

class ColdField<K, V> extends LPad
{
  protected final int mask;
  protected final int capacity;

  protected final K[] keys;
  protected final V[] currentTrends;

  protected static final int SPARSE_SHIFT = Integer.getInteger( "sparse.shift", 2 );

  ColdField( int capacity, Class<V> valueCLass )
  {
    this.capacity = findNextPositivePowerOfTwo( capacity );
    this.mask = capacity - 1;
    this.keys = ( K[] ) new Object[this.capacity << SPARSE_SHIFT];
    this.currentTrends = ( V[] ) Array.newInstance( valueCLass, this.capacity << SPARSE_SHIFT );
  }

  protected int findNextPositivePowerOfTwo( final int value )
  {
    return 1 << ( 32 - Integer.numberOfLeadingZeros( value - 1 ) );
  }
}

class LPad1<K, V> extends ColdField<K, V>
{
  public long p00, p01, p02, p03, p04, p05, p06, p07;
  public long p10, p11, p12, p13, p14, p15, p16;

  LPad1( int capacity, Class<V> valueCLass )
  {
    super( capacity, valueCLass );
  }
}

class WriteField<K, V> extends LPad1<K, V>
{
  protected volatile long writePosition = 1;

  WriteField( int capacity, Class<V> valueCLass )
  {
    super( capacity, valueCLass );
  }
}

class L2Pad<K, V> extends WriteField<K, V>
{
  public long p00, p01, p02, p03, p04, p05, p06, p07;
  public long p30, p31, p32, p33, p34, p35, p36;

  L2Pad( int capacity, Class<V> valueCLass )
  {
    super( capacity, valueCLass );
  }
}

class ReadField<K, V> extends L2Pad<K, V>
{
  protected volatile long readPosition = 1;

  ReadField( int capacity, Class<V> valueCLass )
  {
    super( capacity, valueCLass );
  }
}

class L3Pad<K, V> extends ReadField<K, V>
{
  public long p00, p01, p02, p03, p04, p05, p06, p07;
  public long p50, p51, p52, p53, p54, p55, p56;

  L3Pad( int capacity, Class<V> valueCLass )
  {
    super( capacity, valueCLass );
  }
}

public class TrendBarRingBuffer<K, V extends TrendBar, V2 extends Quote> extends L3Pad<K, V> implements TrendBarBuffer<K, V, V2>
{
  protected static final long ARRAY_BASE;
  protected static final int ELEMENT_SCALE;
  protected final static long WRITE_CURSOR_OFFSET;
  protected final static long READ_CURSOR_OFFSET;

  static
  {
    try
    {
      ARRAY_BASE = Unsafe0.getUnsafe().arrayBaseOffset( Object[].class );
      ELEMENT_SCALE = Unsafe0.getUnsafe().arrayIndexScale( Object[].class );
      READ_CURSOR_OFFSET = Unsafe0.getUnsafe().objectFieldOffset( ReadField.class.getDeclaredField( "readPosition" ) );
      WRITE_CURSOR_OFFSET = Unsafe0.getUnsafe().objectFieldOffset( WriteField.class.getDeclaredField( "writePosition" ) );
    }
    catch ( NoSuchFieldException e )
    {
      throw new RuntimeException( e );
    }
  }

  private void writeWriterCursorOrdered( long v )
  {
    Unsafe0.getUnsafe().putOrderedLong( this, WRITE_CURSOR_OFFSET, v );
  }

  private void readReaderCursorOrdered( long v )
  {
    Unsafe0.getUnsafe().putOrderedLong( this, READ_CURSOR_OFFSET, v );
  }

  private long writerCursor()
  {
    return this.writePosition;
  }

  private long readerCursor()
  {
    return this.readPosition;
  }

  // the prev produces transaction clean position
  private long prevCleanCursor = 0;

  // the prev consumer transaction read position
  private final AtomicLong prevReadCursor = new PaddedAtomicLong( 0 );

  private long rejectionCount = 0;

  private final TrendBarAggregator<K, V, V2> trendBarAggregator;

  public static <K, V extends TrendBar, V2 extends Quote> TrendBarRingBuffer<K, V, V2> createSinglePCBuffer(
          final int capacity, TrendBarAggregator<K, V, V2> trendBarAggregator,
          Class<String> keyClass, Class<TrendBar> valueCLass, Class<Quote> itemClass )
  {
    return new TrendBarRingBuffer<K, V, V2>( capacity, trendBarAggregator, valueCLass );
  }

  private TrendBarRingBuffer( final int capacity, TrendBarAggregator<K, V, V2> trendBarAggregator, Class<TrendBar> valueCLass )
  {
    super( capacity, ( Class<V> ) valueCLass );
    this.trendBarAggregator = trendBarAggregator;
  }

  private long elementOffsetInBuffer( long index )
  {
    return ARRAY_BASE + ( ( ( index & mask ) << SPARSE_SHIFT ) * ELEMENT_SCALE );
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

    final long writeCursor = writerCursor();

    //iterate from reader pos to write pos to find matched key
    for ( long readCursor = readerCursor(); readCursor < writeCursor; readCursor++ )
    {
      if ( key.equals( keys[ mask( readCursor ) ] ) )
      {
        long index = elementOffsetInBuffer( readCursor );
        // LOAD/LOAD barrier
        V currentTrendBar = ( V ) Unsafe0.getUnsafe().getObjectVolatile( currentTrends, index );
        V mergedTrendBar = ( V ) trendBarAggregator.tryAggregate( currentTrendBar, value );
        if ( mergedTrendBar != null )
        {
          //accept update
          //STORE/STORE barrier
          Unsafe0.getUnsafe().putOrderedObject( currentTrends, index, mergedTrendBar );
          if ( readerCursor() <= readCursor ) //check that the reader has not read it yet
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
        } else
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
    final long prevReadCursor = this.prevReadCursor.get();

    if ( prevReadCursor == prevCleanCursor )
    {
      return;
    }

    while ( prevCleanCursor < prevReadCursor )
    {
      this.keys[mask( prevCleanCursor + 1 )] = null;
      // STORE/STORE barrier
      Unsafe0.getUnsafe().putOrderedObject( currentTrends, elementOffsetInBuffer( prevCleanCursor + 1 ), null );
      prevCleanCursor++;
    }
  }

  /**
   * @param key
   * @param value
   */
  private void store( K key, V2 value )
  {
    final long nextWrite = writerCursor();
    this.keys[ mask( nextWrite ) ] = key;
    // STORE/STORE barrier
    Unsafe0.getUnsafe().putOrderedObject( currentTrends, elementOffsetInBuffer( nextWrite ), trendBarAggregator.produceBaseOn( key, value ) );
    writeWriterCursorOrdered( nextWrite + 1 );
  }

  private boolean isFull()
  {
    return size() == capacity;
  }

  public int size()
  {
    return ( int ) ( writerCursor() - prevReadCursor.get() - 1 );
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
    readReaderCursorOrdered( writerCursor() );
    return fill( bucket );
  }

  private int fill( Collection<? super V> bucket )
  {
    final long readPosition = readerCursor();
    final long prevReadPosition = this.prevReadCursor.get();

    for ( long readIndex = prevReadPosition + 1; readIndex < readPosition; readIndex++ )
    {
      // LOAD/LOAD barrier
      bucket.add( ( V ) Unsafe0.getUnsafe().getObjectVolatile( currentTrends, elementOffsetInBuffer( readIndex ) ) );
    }

    int readCount = ( int ) ( readPosition - prevReadPosition - 1 );
    this.prevReadCursor.lazySet( readPosition - 1 );

    return readCount;
  }

  private int mask( long value )
  {
    return ( int ) ( value & mask );
  }

  public long rejectionCount()
  {
    return rejectionCount;
  }

  @Override
  public long writePosition()
  {
    return writerCursor();
  }

  @Override
  public long readPosition()
  {
    return readerCursor();
  }
}