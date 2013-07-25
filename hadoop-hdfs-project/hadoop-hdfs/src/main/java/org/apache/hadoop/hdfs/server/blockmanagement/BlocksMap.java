/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hdfs.server.blockmanagement;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.hadoop.hdfs.protocol.Block;
import org.apache.hadoop.hdfs.server.namenode.persistance.EntityManager;
import org.apache.hadoop.hdfs.server.namenode.persistance.PersistanceException;
import org.apache.hadoop.hdfs.server.namenode.persistance.RequestHandler.OperationType;
import org.apache.hadoop.hdfs.server.namenode.persistance.TransactionalRequestHandler;
import org.apache.hadoop.hdfs.server.namenode.persistance.context.entity.EntityContext;
import org.apache.hadoop.hdfs.server.namenode.persistance.data_access.entity.BlockInfoDataAccess;
import org.apache.hadoop.hdfs.server.namenode.persistance.storage.StorageFactory;

/**
 * This class maintains the map from a block to its metadata.
 * block's metadata currently includes blockCollection it belongs to and
 * the datanodes that store the block.
 */
class BlocksMap {

  private final DatanodeManager datanodeManager;
  BlocksMap(DatanodeManager datanodeManager) {
    this.datanodeManager = datanodeManager;
  }

  void close() {
    // Empty blocks once GSet#clear is implemented (HDFS-3940)
  }

  BlockCollection getBlockCollection(Block b) throws PersistanceException {
    BlockInfo info = getStoredBlock(b);
    return (info != null) ? info.getBlockCollection() : null;
  }

  /**
   * Add block b belonging to the specified block collection to the map.
   */
  BlockInfo addBlockCollection(BlockInfo b, BlockCollection bc) throws PersistanceException {
    b.setBlockCollection(bc);
    return b;
  }

  /**
   * Remove the block from the block map;
   * remove it from all data-node lists it belongs to;
   * and remove all data-node locations associated with the block.
   */
  void removeBlock(Block block) throws PersistanceException {
    BlockInfo blockInfo = getStoredBlock(block);
    if (blockInfo == null)
      return;    
    blockInfo.setBlockCollection(null);
    blockInfo.removeAllReplicas();
  }
  
  /** Returns the block object it it exists in the map. */
  BlockInfo getStoredBlock(Block b) throws PersistanceException {
    if (!(b instanceof BlockInfo)) {
      return EntityManager.find(BlockInfo.Finder.ById, b.getBlockId());
    }
    return (BlockInfo)b;
  }

  /**
   * Searches for the block in the BlocksMap and 
   * returns Iterator that iterates through the nodes the block belongs to.
   */
  Iterator<DatanodeDescriptor> nodeIterator(Block b) throws PersistanceException {
    BlockInfo blockInfo = getStoredBlock(b);
    return nodeIterator(blockInfo);
  }

  /**
   * For a block that has already been retrieved from the BlocksMap
   * returns Iterator that iterates through the nodes the block belongs to.
   */
  Iterator<DatanodeDescriptor> nodeIterator(BlockInfo storedBlock) throws PersistanceException {
    if(storedBlock == null)
      return null;
   return Arrays.asList(storedBlock.getDatanodes(datanodeManager)).iterator();
  }

  /** counts number of containing nodes. Better than using iterator. */
  int numNodes(Block b) throws PersistanceException {
    BlockInfo info = getStoredBlock(b);
    return info == null ? 0 : info.numNodes();
  }

  /**
   * Remove data-node reference from the block.
   * Remove the block from the block map
   * only if it does not belong to any file and data-nodes.
   */
  boolean removeNode(Block b, DatanodeDescriptor node) throws PersistanceException {
    BlockInfo info = getStoredBlock(b);
    if (info == null)
      return false;

    // remove block from the data-node list and the node from the block info
    boolean removed = node.removeBlock(info);
    return removed;
  }

  int size() throws IOException {
    TransactionalRequestHandler getAllBlocksSizeHander = new TransactionalRequestHandler(OperationType.GET_ALL_BLOCKS_SIZE) {
      @Override
      public void acquireLock() throws PersistanceException, IOException {
      }

      @Override
      public Object performTask() throws PersistanceException, IOException {
        BlockInfoDataAccess bida = (BlockInfoDataAccess) StorageFactory.getDataAccess(BlockInfo.class);
        return bida.countAll();
      }
    };
    return (Integer) getAllBlocksSizeHander.handle();
  }

  Iterable<BlockInfo> getBlocks() throws IOException {
    TransactionalRequestHandler getAllBlocksHander = new TransactionalRequestHandler(OperationType.GET_ALL_BLOCKS) {
      @Override
      public void acquireLock() throws PersistanceException, IOException {
      }

      @Override
      public Object performTask() throws PersistanceException, IOException {
        //FIXME. Very inefficient way of block processing
        EntityContext.log(OperationType.GET_ALL_BLOCKS.toString(), EntityContext.CacheHitState.LOSS, "FIXME. Very inefficient way of block processing");
        BlockInfoDataAccess bida = (BlockInfoDataAccess) StorageFactory.getDataAccess(BlockInfo.class);
        return bida.findAllBlocks();
      }
    };
    return (List<BlockInfo>) getAllBlocksHander.handle();
  }
  
  /** Get the capacity of the HashMap that stores blocks */
  int getCapacity() throws PersistanceException {
    throw new UnsupportedOperationException("return capacity");
  }

  /**
   * Replace a block in the block map by a new block.
   * The new block and the old one have the same key.
   * @param newBlock - block for replacement
   * @return new block
   */
  BlockInfo replaceBlock(BlockInfo newBlock) {
    //HOP: [M] doesn't make sense in our case, beacause the new block will have the same id as the old one
    return newBlock;
  }
}
