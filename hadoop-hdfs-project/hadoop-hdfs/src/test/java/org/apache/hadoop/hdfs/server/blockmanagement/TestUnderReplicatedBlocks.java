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

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FsShell;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.DFSTestUtil;
import org.apache.hadoop.hdfs.HdfsConfiguration;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.hdfs.protocol.ExtendedBlock;
import org.apache.hadoop.hdfs.server.namenode.lock.INodeUtil;
import org.apache.hadoop.hdfs.server.namenode.lock.TransactionLockManager;
import org.apache.hadoop.hdfs.server.namenode.lock.TransactionLockTypes;
import org.apache.hadoop.hdfs.server.namenode.lock.TransactionLocks;
import org.apache.hadoop.hdfs.server.namenode.persistance.PersistanceException;
import org.apache.hadoop.hdfs.server.namenode.persistance.RequestHandler.OperationType;
import org.apache.hadoop.hdfs.server.namenode.persistance.TransactionalRequestHandler;
import org.apache.hadoop.hdfs.server.namenode.persistance.storage.StorageException;
import org.junit.Test;

public class TestUnderReplicatedBlocks {
  @Test(timeout=300000) // 5 min timeout
  public void testSetrepIncWithUnderReplicatedBlocks() throws Exception {
    Configuration conf = new HdfsConfiguration();
    final short REPLICATION_FACTOR = 2;
    final String FILE_NAME = "/testFile";
    final Path FILE_PATH = new Path(FILE_NAME);
    MiniDFSCluster cluster = new MiniDFSCluster.Builder(conf).numDataNodes(REPLICATION_FACTOR + 1).build();
    try {
      // create a file with one block with a replication factor of 2
      final FileSystem fs = cluster.getFileSystem();
      DFSTestUtil.createFile(fs, FILE_PATH, 1L, REPLICATION_FACTOR, 1L);
      DFSTestUtil.waitReplication(fs, FILE_PATH, REPLICATION_FACTOR);
      
      // remove one replica from the blocksMap so block becomes under-replicated
      // but the block does not get put into the under-replicated blocks queue
      final BlockManager bm = cluster.getNamesystem().getBlockManager();
      final ExtendedBlock b = DFSTestUtil.getFirstBlock(fs, FILE_PATH);
      
      new TransactionalRequestHandler(OperationType.SET_REPLICA_INCREAMENT) {
        long inodeId;

        @Override
        public void setUp() throws StorageException {
          inodeId = INodeUtil.findINodeIdByBlock(b.getBlockId());
        }

        @Override
        public TransactionLocks acquireLock() throws PersistanceException, IOException {
          TransactionLocks lks = new TransactionLocks();
          lks.addINode(TransactionLockTypes.INodeLockType.WRITE).
                  addBlock(TransactionLockTypes.LockType.WRITE, b.getBlockId()).
                  addReplica(TransactionLockTypes.LockType.WRITE);
          TransactionLockManager tlm = new TransactionLockManager(lks);
          tlm.acquireByBlock(inodeId);
          return lks;
        }

        @Override
        public Object performTask() throws PersistanceException, IOException {
          DatanodeDescriptor dn = bm.blocksMap.nodeIterator(b.getLocalBlock()).next();
          bm.addToInvalidates(b.getLocalBlock(), dn);
          bm.blocksMap.removeNode(b.getLocalBlock(), dn);
          return null;
        }
      }.handle(null);
      
      // increment this file's replication factor
      FsShell shell = new FsShell(conf);
      assertEquals(0, shell.run(new String[]{
          "-setrep", "-w", Integer.toString(1+REPLICATION_FACTOR), FILE_NAME}));
    } finally {
      cluster.shutdown();
    }
    
  }

}
