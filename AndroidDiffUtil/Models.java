/**
 * Eugene W. Myers的算法，git的diff也源于这个算法 
 * <http://www.xmailserver.org/diff2.pdf> 
 * <https://blog.jcoglan.com/2017/02/12/the-myers-diff-algorithm-part-1/>
 * 三个基础model，前两个为算法相关
**/


/**
 * Snakes represent a match between two lists. It is optionally prefixed or postfixed with an
 * add or remove operation. See the Myers' paper for details.
 */
static class Snake {
    /**
     * Position in the old list
     */
    int x;
    /**
     * Position in the new list
     */
    int y;
    /**
     * Number of matches. Might be 0.
     */
    int size;
    /**
     * If true, this is a removal from the original list followed by {@code size} matches.
     * If false, this is an addition from the new list followed by {@code size} matches.
     */
    boolean removal;
    /**
     * If true, the addition or removal is at the end of the snake.
     * If false, the addition or removal is at the beginning of the snake.
     */
    boolean reverse;
}



/**
 * Represents a range in two lists that needs to be solved.
 * <p>
 * This internal class is used when running Myers' algorithm without recursion.
 */
static class Range {
    int oldListStart, oldListEnd;
    int newListStart, newListEnd;
    public Range() {
    }
    public Range(int oldListStart, int oldListEnd, int newListStart, int newListEnd) {
        this.oldListStart = oldListStart;
        this.oldListEnd = oldListEnd;
        this.newListStart = newListStart;
        this.newListEnd = newListEnd;
    }
}



// DiffResult使用，触发patchUpdate

/**
 * Represents an update that we skipped because it was a move.
 * <p>
 * When an update is skipped, it is tracked as other updates are dispatched until the matching
 * add/remove operation is found at which point the tracked position is used to dispatch the
 * update.
 */
private static class PostponedUpdate {
    int posInOwnerList;
    int currentPos;
    boolean removal;
    public PostponedUpdate(int posInOwnerList, int currentPos, boolean removal) {
        this.posInOwnerList = posInOwnerList;
        this.currentPos = currentPos;
        this.removal = removal;
    }
}