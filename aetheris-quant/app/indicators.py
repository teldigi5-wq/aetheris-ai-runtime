import numpy as np
import pandas as pd

def ema(s: pd.Series, n: int):
    return s.ewm(span=n, adjust=False).mean()

def sma(s: pd.Series, n: int):
    return s.rolling(n).mean()

def rsi(s: pd.Series, n: int = 14):
    d = s.diff()
    up = d.clip(lower=0)
    dn = -d.clip(upper=0)
    avg_up = up.ewm(alpha=1/n, adjust=False).mean()
    avg_dn = dn.ewm(alpha=1/n, adjust=False).mean()
    rs = avg_up / avg_dn.replace(0, np.nan)
    return (100 - 100 / (1 + rs)).fillna(50)

def atr(df: pd.DataFrame, n: int = 14):
    pc = df["close"].shift(1)
    tr = pd.concat([
        df["high"] - df["low"],
        (df["high"] - pc).abs(),
        (df["low"] - pc).abs()
    ], axis=1).max(axis=1)
    return tr.ewm(alpha=1/n, adjust=False).mean().bfill()

def macd(s: pd.Series, fast=12, slow=26, signal=9):
    fast_line = ema(s, fast)
    slow_line = ema(s, slow)
    line = fast_line - slow_line
    sig = ema(line, signal)
    hist = line - sig
    return line, sig, hist

def bollinger(s: pd.Series, n=20, std_mult=2.0):
    mid = sma(s, n)
    sd = s.rolling(n).std()
    upper = mid + std_mult * sd
    lower = mid - std_mult * sd
    return upper.bfill(), mid.bfill(), lower.bfill()

def vwap(df: pd.DataFrame):
    typical = (df["high"] + df["low"] + df["close"]) / 3
    pv = typical * df["volume"]
    return (pv.cumsum() / df["volume"].cumsum().replace(0, np.nan)).ffill().bfill()

def adx(df: pd.DataFrame, n=14):
    high = df["high"]
    low = df["low"]
    up_move = high.diff()
    down_move = -low.diff()
    plus_dm = pd.Series(np.where((up_move > down_move) & (up_move > 0), up_move, 0.0), index=df.index)
    minus_dm = pd.Series(np.where((down_move > up_move) & (down_move > 0), down_move, 0.0), index=df.index)
    tr = atr(df, n)
    plus_di = 100 * (plus_dm.ewm(alpha=1/n, adjust=False).mean() / tr.replace(0, np.nan))
    minus_di = 100 * (minus_dm.ewm(alpha=1/n, adjust=False).mean() / tr.replace(0, np.nan))
    dx = ((plus_di - minus_di).abs() / (plus_di + minus_di).replace(0, np.nan)) * 100
    return dx.ewm(alpha=1/n, adjust=False).mean().fillna(0), plus_di.fillna(0), minus_di.fillna(0)
