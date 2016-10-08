/*
 * ARX: Powerful Data Anonymization
 * Copyright 2012 - 2016 Fabian Prasser, Florian Kohlmayer and contributors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.deidentifier.arx.risk.msu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.carrotsearch.hppc.IntOpenHashSet;

public class SUDA2 {

    /** Debug data */
    private int           calls = 0;
    /** The data */
    private final int[][] data;
    /** Number of columns */
    private final int     columns;
    /** The result */
    private SUDA2Result   result;

    /**
     * Constructor
     * @param data
     */
    public SUDA2(int[][] data) {
        
        // Check
        this.check(data);
        
        // Init
        this.data = data;
        this.columns = data[0].length;
    }

    /**
     * Executes the SUDA2 algorithm
     * 
     * @param maxK If maxK <= 0, maxK will be set to the number of columns
     * @return
     */
    public SUDA2Result suda2(int maxK) {
        
        // If maxK <= 0, maxK will be set to the number of columns
        maxK = maxK > 0 ? maxK : columns;
        
        // Execute
        this.calls = 0;
        this.result = new SUDA2Result(this.columns, maxK);
        this.suda2(maxK, this.getItems().getItemList(), data.length);
        
        // Return
        return this.result;
    }

    /**
     * Check argument
     * @param data
     */
    private void check(int[][] data) {
        if (data == null) {
            throw new NullPointerException("Data must not be null");
        }
        if (data.length == 0 || data[0] == null || data[0].length == 0) {
            throw new IllegalArgumentException("Data must not be empty");
        }
    }
        
    /**
     * Returns all items
     * @return
     */
    private SUDA2IndexedItemSet getItems() {

        // Collect all items and their support rows
        SUDA2IndexedItemSet items = new SUDA2IndexedItemSet();
        int index = 0;
        for (int[] row : data) {
            for (int column = 0; column < columns; column++) {
                int value = row[column];
                SUDA2Item item = items.getOrCreateItem(column, value);
                item.addRow(index);
            }
            index++;
        }
        return items;
    }
    
    /**
     * Returns all items for the given reference item from the given list, starting at fromIndex (included)
     * This means that all 1-MSUs can be removed beforehand.
     * @param itemList
     * @param reference
     * @param fromIndex 
     * @return
     */
    private SUDA2IndexedItemSet getItems(SUDA2ItemList itemList, SUDA2Item reference, int fromIndex) {

        // For all items within the given range
        SUDA2IndexedItemSet items = new SUDA2IndexedItemSet(reference);
        List<SUDA2Item> list = itemList.getList();
        for (int index = fromIndex; index < list.size(); index++) {
            
            // Extract item of interest
            SUDA2Item item = list.get(index);
            
            // Smaller set is set 1
            int size1 = item.getRows().size();
            int size2 = reference.getRows().size();
            IntOpenHashSet rows1 = size1 < size2 ? item.getRows() : reference.getRows();
            IntOpenHashSet rows2 = size1 < size2 ? reference.getRows() : item.getRows();
            
            // Intersect support rows with those of the reference item
            IntOpenHashSet rows = new IntOpenHashSet();
            final int [] keys = rows1.keys;
            final boolean [] allocated = rows1.allocated;
            for (int i = 0; i < allocated.length; i++) {
                if (allocated[i]) {
                    int row = keys[i];
                    if (rows2.contains(row)) {
                        rows.add(row);
                    }
                }
            }
            
            // Check whether the set of support rows is not empty, which means that the 
            // item is contained in the sub-table
            if (!rows.isEmpty()) {
                items.addItem(item.getProjection(rows));
            }
        }
        return items;
    }

    /**
     * Clears the list and returns all MSUs
     * @param list
     * @param numRecords
     * @return
     */
    private Pair<Set<SUDA2ItemSet>, SUDA2ItemList> getMSUs(SUDA2ItemList list, int numRecords) {
        
        // Prepare
        Set<SUDA2ItemSet> msus = new HashSet<>();
        
        // Check the items
        List<SUDA2Item> result = new ArrayList<SUDA2Item>();
        for (SUDA2Item item : list.getList()) {

            // All unique items are already MSUs
            if (item.getSupport() == 1) {
                msus.add(new SUDA2ItemSet(item));

            // All items appearing in all rows can be ignored
            } else if (item.getSupport() != numRecords) {
                result.add(item);
            }
        }

        // Return
        return new Pair<Set<SUDA2ItemSet>, SUDA2ItemList>(msus, new SUDA2ItemList(result, list.getReferenceItem()));
    }
