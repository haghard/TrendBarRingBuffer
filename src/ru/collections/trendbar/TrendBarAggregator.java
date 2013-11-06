package ru.collections.trendbar;

abstract class TrendBarAggregator<K, V extends TrendBar, V2 extends Quote>
{
  abstract V tryAggregate( V trendBar, V2 quote );

  public static <K, V extends TrendBar, V2 extends Quote> TrendBarAggregator<K, V, V2> instance()
  {
    return ( TrendBarAggregator<K, V, V2> ) new MaxMinPriceAggregator();
  }

  public abstract V produceBaseOn( K key, V2 value );

  final static class MaxMinPriceAggregator extends TrendBarAggregator<String, TrendBar, Quote>
  {
    @Override
    TrendBar tryAggregate( TrendBar trendBar, Quote quote )
    {
      if ( trendBar == null )
      {
        return null;
      }

      if ( trendBar.getHighPrice() < quote.getPrice() )
      {
        //replace high price
        return new TrendBar( trendBar.getOpenPrice(), trendBar.getClosePrice(),
                quote.getPrice(), trendBar.getLowPrice(),
                trendBar.getTrendStart(), trendBar.getTrendSymbol() );
      }
      else if ( trendBar.getLowPrice() > quote.getPrice() )
      {
        //replace low price
        return new TrendBar( trendBar.getOpenPrice(), trendBar.getClosePrice(),
                trendBar.getHighPrice(), quote.getPrice(),
                trendBar.getTrendStart(), trendBar.getTrendSymbol() );
      }
      else
      {
        //no need to replace
        return null;
      }
    }

    @Override
    public TrendBar produceBaseOn( String key, Quote value )
    {
      return new TrendBar( value, key );
    }
  }
}