package ru.inversion.tds;

import ru.inversion.dataset.DataSetRowEvent;
import ru.inversion.dataset.IDataSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static ru.inversion.dataset.DataSetRowEvent.RowOperationEnum.DELETE;
import static ru.inversion.dataset.DataSetRowEvent.RowOperationEnum.INSERT;

/** */
public interface ITreeDataSetItem<P> {

    /** */
    ITreeDataSet<P> getDataSet();

    /** */
    ITreeDataSetItem<P> getParentItem();

    /** */
    List<ITreeDataSetItem<P>> getChildrenList();

    /** */
    P getValue();

    /** */
    void setValue(P value);

    /** */
    void executeQuery();

    /**
     * Creates new child item for this parent.
     *
     * Must create item compatible with current implementation:
     * TreeDataSetItem creates TreeDataSetItem,
     * TreeViewItemAdapter creates TreeViewItemAdapter.
     *
     * Must not fire events.
     * Prefer not to attach item here; attach happens in addChildrenAt/addChild.
     */
    ITreeDataSetItem<P> newChildItem(P value);

    /**
     * Physically adds one child.
     *
     * Must attach child to this item.
     * Must not fire events.
     */
    void addChild(ITreeDataSetItem<P> child);

    /**
     * Physically inserts already-created child items at specified position.
     *
     * Must:
     *   - attach every child to this item;
     *   - preserve order of newItems;
     *   - insert at exact/safe position;
     *   - not fire rows/item/navigation events.
     */
    void addChildrenAt(
        int position,
        List<ITreeDataSetItem<P>> newItems
    );

    /**
     * Physically removes child items from this item.
     *
     * Must not fire rows/item/navigation events.
     */
    void removeChildren(Collection<ITreeDataSetItem<P>> items);

    /** */
    default boolean isRoot() {
        return getParentItem() == null;
    }

    /** */
    default boolean isLeaf() {
        List<ITreeDataSetItem<P>> children = getChildrenList();
        return children == null || children.isEmpty();
    }

    /** */
    default int getChildCount() {
        List<ITreeDataSetItem<P>> children = getChildrenList();
        return children == null ? 0 : children.size();
    }

    /** */
    default ITreeDataSetItem<P> getRootItem() {
        ITreeDataSetItem<P> parent = getParentItem();
        return parent == null ? this : parent.getRootItem();
    }

    /** */
    default ITreeDataSetItem<P> getChildAt(int childIndex) {

        List<ITreeDataSetItem<P>> children = getChildrenList();

        if (children == null || childIndex < 0 || childIndex >= children.size()) {
            return null;
        }

        return children.get(childIndex);
    }

    /** */
    default int getChildIndex(ITreeDataSetItem<P> child) {

        if (child == null || child.getParentItem() != this) {
            return -1;
        }

        List<ITreeDataSetItem<P>> children = getChildrenList();

        if (children == null || children.isEmpty()) {
            return -1;
        }

        return children.indexOf(child);
    }

    /** */
    default boolean setCurrentChildNum(int childNum) throws TreeDataSetException {

        ITreeDataSetItem<P> child = getChildAt(childNum);

        return child != null && child.setItemCurrent();
    }

    /** */
    default boolean setCurrentChild(ITreeDataSetItem<P> child) throws TreeDataSetException {

        if (child == null || child.getParentItem() != this) {
            return false;
        }

        return child.setItemCurrent();
    }

    /** */
    default boolean setItemCurrent() throws TreeDataSetException {
        return getDataSet().setCurrentItem(this);
    }

    /** */
    default boolean isCurrentItem() throws TreeDataSetException {
        return getDataSet().getCurrentItem() == this;
    }

    /** */
    default void insert(P value) throws TreeDataSetException {
        insert(
                Collections.singletonList(value),
                IDataSet.InsertRowModeEnum.LAST,
                false
        );
    }