//
//    /**
//     * Returns all 1-MSUs for the given item
//     * @param item
//     * @return
//     */
//    private Set<SUDA2ItemSet> getOneMSUs(SUDA2Item item) {
//        return getItems(item).getOneMSUs();
//    }

    /**
     * Returns all 1-MSUS for the given reference item from the given list, starting at fromIndex (included)
     * 
     * @param itemList
     * @param reference
     * @param fromIndex 
     * @return
     */
    private Set<SUDA2ItemSet> getMSUs(SUDA2ItemList itemList, SUDA2Item reference, int fromIndex) {

        // For all items within the given range
        Set<SUDA2ItemSet> result = new HashSet<>();
        List<SUDA2Item> list = itemList.getList();
        outer: for (int index = fromIndex; index < list.size(); index++) {
            
            // Extract item of interest
            SUDA2Item item = list.get(index);
            
            // Smaller set is rows1
            int size1 = item.getRows().size();
            int size2 = reference.getRows().size();
            IntOpenHashSet rows1 = size1 < size2 ? item.getRows() : reference.getRows();
            IntOpenHashSet rows2 = size1 < size2 ? reference.getRows() : item.getRows();
            
            // Check if they intersect with exactly one support row
            boolean supportRowFound = false;
            final int [] keys = rows1.keys;
            final boolean [] allocated = rows1.allocated;
            for (int i = 0; i < allocated.length; i++) {
                if (allocated[i]) {
                    int row = keys[i];
                    if (rows2.contains(row)) {
                        
                        // More than one support row
                        if (supportRowFound) {
                            continue outer;
                        } else {
                            supportRowFound = true;
                        }
                    }
                }
            }
            
            // Check whether the item is a 1-MSU
            if (supportRowFound) {
                result.add(new SUDA2ItemSet(item)); // Rows will never be used, so we can re-use the old item
            }
        }
        return result;
    }

    /**
     * Implements both checks for MSUs described in the paper
     * @param currentList
     * @param currentRanks 
     * @param candidate
     * @param referenceItem
     * @param referenceRank
     * @return
     */

    private boolean isMSU(SUDA2ItemList currentList,
                          SUDA2ItemRanks currentRanks, 
                          SUDA2ItemSet candidate,
                          SUDA2Item referenceItem,
                          int referenceRank) {

        // All of the k-1 items in the candidate set must have rank > reference rank
        for (SUDA2Item candidateItem : candidate.getItems()) {
            if (currentRanks.getRank(candidateItem.getId()) <= referenceRank) {
                return false;
            }
        }
        
        // Search for the special row
        if (candidate.getItems().size() <= 1) {
            return true;
        } else {
            return isSpecialRowAvailable(currentList, referenceItem, candidate);
        }
    }

    /**
     * Searches for the special row
     * @param rows
     * @param referenceItem
     * @param candidate
     * @return
     */
    private boolean isSpecialRowAvailable(final SUDA2ItemList currentList, SUDA2Item referenceItem, SUDA2ItemSet candidate) {
        
        // This is one of the hottest functions in SUDA2
        // (1) It seems to be more likely that the special row is found than that
        //     the special row is not found -> We optimize for this path
        // (2) It is more likely that not all of the candidate items are contained
        //     than that the reference item is contained
        
        // Find item with smallest support 
        IntOpenHashSet rows = null;
        SUDA2Item pivot = null;
        for (SUDA2Item item : candidate.getItems()) {
            IntOpenHashSet _rows = currentList.getItem(item.getId()).getRows();
            if (rows == null || _rows.size() < rows.size()) {
                rows = _rows;
                pivot = item;
            }
        }
        
        // Prepare list of items to check
        SUDA2Item[] items = new SUDA2Item[candidate.size()-1];
        int index = 0;
        for(SUDA2Item item : candidate.getItems()) {
            if (item != pivot) {
                items[index++] = item;
            }
        }
        Arrays.sort(items, new Comparator<SUDA2Item>() {
            @Override
            public int compare(SUDA2Item o1, SUDA2Item o2) {
                int support1 = currentList.getItem(o1.getId()).getSupport();
                int support2 = currentList.getItem(o1.getId()).getSupport();
                return support1 < support2 ? -1 :
                       support1 > support2 ? +1 : 0;
            }
        });
        
        // And search for the special row
        final int [] keys = rows.keys;
        final boolean [] allocated = rows.allocated;
        outer: for (int i = 0; i < allocated.length; i++) {
            if (allocated[i]) {
                int[] row = data[keys[i]];
                for (SUDA2Item item : items) {
                    if (!item.isContained(row)) {
                        continue outer;
                    }
                }
                if (referenceItem.isContained(row)) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    /**
     * SUDA2
     * @param maxK
     * @param currentList
     * @param numRecords
     * @return
     */
    private Set<SUDA2ItemSet> suda2(int maxK,
                                    SUDA2ItemList currentList,
                                    int numRecords) {

        this.calls++;

        // Find MSUs and clear list
        Pair<Set<SUDA2ItemSet>, SUDA2ItemList> msusAndList = getMSUs(currentList, numRecords);
        Set<SUDA2ItemSet> msus = msusAndList.first;
        currentList = msusAndList.second;
        
        // When processing the original table
        if (numRecords == data.length) {
            
            // Register 1-MSUs for the original table
            for (SUDA2ItemSet msu : msus) {
                result.registerMSU(msu);
            }
        }
//
//        // Find perfectly correlating MSUs
//        for (int i = 0; i < currentList.size(); i++) {
//            SUDA2Item item1 = currentList.getList().get(i);
//            for (int j = i+1; j < currentList.size(); j++) {
//                SUDA2Item item2 = currentList.getList().get(j);
//                if (item1.getRows().equals(item2.getRows())) {
//                    System.out.println("Perfect correlation between " + item1.getSupport() + "/" + item2.getSupport());
//                }
//            }
//        }

        // Check for maxK
        if (maxK <= 1) {
            return msus;
        }

        // For each item i
        int index = 0;
        SUDA2ItemRanks currentRanks = currentList.getRanks();
        for (SUDA2Item referenceItem : currentList.getList()) {
            
            // Track
            index++;
            
            // Progress information
            if (numRecords == data.length) {
                System.out.println(index + "/" + currentList.size() + " -> " + calls);
//                if (index == 50) {
//                    return null;
//                }
            }

            // Obtain rank
            int referenceRank = currentRanks.getRank(referenceItem.getId());

            // Recursive call
            int upperLimit = maxK - 1; // Pruning strategy 3
            upperLimit = Math.min(upperLimit, currentList.size() - index); // Pruning strategy 2 // TODO: (+1)?
            upperLimit = Math.min(upperLimit, referenceItem.getSupport() - 1); // Pruning strategy 1 // TODO: No effect.
            
            // We only perform recursion for maxK > 1
            Set<SUDA2ItemSet> msus_i;
            if (upperLimit > 1) {
                msus_i = suda2(upperLimit,
                               getItems(currentList, referenceItem, index).getItemList(),
                               referenceItem.getRows().size());
            } else {
                msus_i = getMSUs(currentList, referenceItem, index);
            }

            // For each candidate
            outer: for (SUDA2ItemSet candidate : msus_i) {
                
                // Check if candidate is an MSU
                if (!isMSU(currentList, currentRanks, candidate, referenceItem, referenceRank)) {
                    continue outer;
                }

                // Add MSU
                if (numRecords == data.length) {
                    result.registerMSU(referenceItem, candidate);
                } else {
                    msus.add(new SUDA2ItemSet(referenceItem, candidate));
                }
                
                // TODO: Just a sanity check
//                if (msus.contains(merged)) {
//                    throw new IllegalStateException("Duplicate result");
//                }
                
//                if (maxK == 1) {
//                    for (SUDA2ItemSet existing : msus) {
//                        if (existing.intersectsWith(merged)) {
//                            throw new IllegalStateException("Non-minimal result");
//                        }
//                    }
//                    Set<Integer> rows = new HashSet<Integer>();
//                    Iterator<SUDA2Item> iter = merged.getItems().iterator();
//                    rows.addAll(this.set.getItem(iter.next().getId()).getRows());
//                    while (iter.hasNext()) {
//                        rows.retainAll(this.set.getItem(iter.next().getId()).getRows());
//                    }
//                    if (rows.size() != 1) {
//                        throw new IllegalStateException("Non-unique result");
//                    }
//                }

            }
        }
        
        // Return
        return msus;
    }
}
