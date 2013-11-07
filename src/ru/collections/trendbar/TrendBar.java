package ru.collections.trendbar;

import static java.lang.String.format;

public class TrendBar
{
  private final double openPrice;
  private final double closePrice;
  private final double highPrice;
  private final double lowPrice;
  private final long trendStart;
  private final String symbol;

  public TrendBar( double openPrice, double closePrice,
                   double highPrice, double lowPrice,
                   long trendStart, String symbol )
  {
    this.openPrice = openPrice;
    this.closePrice = closePrice;
    this.highPrice = highPrice;
    this.lowPrice = lowPrice;
    this.trendStart = trendStart;
    this.symbol = symbol;
  }

  public TrendBar( Quote quote, String symbol )
  {
    this.openPrice = quote.getPrice();
    this.closePrice = quote.getPrice();
    this.highPrice = quote.getPrice();
    this.lowPrice = quote.getPrice();
    this.trendStart = System.currentTimeMillis();
    this.symbol = symbol;
  }

  public double getOpenPrice()
  {
    return openPrice;
  }

  public double getClosePrice()
  {
    return closePrice;
  }

  public double getHighPrice()
  {
    return highPrice;
  }

  public double getLowPrice()
  {
    return lowPrice;
  }

  public long getTrendStart()
  {
    return trendStart;
  }

  public String getTrendSymbol()
  {
    return symbol;
  }

  public String toString()
  {
    return new StringBuilder()
            .append( "highPrice: " )
            .append( format( "$%.2f ", highPrice ) )
            .append( "lowPrice: " )
            .append( format( "$%.2f ", lowPrice ) )
            .append( "symbol: " ).append( symbol )
            .append( "] \n" )
            .toString();
  }

  public enum TrendPeriod
  {
    M1,
    H1,
    D1
  }
}