// Myers' algorithm uses two lists as axis labels. In DiffUtil's implementation, `x` axis is
// used for old list and `y` axis is used for new list.
/**
 * Calculates the list of update operations that can covert one list into the other one.
 *
 * @param cb The callback that acts as a gateway to the backing list data
 *
 * @return A DiffResult that contains the information about the edit sequence to convert the
 * old list into the new list.
 */
public static DiffResult calculateDiff(Callback cb) {
    return calculateDiff(cb, true);
}
/**
 * Calculates the list of update operations that can covert one list into the other one.
 * <p>
 * If your old and new lists are sorted by the same constraint and items never move (swap
 * positions), you can disable move detection which takes <code>O(N^2)</code> time where
 * N is the number of added, moved, removed items.
 *
 * @param cb The callback that acts as a gateway to the backing list data
 * @param detectMoves True if DiffUtil should try to detect moved items, false otherwise.
 *
 * @return A DiffResult that contains the information about the edit sequence to convert the
 * old list into the new list.
 */
public static DiffResult calculateDiff(Callback cb, boolean detectMoves) {
    final int oldSize = cb.getOldListSize();
    final int newSize = cb.getNewListSize();
    final List<Snake> snakes = new ArrayList<>();
    // instead of a recursive implementation, we keep our own stack to avoid potential stack
    // overflow exceptions
    final List<Range> stack = new ArrayList<>();
    stack.add(new Range(0, oldSize, 0, newSize));
    final int max = oldSize + newSize + Math.abs(oldSize - newSize);
    // allocate forward and backward k-lines. K lines are diagonal lines in the matrix. (see the
    // paper for details)
    // These arrays lines keep the max reachable position for each k-line.
    final int[] forward = new int[max * 2];
    final int[] backward = new int[max * 2];
    // We pool the ranges to avoid allocations for each recursive call.
    final List<Range> rangePool = new ArrayList<>();
    while (!stack.isEmpty()) {
        final Range range = stack.remove(stack.size() - 1);
        final Snake snake = diffPartial(cb, range.oldListStart, range.oldListEnd,
                range.newListStart, range.newListEnd, forward, backward, max);
        if (snake != null) {
            if (snake.size > 0) {
                snakes.add(snake);
            }
            // offset the snake to convert its coordinates from the Range's area to global
            snake.x += range.oldListStart;
            snake.y += range.newListStart;
            // add new ranges for left and right
            final Range left = rangePool.isEmpty() ? new Range() : rangePool.remove(
                    rangePool.size() - 1);
            left.oldListStart = range.oldListStart;
            left.newListStart = range.newListStart;
            if (snake.reverse) {
                left.oldListEnd = snake.x;
                left.newListEnd = snake.y;
            } else {
                if (snake.removal) {
                    left.oldListEnd = snake.x - 1;
                    left.newListEnd = snake.y;
                } else {
                    left.oldListEnd = snake.x;
                    left.newListEnd = snake.y - 1;
                }
            }
            stack.add(left);
            // re-use range for right
            //noinspection UnnecessaryLocalVariable
            final Range right = range;
            if (snake.reverse) {
                if (snake.removal) {
                    right.oldListStart = snake.x + snake.size + 1;
                    right.newListStart = snake.y + snake.size;
                } else {
                    right.oldListStart = snake.x + snake.size;
                    right.newListStart = snake.y + snake.size + 1;
                }
            } else {
                right.oldListStart = snake.x + snake.size;
                right.newListStart = snake.y + snake.size;
            }
            stack.add(right);
        } else {
            rangePool.add(range);
        }
    }
    // sort snakes
    Collections.sort(snakes, SNAKE_COMPARATOR);
    return new DiffResult(cb, snakes, forward, backward, detectMoves);
}


private static Snake diffPartial(Callback cb, int startOld, int endOld,
        int startNew, int endNew, int[] forward, int[] backward, int kOffset) {
    final int oldSize = endOld - startOld;
    final int newSize = endNew - startNew;
    if (endOld - startOld < 1 || endNew - startNew < 1) {
        return null;
    }
    final int delta = oldSize - newSize;
    final int dLimit = (oldSize + newSize + 1) / 2;
    Arrays.fill(forward, kOffset - dLimit - 1, kOffset + dLimit + 1, 0);
    Arrays.fill(backward, kOffset - dLimit - 1 + delta, kOffset + dLimit + 1 + delta, oldSize);
    final boolean checkInFwd = delta % 2 != 0;
    for (int d = 0; d <= dLimit; d++) {
        for (int k = -d; k <= d; k += 2) {
            // find forward path
            // we can reach k from k - 1 or k + 1. Check which one is further in the graph
            int x;
            final boolean removal;
            if (k == -d || k != d && forward[kOffset + k - 1] < forward[kOffset + k + 1]) {
                x = forward[kOffset + k + 1];
                removal = false;
            } else {
                x = forward[kOffset + k - 1] + 1;
                removal = true;
            }
            // set y based on x
            int y = x - k;
            // move diagonal as long as items match
            while (x < oldSize && y < newSize
                    && cb.areItemsTheSame(startOld + x, startNew + y)) {
                x++;
                y++;
            }
            forward[kOffset + k] = x;
            if (checkInFwd && k >= delta - d + 1 && k <= delta + d - 1) {
                if (forward[kOffset + k] >= backward[kOffset + k]) {
                    Snake outSnake = new Snake();
                    outSnake.x = backward[kOffset + k];
                    outSnake.y = outSnake.x - k;
                    outSnake.size = forward[kOffset + k] - backward[kOffset + k];
                    outSnake.removal = removal;
                    outSnake.reverse = false;
                    return outSnake;
                }
            }
        }
        for (int k = -d; k <= d; k += 2) {
            // find reverse path at k + delta, in reverse
            final int backwardK = k + delta;
            int x;
            final boolean removal;
            if (backwardK == d + delta || backwardK != -d + delta
                    && backward[kOffset + backwardK - 1] < backward[kOffset + backwardK + 1]) {
                x = backward[kOffset + backwardK - 1];
                removal = false;
            } else {
                x = backward[kOffset + backwardK + 1] - 1;
                removal = true;
            }
            // set y based on x
            int y = x - backwardK;
            // move diagonal as long as items match
            while (x > 0 && y > 0
                    && cb.areItemsTheSame(startOld + x - 1, startNew + y - 1)) {
                x--;
                y--;
            }
            backward[kOffset + backwardK] = x;
            if (!checkInFwd && k + delta >= -d && k + delta <= d) {
                if (forward[kOffset + backwardK] >= backward[kOffset + backwardK]) {
                    Snake outSnake = new Snake();
                    outSnake.x = backward[kOffset + backwardK];
                    outSnake.y = outSnake.x - backwardK;
                    outSnake.size =
                            forward[kOffset + backwardK] - backward[kOffset + backwardK];
                    outSnake.removal = removal;
                    outSnake.reverse = true;
                    return outSnake;
                }
            }
        }
    }
    throw new IllegalStateException("DiffUtil hit an unexpected case while trying to calculate"
            + " the optimal path. Please make sure your data is not changing during the"
            + " diff calculation.");
}