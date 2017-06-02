/*
 * This file is part of UnplannedDescent, licensed under the MIT License (MIT).
 *
 * Copyright (c) TechShroom Studios <https://techshroom.com>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.techshroom.midishapes.fx;

import static com.google.common.base.Preconditions.checkElementIndex;
import static com.google.common.base.Preconditions.checkState;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableRangeMap;
import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;

import javafx.collections.ListChangeListener;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import javafx.collections.ObservableListBase;

/**
 * View-only observable list that is made of other sub-lists.
 */
public class ConcatObservableList<E> extends ObservableListBase<E> {

    private static final class ListWithOffset<E> {

        private final ObservableList<E> list;
        private final int offset;

        ListWithOffset(ObservableList<E> list, int offset) {
            this.list = list;
            this.offset = offset;
        }

    }

    private final List<ObservableList<? extends E>> sourceLists;
    private final Map<ObservableList<? extends E>, Integer> listToOffsetMap = new IdentityHashMap<>();
    private int realSize;
    private RangeMap<Integer, ListWithOffset<? extends E>> listRangeMap;

    @SafeVarargs
    public ConcatObservableList(ObservableList<? extends E>... source) {
        // validate lists
        checkState(source.length == Stream.of(source)
                .mapToInt(System::identityHashCode)
                .distinct()
                .count(), "duplicate lists in source");

        sourceLists = ImmutableList.copyOf(source);
        sourceLists.forEach(l -> {
            l.addListener((ListChangeListener<E>) change -> {
                // must represent the appropriate view in changes
                reconstructRanges();
                fireChangeListener(change);
            });
        });
    }

    private void fireChangeListener(Change<? extends E> change) {
        // We need to adapt all of the indexes in change to the list's offset
        int offset = listToOffsetMap.get(change.getList());
        beginChange();
        while (change.next()) {
            if (change.wasPermutated()) {
                ContiguousSet<Integer> permRange = ContiguousSet.create(Range.closedOpen(change.getFrom(), change.getTo()), DiscreteDomain.integers());
                int[] permArray = new int[permRange.size()];
                for (int i : permRange) {
                    permArray[i - change.getFrom()] = change.getPermutation(i) + offset;
                }
                nextPermutation(change.getFrom() + offset, change.getTo() + offset, permArray);
            } else if (change.wasAdded()) {
                nextAdd(change.getFrom() + offset, change.getTo() + offset);
            } else if (change.wasRemoved()) {
                nextRemove(change.getFrom() + offset, change.getRemoved());
            } else if (change.wasUpdated()) {
                nextUpdate(change.getFrom() + offset);
            }
        }
        endChange();
    }

    private void reconstructRanges() {
        ImmutableRangeMap.Builder<Integer, ListWithOffset<? extends E>> b = ImmutableRangeMap.builder();
        int lastIndex = 0;
        for (ObservableList<? extends E> list : sourceLists) {
            int nextLast = lastIndex + list.size();
            listToOffsetMap.put(list, lastIndex);
            if (list.isEmpty()) {
                continue;
            }
            b.put(Range.closedOpen(lastIndex, nextLast), new ListWithOffset<>(list, lastIndex));
            lastIndex = nextLast;
        }
        listRangeMap = b.build();
        realSize = lastIndex;
    }

    @Override
    public E get(int index) {
        checkElementIndex(index, realSize);
        ListWithOffset<? extends E> lwo = listRangeMap.get(index);
        return lwo.list.get(index - lwo.offset);
    }

    @Override
    public int size() {
        return realSize;
    }

}
