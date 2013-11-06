package ru.collections.trendbar;


public class Quote
{
  private final double price;
  private final long ts;
  private final String symbol;

  public Quote(double price, long ts, String symbol)
  {
    this.price = price;
    this.ts = ts;
    this.symbol = symbol;
  }

  public String getSymbol()
  {
    return symbol;
  }

  public long getTs()
  {
    return ts;
  }

  public double getPrice()
  {
    return price;
  }
}
