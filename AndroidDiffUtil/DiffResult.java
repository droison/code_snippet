/**
 * This class holds the information about the result of a
 * {@link DiffUtil#calculateDiff(Callback, boolean)} call.
 * <p>
 * You can consume the updates in a DiffResult via
 * {@link #dispatchUpdatesTo(ListUpdateCallback)} or directly stream the results into a
 * {@link RecyclerView.Adapter} via {@link #dispatchUpdatesTo(RecyclerView.Adapter)}.
 */
public static class DiffResult {
    /**
     * While reading the flags below, keep in mind that when multiple items move in a list,
     * Myers's may pick any of them as the anchor item and consider that one NOT_CHANGED while
     * picking others as additions and removals. This is completely fine as we later detect
     * all moves.
     * <p>
     * Below, when an item is mentioned to stay in the same "location", it means we won't
     * dispatch a move/add/remove for it, it DOES NOT mean the item is still in the same
     * position.
     */
    // item stayed the same.
    private static final int FLAG_NOT_CHANGED = 1;
    // item stayed in the same location but changed.
    private static final int FLAG_CHANGED = FLAG_NOT_CHANGED << 1;
    // Item has moved and also changed.
    private static final int FLAG_MOVED_CHANGED = FLAG_CHANGED << 1;
    // Item has moved but did not change.
    private static final int FLAG_MOVED_NOT_CHANGED = FLAG_MOVED_CHANGED << 1;
    // Ignore this update.
    // If this is an addition from the new list, it means the item is actually removed from an
    // earlier position and its move will be dispatched when we process the matching removal
    // from the old list.
    // If this is a removal from the old list, it means the item is actually added back to an
    // earlier index in the new list and we'll dispatch its move when we are processing that
    // addition.
    private static final int FLAG_IGNORE = FLAG_MOVED_NOT_CHANGED << 1;
    // since we are re-using the int arrays that were created in the Myers' step, we mask
    // change flags
    private static final int FLAG_OFFSET = 5;
    private static final int FLAG_MASK = (1 << FLAG_OFFSET) - 1;
    // The Myers' snakes. At this point, we only care about their diagonal sections.
    private final List<Snake> mSnakes;
    // The list to keep oldItemStatuses. As we traverse old items, we assign flags to them
    // which also includes whether they were a real removal or a move (and its new index).
    private final int[] mOldItemStatuses;
    // The list to keep newItemStatuses. As we traverse new items, we assign flags to them
    // which also includes whether they were a real addition or a move(and its old index).
    private final int[] mNewItemStatuses;
    // The callback that was given to calcualte diff method.
    private final Callback mCallback;
    private final int mOldListSize;
    private final int mNewListSize;
    private final boolean mDetectMoves;
    /**
     * @param callback The callback that was used to calculate the diff
     * @param snakes The list of Myers' snakes
     * @param oldItemStatuses An int[] that can be re-purposed to keep metadata
     * @param newItemStatuses An int[] that can be re-purposed to keep metadata
     * @param detectMoves True if this DiffResult will try to detect moved items
     */
    DiffResult(Callback callback, List<Snake> snakes, int[] oldItemStatuses,
            int[] newItemStatuses, boolean detectMoves) {
        mSnakes = snakes;
        mOldItemStatuses = oldItemStatuses;
        mNewItemStatuses = newItemStatuses;
        Arrays.fill(mOldItemStatuses, 0);
        Arrays.fill(mNewItemStatuses, 0);
        mCallback = callback;
        mOldListSize = callback.getOldListSize();
        mNewListSize = callback.getNewListSize();
        mDetectMoves = detectMoves;
        addRootSnake(); // 注释很清楚了，为了循环运行，增加一个头部，类似OC NSNotFound
        findMatchingItems();
    }
    /**
     * We always add a Snake to 0/0 so that we can run loops from end to beginning and be done
     * when we run out of snakes.
     */
    private void addRootSnake() {
        Snake firstSnake = mSnakes.isEmpty() ? null : mSnakes.get(0);
        if (firstSnake == null || firstSnake.x != 0 || firstSnake.y != 0) {
            Snake root = new Snake();
            root.x = 0;
            root.y = 0;
            root.removal = false;
            root.size = 0;
            root.reverse = false;
            mSnakes.add(0, root);
        }
    }
    /**
     * This method traverses each addition / removal and tries to match it to a previous
     * removal / addition. This is how we detect move operations.
     * <p>
     * This class also flags whether an item has been changed or not.
     * <p>
     * DiffUtil does this pre-processing so that if it is running on a big list, it can be moved
     * to background thread where most of the expensive stuff will be calculated and kept in
     * the statuses maps. DiffResult uses this pre-calculated information while dispatching
     * the updates (which is probably being called on the main thread).
     */
    private void findMatchingItems() {
        int posOld = mOldListSize;
        int posNew = mNewListSize;
        // traverse the matrix from right bottom to 0,0.
        for (int i = mSnakes.size() - 1; i >= 0; i--) {
            final Snake snake = mSnakes.get(i);
            final int endX = snake.x + snake.size;
            final int endY = snake.y + snake.size;
            if (mDetectMoves) {
                while (posOld > endX) {
                    // this is a removal. Check remaining snakes to see if this was added before
                    findAddition(posOld, posNew, i);
                    posOld--;
                }
                while (posNew > endY) {
                    // this is an addition. Check remaining snakes to see if this was removed
                    // before
                    findRemoval(posOld, posNew, i);
                    posNew--;
                }
            }
            for (int j = 0; j < snake.size; j++) {
                // matching items. Check if it is changed or not
                final int oldItemPos = snake.x + j;
                final int newItemPos = snake.y + j;
                final boolean theSame = mCallback
                        .areContentsTheSame(oldItemPos, newItemPos);
                final int changeFlag = theSame ? FLAG_NOT_CHANGED : FLAG_CHANGED;
                mOldItemStatuses[oldItemPos] = (newItemPos << FLAG_OFFSET) | changeFlag;
                mNewItemStatuses[newItemPos] = (oldItemPos << FLAG_OFFSET) | changeFlag;
            }
            posOld = snake.x;
            posNew = snake.y;
        }
    }
    private void findAddition(int x, int y, int snakeIndex) {
        if (mOldItemStatuses[x - 1] != 0) {
            return; // already set by a latter item
        }
        findMatchingItem(x, y, snakeIndex, false);
    }
    private void findRemoval(int x, int y, int snakeIndex) {
        if (mNewItemStatuses[y - 1] != 0) {
            return; // already set by a latter item
        }
        findMatchingItem(x, y, snakeIndex, true);
    }
    /**
     * Finds a matching item that is before the given coordinates in the matrix
     * (before : left and above).
     *
     * @param x The x position in the matrix (position in the old list)
     * @param y The y position in the matrix (position in the new list)
     * @param snakeIndex The current snake index
     * @param removal True if we are looking for a removal, false otherwise
     *
     * @return True if such item is found.
     */
    private boolean findMatchingItem(final int x, final int y, final int snakeIndex,
            final boolean removal) {
        final int myItemPos;
        int curX;
        int curY;
        if (removal) {
            myItemPos = y - 1;
            curX = x;
            curY = y - 1;
        } else {
            myItemPos = x - 1;
            curX = x - 1;
            curY = y;
        }
        for (int i = snakeIndex; i >= 0; i--) {
            final Snake snake = mSnakes.get(i);
            final int endX = snake.x + snake.size;
            final int endY = snake.y + snake.size;
            if (removal) {
                // check removals for a match
                for (int pos = curX - 1; pos >= endX; pos--) {
                    if (mCallback.areItemsTheSame(pos, myItemPos)) {
                        // found!
                        final boolean theSame = mCallback.areContentsTheSame(pos, myItemPos);
                        final int changeFlag = theSame ? FLAG_MOVED_NOT_CHANGED
                                : FLAG_MOVED_CHANGED;
                        mNewItemStatuses[myItemPos] = (pos << FLAG_OFFSET) | FLAG_IGNORE;
                        mOldItemStatuses[pos] = (myItemPos << FLAG_OFFSET) | changeFlag;
                        return true;
                    }
                }
            } else {
                // check for additions for a match
                for (int pos = curY - 1; pos >= endY; pos--) {
                    if (mCallback.areItemsTheSame(myItemPos, pos)) {
                        // found
                        final boolean theSame = mCallback.areContentsTheSame(myItemPos, pos);
                        final int changeFlag = theSame ? FLAG_MOVED_NOT_CHANGED
                                : FLAG_MOVED_CHANGED;
                        mOldItemStatuses[x - 1] = (pos << FLAG_OFFSET) | FLAG_IGNORE;
                        mNewItemStatuses[pos] = ((x - 1) << FLAG_OFFSET) | changeFlag;
                        return true;
                    }
                }
            }
            curX = snake.x;
            curY = snake.y;
        }
        return false;
    }
    /**
     * Dispatches the update events to the given adapter.
     * <p>
     * For example, if you have an {@link android.support.v7.widget.RecyclerView.Adapter Adapter}
     * that is backed by a {@link List}, you can swap the list with the new one then call this
     * method to dispatch all updates to the RecyclerView.
     * <pre>
     *     List oldList = mAdapter.getData();
     *     DiffResult result = DiffUtil.calculateDiff(new MyCallback(oldList, newList));
     *     mAdapter.setData(newList);
     *     result.dispatchUpdatesTo(mAdapter);
     * </pre>
     * <p>
     * Note that the RecyclerView requires you to dispatch adapter updates immediately when you
     * change the data (you cannot defer {@code notify*} calls). The usage above adheres to this
     * rule because updates are sent to the adapter right after the backing data is changed,
     * before RecyclerView tries to read it.
     * <p>
     * On the other hand, if you have another
     * {@link android.support.v7.widget.RecyclerView.AdapterDataObserver AdapterDataObserver}
     * that tries to process events synchronously, this may confuse that observer because the
     * list is instantly moved to its final state while the adapter updates are dispatched later
     * on, one by one. If you have such an
     * {@link android.support.v7.widget.RecyclerView.AdapterDataObserver AdapterDataObserver},
     * you can use
     * {@link #dispatchUpdatesTo(ListUpdateCallback)} to handle each modification
     * manually.
     *
     * @param adapter A RecyclerView adapter which was displaying the old list and will start
     *                displaying the new list.
     */
    public void dispatchUpdatesTo(final RecyclerView.Adapter adapter) {
        dispatchUpdatesTo(new ListUpdateCallback() {
            @Override
            public void onInserted(int position, int count) {
                adapter.notifyItemRangeInserted(position, count);
            }
            @Override
            public void onRemoved(int position, int count) {
                adapter.notifyItemRangeRemoved(position, count);
            }
            @Override
            public void onMoved(int fromPosition, int toPosition) {
                adapter.notifyItemMoved(fromPosition, toPosition);
            }
            @Override
            public void onChanged(int position, int count, Object payload) {
                adapter.notifyItemRangeChanged(position, count, payload);
            }
        });
    }
    /**
     * Dispatches update operations to the given Callback.
     * <p>
     * These updates are atomic such that the first update call effects every update call that
     * comes after it (the same as RecyclerView).
     *
     * @param updateCallback The callback to receive the update operations.
     * @see #dispatchUpdatesTo(RecyclerView.Adapter)
     */
    public void dispatchUpdatesTo(ListUpdateCallback updateCallback) {
        final BatchingListUpdateCallback batchingCallback;
        if (updateCallback instanceof BatchingListUpdateCallback) {
            batchingCallback = (BatchingListUpdateCallback) updateCallback;
        } else {
            batchingCallback = new BatchingListUpdateCallback(updateCallback);
            // replace updateCallback with a batching callback and override references to
            // updateCallback so that we don't call it directly by mistake
            //noinspection UnusedAssignment
            updateCallback = batchingCallback;
        }
        // These are add/remove ops that are converted to moves. We track their positions until
        // their respective update operations are processed.
        final List<PostponedUpdate> postponedUpdates = new ArrayList<>();
        int posOld = mOldListSize;
        int posNew = mNewListSize;
        for (int snakeIndex = mSnakes.size() - 1; snakeIndex >= 0; snakeIndex--) {
            final Snake snake = mSnakes.get(snakeIndex);
            final int snakeSize = snake.size;
            final int endX = snake.x + snakeSize;
            final int endY = snake.y + snakeSize;
            if (endX < posOld) {
                dispatchRemovals(postponedUpdates, batchingCallback, endX, posOld - endX, endX);
            }
            if (endY < posNew) {
                dispatchAdditions(postponedUpdates, batchingCallback, endX, posNew - endY,
                        endY);
            }
            for (int i = snakeSize - 1; i >= 0; i--) {
                if ((mOldItemStatuses[snake.x + i] & FLAG_MASK) == FLAG_CHANGED) {
                    batchingCallback.onChanged(snake.x + i, 1,
                            mCallback.getChangePayload(snake.x + i, snake.y + i));
                }
            }
            posOld = snake.x;
            posNew = snake.y;
        }
        batchingCallback.dispatchLastEvent();
    }
    private static PostponedUpdate removePostponedUpdate(List<PostponedUpdate> updates,
            int pos, boolean removal) {
        for (int i = updates.size() - 1; i >= 0; i--) {
            final PostponedUpdate update = updates.get(i);
            if (update.posInOwnerList == pos && update.removal == removal) {
                updates.remove(i);
                for (int j = i; j < updates.size(); j++) {
                    // offset other ops since they swapped positions
                    updates.get(j).currentPos += removal ? 1 : -1;
                }
                return update;
            }
        }
        return null;
    }
    private void dispatchAdditions(List<PostponedUpdate> postponedUpdates,
            ListUpdateCallback updateCallback, int start, int count, int globalIndex) {
        if (!mDetectMoves) {
            updateCallback.onInserted(start, count);
            return;
        }
        for (int i = count - 1; i >= 0; i--) {
            int status = mNewItemStatuses[globalIndex + i] & FLAG_MASK;
            switch (status) {
                case 0: // real addition
                    updateCallback.onInserted(start, 1);
                    for (PostponedUpdate update : postponedUpdates) {
                        update.currentPos += 1;
                    }
                    break;
                case FLAG_MOVED_CHANGED:
                case FLAG_MOVED_NOT_CHANGED:
                    final int pos = mNewItemStatuses[globalIndex + i] >> FLAG_OFFSET;
                    final PostponedUpdate update = removePostponedUpdate(postponedUpdates, pos,
                            true);
                    // the item was moved from that position
                    //noinspection ConstantConditions
                    updateCallback.onMoved(update.currentPos, start);
                    if (status == FLAG_MOVED_CHANGED) {
                        // also dispatch a change
                        updateCallback.onChanged(start, 1,
                                mCallback.getChangePayload(pos, globalIndex + i));
                    }
                    break;
                case FLAG_IGNORE: // ignoring this
                    postponedUpdates.add(new PostponedUpdate(globalIndex + i, start, false));
                    break;
                default:
                    throw new IllegalStateException(
                            "unknown flag for pos " + (globalIndex + i) + " " + Long
                                    .toBinaryString(status));
            }
        }
    }
    private void dispatchRemovals(List<PostponedUpdate> postponedUpdates,
            ListUpdateCallback updateCallback, int start, int count, int globalIndex) {
        if (!mDetectMoves) {
            updateCallback.onRemoved(start, count);
            return;
        }
        for (int i = count - 1; i >= 0; i--) {
            final int status = mOldItemStatuses[globalIndex + i] & FLAG_MASK;
            switch (status) {
                case 0: // real removal
                    updateCallback.onRemoved(start + i, 1);
                    for (PostponedUpdate update : postponedUpdates) {
                        update.currentPos -= 1;
                    }
                    break;
                case FLAG_MOVED_CHANGED:
                case FLAG_MOVED_NOT_CHANGED:
                    final int pos = mOldItemStatuses[globalIndex + i] >> FLAG_OFFSET;
                    final PostponedUpdate update = removePostponedUpdate(postponedUpdates, pos,
                            false);
                    // the item was moved to that position. we do -1 because this is a move not
                    // add and removing current item offsets the target move by 1
                    //noinspection ConstantConditions
                    updateCallback.onMoved(start + i, update.currentPos - 1);
                    if (status == FLAG_MOVED_CHANGED) {
                        // also dispatch a change
                        updateCallback.onChanged(update.currentPos - 1, 1,
                                mCallback.getChangePayload(globalIndex + i, pos));
                    }
                    break;
                case FLAG_IGNORE: // ignoring this
                    postponedUpdates.add(new PostponedUpdate(globalIndex + i, start + i, true));
                    break;
                default:
                    throw new IllegalStateException(
                            "unknown flag for pos " + (globalIndex + i) + " " + Long
                                    .toBinaryString(status));
            }
        }
    }
    @VisibleForTesting
    List<Snake> getSnakes() {
        return mSnakes;
    }
}