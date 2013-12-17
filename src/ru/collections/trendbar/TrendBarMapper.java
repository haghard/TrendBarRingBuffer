package ru.collections.trendbar;

abstract class TrendBarMapper<K, V extends TrendBar, V2 extends Quote>
{
  abstract V map( V trendBar, V2 quote );

  abstract V newInstance( K key, V2 value );

  @SuppressWarnings("unchecked")
  public static <K, V extends TrendBar, V2 extends Quote> TrendBarMapper<K, V, V2> instance()
  {
    return ( TrendBarMapper<K, V, V2> ) new MaxMinPriceMapper();
  }

  final static class MaxMinPriceMapper extends TrendBarMapper<String, TrendBar, Quote>
  {
    @Override
    TrendBar map( TrendBar trendBar, Quote quote )
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
    TrendBar newInstance( String key, Quote value )
    {
      return new TrendBar( value, key );
    }
  }
}