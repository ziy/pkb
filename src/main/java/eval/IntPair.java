package eval;

public class IntPair {

  private int k;

  private int v;

  public IntPair(int k, int v) {
    super();
    this.k = k;
    this.v = v;
  }

  public int getK() {
    return k;
  }

  public int getV() {
    return v;
  }

  @Override
  public String toString() {
    return "[" + k + ", " + v + ")";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + k;
    result = prime * result + v;
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    IntPair other = (IntPair) obj;
    if (k != other.k)
      return false;
    if (v != other.v)
      return false;
    return true;
  }

}
