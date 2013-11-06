package ru.collections.trendbar;

import java.util.Collection;

public interface TrendBarBuffer<K, V, V2>
{
  boolean offer( K key, V2 value );

  int poll( Collection<? super V> bucket );

  int size();

  int getCapacity();

  long rejectionCount();

  long writePosition();

  long readPosition();
}