    /** */
    default void insert(
            List<P> newRows,
            IDataSet.InsertRowModeEnum insertMode,
            boolean doSetToCurrent
    ) throws TreeDataSetException {

        if (newRows == null || newRows.isEmpty()) {
            return;
        }

        final List<ITreeDataSetItem<P>> newItems = new ArrayList<>(newRows.size());

        for (P value : newRows) {
            newItems.add(newChildItem(value));
        }

        final boolean wasLeaf = isLeaf();

        int position = wasLeaf ? 0 : resolveInsertPosition(insertMode);

        if (wasLeaf) {
            fireLeafEvent(true, false);
        }

        fireBeforeRowsEvent(INSERT, newItems, position);

        addChildrenAt(position, newItems);

        fireAfterRowsEvent(INSERT, newItems, position);

        if (wasLeaf) {
            fireLeafEvent(false, false);
        }

        if (doSetToCurrent && !newItems.isEmpty()) {
            newItems.get(0).setItemCurrent();
        }
    }

    /** */
    default int resolveInsertPosition(IDataSet.InsertRowModeEnum insertMode) {

        int childCount = getChildCount();

        if (childCount == 0) {
            return 0;
        }

        if (insertMode == null) {
            return childCount;
        }

        switch (insertMode) {
            case FIRST:
                return 0;

            case LAST:
                return childCount;

            case BEFORE_CURRENT: {
                ITreeDataSetItem<P> current = getDataSet().getCurrentItem();

                if (current != null && current.getParentItem() == this) {
                    int index = getChildIndex(current);
                    return index < 0 ? childCount : index;
                }

                return childCount;
            }

            case AFTER_CURRENT: {
                ITreeDataSetItem<P> current = getDataSet().getCurrentItem();

                if (current != null && current.getParentItem() == this) {
                    int index = getChildIndex(current);
                    return index < 0 ? childCount : index + 1;
                }

                return childCount;
            }

            default:
                return childCount;
        }
    }

    /** */
    default int remove(Predicate<P> predicate) throws TreeDataSetException {

        if (predicate == null) {
            return 0;
        }

        return removeItems(item -> predicate.test(item.getValue()));
    }

    /** */
    default boolean remove(ITreeDataSetItem<P> child) throws TreeDataSetException {

        if (child == null || child.getParentItem() != this) {
            return false;
        }

        return removeItems(item -> item == child) > 0;
    }

    /** */
    default boolean remove(int itemNum) throws TreeDataSetException {

        ITreeDataSetItem<P> child = getChildAt(itemNum);

        return child != null && remove(child);
    }

    /** */
    default boolean remove() throws TreeDataSetException {

        if (isRoot()) {
            return getDataSet().removeRootItem(this);
        }

        ITreeDataSetItem<P> parent = getParentItem();

        return parent != null && parent.remove(this);
    }

    /**
     * Removes children matching item-level predicate.
     */
    default int removeItems(Predicate<ITreeDataSetItem<P>> predicate) throws TreeDataSetException {

        if( predicate == null || isLeaf() )
            return 0;

        List<ITreeDataSetItem<P>> children = getChildrenList();

        if( children == null || children.isEmpty() )
            return 0;

        final int totalCount = children.size();

        int firstDeletedIndex = -1;
        int index = 0;

        List<ITreeDataSetItem<P>> removed = new LinkedList<>();

        ITreeDataSetItem<P> current = getDataSet().getCurrentItem();
        boolean currentRemoved = false;

        /*
         * Copy is important:
         * predicate/listeners must not break iteration over physical children list.
         */
        for( ITreeDataSetItem<P> child : new ArrayList<>(children) )
        {
            if( predicate.test(child) )
            {
                if( firstDeletedIndex == -1 )
                    firstDeletedIndex = index;

                removed.add(child);

                if( !currentRemoved && child.containsItem(current) )
                    currentRemoved = true;
            }

            index++;
        }

        if( removed.isEmpty() )
            return 0;

        final boolean willBecomeLeaf = removed.size() == totalCount;

        if( willBecomeLeaf )
            fireLeafEvent( true, true );

        fireBeforeRowsEvent( DELETE, removed, firstDeletedIndex );

        removeChildren(removed);

        final int removeCount = totalCount - getChildCount();

        if( currentRemoved )
        {
            final ITreeDataSetItem<P> newCurrent = selectCurrentAfterChildrenDelete(firstDeletedIndex);
            getDataSet().setCurrentItem(newCurrent);
        }

        fireAfterRowsEvent(DELETE, removed, firstDeletedIndex );

        if( willBecomeLeaf )
            fireLeafEvent(false, true);

        return removeCount;
    }

