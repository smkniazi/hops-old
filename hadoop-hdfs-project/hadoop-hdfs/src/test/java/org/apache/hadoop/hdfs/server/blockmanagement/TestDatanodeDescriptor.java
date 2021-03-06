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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import org.apache.hadoop.hdfs.DFSTestUtil;
import org.apache.hadoop.hdfs.HdfsConfiguration;
import org.apache.hadoop.hdfs.protocol.Block;
import org.apache.hadoop.hdfs.server.common.GenerationStamp;
import org.apache.hadoop.hdfs.server.namenode.INode;
import se.sics.hop.metadata.INodeIdentifier;
import se.sics.hop.exception.StorageException;
import se.sics.hop.transaction.handler.HDFSOperationType;
import se.sics.hop.metadata.StorageFactory;
import org.junit.Test;
import se.sics.hop.transaction.handler.HopsTransactionalRequestHandler;
import se.sics.hop.transaction.lock.HopsLockFactory;
import static se.sics.hop.transaction.lock.HopsLockFactory.BLK;
import se.sics.hop.common.INodeUtil;
import se.sics.hop.transaction.lock.TransactionLocks;

/**
 * This class tests that methods in DatanodeDescriptor
 */
public class TestDatanodeDescriptor {
  /**
   * Test that getInvalidateBlocks observes the maxlimit.
   */
  @Test
  public void testGetInvalidateBlocks() throws Exception {
    final int MAX_BLOCKS = 10;
    final int REMAINING_BLOCKS = 2;
    final int MAX_LIMIT = MAX_BLOCKS - REMAINING_BLOCKS;
    
    DatanodeDescriptor dd = DFSTestUtil.getLocalDatanodeDescriptor();
    ArrayList<Block> blockList = new ArrayList<Block>(MAX_BLOCKS);
    for (int i=0; i<MAX_BLOCKS; i++) {
      blockList.add(new Block(i, 0, GenerationStamp.FIRST_VALID_STAMP));
    }
    dd.addBlocksToBeInvalidated(blockList);
    Block[] bc = dd.getInvalidateBlocks(MAX_LIMIT);
    assertEquals(bc.length, MAX_LIMIT);
    bc = dd.getInvalidateBlocks(MAX_LIMIT);
    assertEquals(bc.length, REMAINING_BLOCKS);
  }
  
  @Test
  public void testBlocksCounter() throws Exception {
    StorageFactory.setConfiguration(new HdfsConfiguration());
    StorageFactory.formatStorage();
    
    DatanodeDescriptor dd = DFSTestUtil.getLocalDatanodeDescriptor();
    assertEquals(0, dd.numBlocks());
    BlockInfo blk = new BlockInfo(new Block(1L), INode.NON_EXISTING_ID);
    BlockInfo blk1 = new BlockInfo(new Block(2L), INode.NON_EXISTING_ID);
    // add first block
    assertTrue(addBlock(dd, blk));
    assertEquals(1, dd.numBlocks());
    // remove a non-existent block
    assertFalse(removeBlock(dd, blk1));
    assertEquals(1, dd.numBlocks());
    // add an existent block
    assertFalse(addBlock(dd, blk));
    System.out.println("number of blks are " + dd.numBlocks());
    assertEquals(1, dd.numBlocks());
    // add second block
    assertTrue(addBlock(dd, blk1));
    assertEquals(2, dd.numBlocks());
    // remove first block
    assertTrue(removeBlock(dd, blk));
    assertEquals(1, dd.numBlocks());
    // remove second block
    assertTrue(removeBlock(dd, blk1));
    assertEquals(0, dd.numBlocks());    
  }
  
    private boolean addBlock(final DatanodeDescriptor dn, final BlockInfo blk) throws IOException{
     return (Boolean) new HopsTransactionalRequestHandler(HDFSOperationType.TEST) {
       INodeIdentifier inodeIdentifier;

       @Override
       public void setUp() throws StorageException, IOException {
         inodeIdentifier = INodeUtil.resolveINodeFromBlock(blk);
       }

       @Override
       public void acquireLock(TransactionLocks locks) throws IOException {
         HopsLockFactory lf = HopsLockFactory.getInstance();
         locks.add(lf.getIndividualBlockLock(blk.getBlockId(), inodeIdentifier))
                 .add(lf.getBlockRelated(BLK.RE));
       }

       @Override
       public Object performTask() throws StorageException, IOException {
         return dn.addBlock(blk);
       }

    }.handle();
  }
    
    private boolean removeBlock(final DatanodeDescriptor dn, final BlockInfo blk) throws IOException{
           return (Boolean) new HopsTransactionalRequestHandler(HDFSOperationType.TEST) {
       INodeIdentifier inodeIdentifier;

       @Override
       public void setUp() throws StorageException, IOException {
         inodeIdentifier = INodeUtil.resolveINodeFromBlock(blk);
       }

       @Override
       public void acquireLock(TransactionLocks locks) throws IOException {
         HopsLockFactory lf = HopsLockFactory.getInstance();
         locks.add(lf.getIndividualBlockLock(blk.getBlockId(), inodeIdentifier))
                 .add(lf.getBlockRelated(BLK.RE));
       }

       @Override
       public Object performTask() throws StorageException, IOException {
         return dn.removeBlock(blk);
       }

    }.handle();
  }
}
