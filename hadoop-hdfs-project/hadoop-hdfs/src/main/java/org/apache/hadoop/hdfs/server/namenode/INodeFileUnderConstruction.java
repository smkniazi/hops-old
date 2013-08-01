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
package org.apache.hadoop.hdfs.server.namenode;

import java.io.IOException;

import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.fs.permission.PermissionStatus;
import org.apache.hadoop.hdfs.protocol.Block;
import org.apache.hadoop.hdfs.protocol.DatanodeID;
import org.apache.hadoop.hdfs.server.blockmanagement.BlockInfo;
import org.apache.hadoop.hdfs.server.blockmanagement.BlockInfoUnderConstruction;
import org.apache.hadoop.hdfs.server.blockmanagement.DatanodeDescriptor;
import org.apache.hadoop.hdfs.server.blockmanagement.MutableBlockCollection;
import org.apache.hadoop.hdfs.server.common.HdfsServerConstants.BlockUCState;
import org.apache.hadoop.hdfs.server.namenode.persistance.PersistanceException;
import org.apache.hadoop.hdfs.server.namenode.persistance.context.entity.EntityContext;
import org.apache.hadoop.hdfs.server.namenode.persistance.context.entity.EntityContext;
import org.apache.hadoop.hdfs.server.namenode.persistance.storage.StorageException;

/**
 * I-node for file being written.
 */
@InterfaceAudience.Private
public class INodeFileUnderConstruction extends INodeFile implements MutableBlockCollection {
  /** Cast INode to INodeFileUnderConstruction. */
  public static INodeFileUnderConstruction valueOf(INode inode, String path
      ) throws IOException {
    final INodeFile file = INodeFile.valueOf(inode, path);
    if (!file.isUnderConstruction()) {
      throw new IOException("File is not under construction: " + path);
    }
    return (INodeFileUnderConstruction)file;
  }

  private  String clientName;         // lease holder
  private final String clientMachine;
  private final DatanodeID clientNode; // if client is a cluster node too.
  
  public INodeFileUnderConstruction(PermissionStatus permissions,
                             short replication,
                             long preferredBlockSize,
                             long modTime,
                             String clientName,
                             String clientMachine,
                             DatanodeID clientNode) {
    super(permissions.applyUMask(UMASK), null, replication,
        modTime, modTime, preferredBlockSize);
    this.clientName = clientName;
    this.clientMachine = clientMachine;
    this.clientNode = clientNode;
  }

  public INodeFileUnderConstruction(byte[] name,
                             short blockReplication,
                             long modificationTime,
                             long preferredBlockSize,
                             BlockInfo[] blocks,
                             PermissionStatus perm,
                             String clientName,
                             String clientMachine,
                             DatanodeID clientNode) {
    super(perm, blocks, blockReplication, modificationTime, modificationTime,
          preferredBlockSize);
    setLocalNameNoPersistance(name);
    this.clientName = clientName;
    this.clientMachine = clientMachine;
    this.clientNode = clientNode;
    throw new UnsupportedOperationException("HOP: This constructor should not be used"); // The only reason it is here that it is called in some FSImage Classes that are not deleted. 
  }

   //HOP: used instead of INodeFile.convertToUnderConstruction
  protected INodeFileUnderConstruction(INodeFile file,
                             String clientName,
                             String clientMachine,
                             DatanodeID clientNode) throws PersistanceException {
    super(file);
    this.clientName = clientName;
    this.clientMachine = clientMachine;
    this.clientNode = clientNode;
  }
  
  public String getClientName() {
    return clientName;
  }

  void setClientName(String clientName) throws PersistanceException {
    this.clientName = clientName;
    save();
  }

  public String getClientMachine() {
    return clientMachine;
  }

  public DatanodeID getClientNode() {
    return clientNode;
  }

  /**
   * Is this inode being constructed?
   */
  @Override
  public boolean isUnderConstruction() {
    return true;
  }

  //
  // converts a INodeFileUnderConstruction into a INodeFile
  // use the modification time as the access time
  //
  INodeFile convertToInodeFile() throws PersistanceException {
    assert allBlocksComplete() : "Can't finalize inode " + this
      + " since it contains non-complete blocks! Blocks are "
      + getBlocks();
    INodeFile obj = new INodeFile(this);
    obj.setAccessTime(getModificationTime());
    return obj;
    
  }
  
  /**
   * @return true if all of the blocks in this file are marked as completed.
   */
  private boolean allBlocksComplete() throws PersistanceException {
    for (BlockInfo b : getBlocks()) {
      if (!b.isComplete()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Remove a block from the block list. This block should be
   * the last one on the list.
   */
  void removeLastBlock(Block oldblock) throws IOException, PersistanceException {
    final BlockInfo[] blocks = getBlocks();
    if (blocks == null) {
      throw new IOException("Trying to delete non-existant block " + oldblock);
    }
    int size_1 = blocks.length - 1;
    if (!blocks[size_1].equals(oldblock)) {
      throw new IOException("Trying to delete non-last block " + oldblock);
    }

    removeBlock((BlockInfo) oldblock);  
  }

  /**
   * Convert the last block of the file to an under-construction block.
   * Set its locations.
   */
  @Override
  public BlockInfoUnderConstruction setLastBlock(BlockInfo lastBlock,
      DatanodeDescriptor[] targets) throws IOException, PersistanceException {
    if (numBlocks() == 0) {
      throw new IOException("Failed to set last block: File is empty.");
    }
    BlockInfoUnderConstruction ucBlock =
      lastBlock.convertToBlockUnderConstruction(
          BlockUCState.UNDER_CONSTRUCTION, targets);
    ucBlock.setBlockCollection(this);
    setBlock(numBlocks()-1, ucBlock);
    return ucBlock;
  }

  /**
   * Update the length for the last block
   * 
   * @param lastBlockLength
   *          The length of the last block reported from client
   * @throws IOException
   */
  void updateLengthOfLastBlock(long lastBlockLength) throws IOException, PersistanceException {
    BlockInfo lastBlock = this.getLastBlock();
    assert (lastBlock != null) : "The last block for path "
        + this.getFullPathName() + " is null when updating its length";
    assert (lastBlock instanceof BlockInfoUnderConstruction) : "The last block for path "
        + this.getFullPathName()
        + " is not a BlockInfoUnderConstruction when updating its length";
    lastBlock.setNumBytes(lastBlockLength);
  }
 
  //START_HOP_CODE
    public void removeBlock(BlockInfo block) throws PersistanceException {
    BlockInfo[] blks = getBlocks();
    int index = block.getBlockIndex();
    
    block.setBlockCollection(null);
    
    if (index != blks.length) {
      for (int i = index+1; i < blks.length; i++) {
        blks[i].setBlockIndex(i-1);
      }
    }
  }
  //END_HOP_CODE
}