    /** */
    default ITreeDataSetItem<P> selectCurrentAfterChildrenDelete(int firstDeletedIndex) {

        if (!isLeaf()) {
            int index = firstDeletedIndex;

            if (index >= getChildCount()) {
                index = getChildCount() - 1;
            }

            if (index >= 0) {
                return getChildAt(index);
            }
        }

        return this;
    }

    /** */
    default boolean containsItem(ITreeDataSetItem<P> item) {

        if (item == null) {
            return false;
        }

        if (this == item) {
            return true;
        }

        if (isLeaf()) {
            return false;
        }

        for (ITreeDataSetItem<P> child : getChildrenList()) {
            if (child.containsItem(item)) {
                return true;
            }
        }

        return false;
    }

    /** */
    default boolean findItem(Predicate<P> predicate) throws TreeDataSetException {

        if (predicate == null) {
            return false;
        }

        if (predicate.test(getValue())) {
            return setItemCurrent();
        }

        if (isLeaf()) {
            return false;
        }

        for (ITreeDataSetItem<P> child : getChildrenList()) {
            if (child.findItem(predicate)) {
                return true;
            }
        }

        return false;
    }

    /** */
    default ITreeDataSetPath<P, ? extends ITreeDataSetItem<P>> getPath() {
        return new TreeDataSetPath<>(this);
    }

    /** */
    default boolean acceptVisitor(Function<ITreeDataSetItem<P>, Boolean> visitor) {

        if (visitor == null) {
            return false;
        }

        Boolean result = visitor.apply(this);

        if (Boolean.FALSE.equals(result)) {
            return false;
        }

        if (isLeaf()) {
            return true;
        }

        for (ITreeDataSetItem<P> child : getChildrenList()) {
            if (!child.acceptVisitor(visitor)) {
                return false;
            }
        }

        return true;
    }

    /** */
    default void fireBeforeRowsEvent(
            DataSetRowEvent.RowOperationEnum operation,
            List<ITreeDataSetItem<P>> items,
            int rowIndex
    ) {
        getDataSet().fireRowsEvent(
                new TreeDataSetRowsEvent<>(
                        getDataSet(),
                        true,
                        operation,
                        items,
                        rowIndex
                )
        );
    }

    /** */
    default void fireAfterRowsEvent(
            DataSetRowEvent.RowOperationEnum operation,
            List<ITreeDataSetItem<P>> items,
            int rowIndex
    ) {
        getDataSet().fireRowsEvent(
                new TreeDataSetRowsEvent<>(
                        getDataSet(),
                        false,
                        operation,
                        items,
                        rowIndex
                )
        );
    }

    /** */
    default void fireLeafEvent(
            boolean before,
            boolean newLeaf
    ) {
        getDataSet().fireItemEvent(
                new TreeDataSetItemEvent<>(this, before, newLeaf)
        );
    }

    /** */
    default void fillPreOrder(List<ITreeDataSetItem<P>> items) {

        if (items == null) {
            return;
        }

        items.add(this);

        if (isLeaf()) {
            return;
        }

        for (ITreeDataSetItem<P> child : getChildrenList()) {
            child.fillPreOrder(items);
        }
    }
}