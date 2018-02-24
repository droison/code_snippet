

// 用来调整分块部分处理的先后顺序
private static final Comparator<Snake> SNAKE_COMPARATOR = new Comparator<Snake>() {
    @Override
    public int compare(Snake o1, Snake o2) {
        int cmpX = o1.x - o2.x;
        return cmpX == 0 ? o1.y - o2.y : cmpX;
    }
};




/**
 * 叫Callback在词义上不是很好，DataSource更合意一些
 * A Callback class used by DiffUtil while calculating the diff between two lists.
 */
public abstract static class Callback {
    /**
     * Returns the size of the old list.
     *
     * @return The size of the old list.
     */
    public abstract int getOldListSize();
    /**
     * Returns the size of the new list.
     *
     * @return The size of the new list.
     */
    public abstract int getNewListSize();
    /**
     * Called by the DiffUtil to decide whether two object represent the same Item.
     * <p>
     * For example, if your items have unique ids, this method should check their id equality.
     *
     * @param oldItemPosition The position of the item in the old list
     * @param newItemPosition The position of the item in the new list
     * @return True if the two items represent the same object or false if they are different.
     */
    public abstract boolean areItemsTheSame(int oldItemPosition, int newItemPosition);
    /**
     * Called by the DiffUtil when it wants to check whether two items have the same data.
     * DiffUtil uses this information to detect if the contents of an item has changed.
     * <p>
     * DiffUtil uses this method to check equality instead of {@link Object#equals(Object)}
     * so that you can change its behavior depending on your UI.
     * For example, if you are using DiffUtil with a
     * {@link android.support.v7.widget.RecyclerView.Adapter RecyclerView.Adapter}, you should
     * return whether the items' visual representations are the same.
     * <p>
     * This method is called only if {@link #areItemsTheSame(int, int)} returns
     * {@code true} for these items.
     *
     * @param oldItemPosition The position of the item in the old list
     * @param newItemPosition The position of the item in the new list which replaces the
     *                        oldItem
     * @return True if the contents of the items are the same or false if they are different.
     */
    public abstract boolean areContentsTheSame(int oldItemPosition, int newItemPosition);
    /**
     * When {@link #areItemsTheSame(int, int)} returns {@code true} for two items and
     * {@link #areContentsTheSame(int, int)} returns false for them, DiffUtil
     * calls this method to get a payload about the change.
     * <p>
     * For example, if you are using DiffUtil with {@link RecyclerView}, you can return the
     * particular field that changed in the item and your
     * {@link android.support.v7.widget.RecyclerView.ItemAnimator ItemAnimator} can use that
     * information to run the correct animation.
     * <p>
     * Default implementation returns {@code null}.
     *
     * @param oldItemPosition The position of the item in the old list
     * @param newItemPosition The position of the item in the new list
     *
     * @return A payload object that represents the change between the two items.
     */
    @Nullable
    public Object getChangePayload(int oldItemPosition, int newItemPosition) {
        return null;
    }
}