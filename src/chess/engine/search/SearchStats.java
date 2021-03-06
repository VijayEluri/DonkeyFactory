/* $Id$ */

package chess.engine.search;

/**
 * @author Joshua Levine <levinester@gmail.com>
 * @version $Revision$ $Name$ $Date$
 */
public class SearchStats {
  private static final String SPACES = "                                             ";

  public double time;
  public int nodes;
  public int pvNodes;
  public int zwNodes;
  public int qNodes;
  public int evals;
  public int reduceBoring;

  public int hashMisses;
  public int hardHashHits;
  public int qHashHits;
  public int softHashHits;

  public int reducePrune;
  public int reduceMargin;

  public int endgameExtensions;
  public int checkExtensions;
  public int doubleCheckExtensions;
  public int pawnPushExtensions;
  public int recaptureExtensions;
  public int threatExtensions;
  public int reduceFutile;


  public String toString() {
    return new StringBuilder()
            .append(" T: ").append(pad(time, 8))
            .append("  E/S: ").append(pad((int) ((double) evals / time), 8))
            .append("  N/S: ").append(pad((int) ((double) nodes / time), 8))
            .append("\nSearch      |   N: ").append(pad(nodes, 8))
            .append("    E: ").append(pad(evals, 8))
            .append("  PVN: ").append(pad(pvNodes, 8))
            .append("  ZWN: ").append(pad(zwNodes, 8))
            .append("   QN: ").append(pad(qNodes, 8))
            .append("\nHash        | HHH: ").append(pad(hardHashHits, 8))
            .append("  SHH: ").append(pad(softHashHits, 8))
            .append("  QHH: ").append(pad(qHashHits, 8))
            .append("  Miss: ").append(pad(hashMisses, 8))
            .append("\nExtensions  |  EX: ").append(pad(endgameExtensions, 8))
            .append("   PX: ").append(pad(pawnPushExtensions, 8))
            .append("   CX: ").append(pad(checkExtensions, 8))
            .append("   DCX: ").append(pad(doubleCheckExtensions, 8))
            .append("   RX: ").append(pad(recaptureExtensions, 8))
            .append("   TX: ").append(pad(threatExtensions, 8))
            .append("\nReductions  |  B: ").append(pad(reduceBoring, 8))
            .append("    M: ").append(pad(reduceMargin, 8))
            .append("    P: ").append(pad(reducePrune, 8))
            .append("    F: ").append(pad(reduceFutile, 8)).toString();
  }

  private String pad(Number value, int length) {
    StringBuffer buffer = new StringBuffer("" + value);
    return buffer.insert(0, SPACES.substring(0, Math.max(1, length - buffer.length()))).toString();
  }


  public void reset() {
    time = 0;
    nodes = 0;
    evals = 0;
    reduceBoring = 0;

    hashMisses = 0;
    hardHashHits = 0;
    qHashHits = 0;
    softHashHits = 0;

    reducePrune = 0;
    reduceMargin = 0;

    endgameExtensions = 0;
    checkExtensions = 0;
    doubleCheckExtensions = 0;
    pawnPushExtensions = 0;
    recaptureExtensions = 0;
  }
}

