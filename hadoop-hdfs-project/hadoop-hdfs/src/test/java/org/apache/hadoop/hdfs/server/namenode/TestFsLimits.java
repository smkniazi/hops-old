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

import static org.apache.hadoop.hdfs.server.common.Util.fileAsURI;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.fs.permission.PermissionStatus;
import org.apache.hadoop.hdfs.DFSConfigKeys;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.hdfs.protocol.FSLimitException;
import org.apache.hadoop.hdfs.protocol.FSLimitException.MaxDirectoryItemsExceededException;
import org.apache.hadoop.hdfs.protocol.FSLimitException.PathComponentTooLongException;
import org.apache.hadoop.hdfs.protocol.QuotaExceededException;
import se.sics.hop.metadata.lock.TransactionLockAcquirer;
import se.sics.hop.metadata.lock.TransactionLockTypes;
import se.sics.hop.metadata.lock.HDFSTransactionLocks;
import se.sics.hop.exception.PersistanceException;
import se.sics.hop.transaction.handler.HDFSTransactionalRequestHandler;
import se.sics.hop.exception.StorageException;
import se.sics.hop.metadata.StorageFactory;
import org.junit.Before;
import org.junit.Test;
import se.sics.hop.transaction.handler.HDFSOperationType;

public class TestFsLimits {
  static Configuration conf;
  static INode[] inodes;
  static FSDirectory fs;
  static boolean fsIsReady;
  
  static PermissionStatus perms
    = new PermissionStatus("admin", "admin", FsPermission.getDefault());

  static INodeDirectoryWithQuota rootInode;
  
  static private FSNamesystem getMockNamesystem() {
    FSNamesystem fsn = mock(FSNamesystem.class);
    when(
        fsn.createFsOwnerPermissions((FsPermission)anyObject())
    ).thenReturn(
         new PermissionStatus("root", "wheel", FsPermission.getDefault())
    );
    return fsn;
  }
  
  private void initFS() throws StorageException, IOException{
        StorageFactory.setConfiguration(conf);
    assert (StorageFactory.getConnector().formatStorage());
    rootInode = FSDirectory.createRootInode(perms,true);
    inodes = new INode[]{ rootInode, null };
    fs = null;
    fsIsReady = true;
  }
  private static class TestFSDirectory extends FSDirectory {
    public TestFSDirectory() throws IOException {
      super(getMockNamesystem(), conf);
      setReady(fsIsReady);
    }
    
    @Override
    public <T extends INode> void verifyFsLimits(INode[] pathComponents,
        int pos, T child) throws FSLimitException, PersistanceException {
      super.verifyFsLimits(pathComponents, pos, child);
    }
  }

  @Before
  public void setUp() throws IOException {
    conf = new Configuration();

    conf.set(DFSConfigKeys.DFS_NAMENODE_NAME_DIR_KEY,
             fileAsURI(new File(MiniDFSCluster.getBaseDirectory(),
                                "namenode")).toString());
   initFS();
  }

  @Test
  public void testDefaultMaxComponentLength() {
    int maxComponentLength = conf.getInt(
        DFSConfigKeys.DFS_NAMENODE_MAX_COMPONENT_LENGTH_KEY,
        DFSConfigKeys.DFS_NAMENODE_MAX_COMPONENT_LENGTH_DEFAULT);
    assertEquals(0, maxComponentLength);
  }
  
  @Test
  public void testDefaultMaxDirItems() {
    int maxDirItems = conf.getInt(
        DFSConfigKeys.DFS_NAMENODE_MAX_DIRECTORY_ITEMS_KEY,
        DFSConfigKeys.DFS_NAMENODE_MAX_DIRECTORY_ITEMS_DEFAULT);
    assertEquals(0, maxDirItems);
  }

  @Test
  public void testNoLimits() throws Exception {
    addChildWithName("1", null);
    addChildWithName("22", null);
    addChildWithName("333", null);
    addChildWithName("4444", null);
    addChildWithName("55555", null);
  }

  @Test
  public void testMaxComponentLength() throws Exception {
    conf.setInt(DFSConfigKeys.DFS_NAMENODE_MAX_COMPONENT_LENGTH_KEY, 2);
    
    addChildWithName("1", null);
    addChildWithName("22", null);
    addChildWithName("333", PathComponentTooLongException.class);
    addChildWithName("4444", PathComponentTooLongException.class);
  }

  @Test
  public void testMaxDirItems() throws Exception {
    conf.setInt(DFSConfigKeys.DFS_NAMENODE_MAX_DIRECTORY_ITEMS_KEY, 2);
    
    addChildWithName("1", null);
    addChildWithName("22", null);
    addChildWithName("333", MaxDirectoryItemsExceededException.class);
    addChildWithName("4444", MaxDirectoryItemsExceededException.class);
  }

  @Test
  public void testMaxComponentsAndMaxDirItems() throws Exception {
    conf.setInt(DFSConfigKeys.DFS_NAMENODE_MAX_COMPONENT_LENGTH_KEY, 3);
    conf.setInt(DFSConfigKeys.DFS_NAMENODE_MAX_DIRECTORY_ITEMS_KEY, 2);
    
    addChildWithName("1", null);
    addChildWithName("22", null);
    addChildWithName("333", MaxDirectoryItemsExceededException.class);
    addChildWithName("4444", PathComponentTooLongException.class);
  }

  @Test
  public void testDuringEditLogs() throws Exception {
    conf.setInt(DFSConfigKeys.DFS_NAMENODE_MAX_COMPONENT_LENGTH_KEY, 3);
    conf.setInt(DFSConfigKeys.DFS_NAMENODE_MAX_DIRECTORY_ITEMS_KEY, 2);
    fsIsReady = false;
    
    addChildWithName("1", null);
    addChildWithName("22", null);
    addChildWithName("333", null);
    addChildWithName("4444", null);
  }

  private static long id  = 1 ;
  private void addChildWithName(final String name, final Class<?> expected)
  throws Exception {
  HDFSTransactionalRequestHandler handler = new HDFSTransactionalRequestHandler(HDFSOperationType.TEST) {

    @Override
    public HDFSTransactionLocks acquireLock() throws PersistanceException, IOException {
      TransactionLockAcquirer tla = new TransactionLockAcquirer();
      tla.getLocks().addINode(TransactionLockTypes.INodeResolveType.PATH_AND_ALL_CHILDREN_RECURESIVELY, TransactionLockTypes.INodeLockType.WRITE_ON_PARENT, new String[]{"/", "/"+name});
      return tla.acquire();
    }

    @Override
    public Object performTask() throws PersistanceException, IOException {
      // have to create after the caller has had a chance to set conf values
    if (fs == null) fs = new TestFSDirectory();

    INode child = new INodeDirectory(name, perms);
    child.setIdNoPersistance(id++);
    child.setLocalName(name);
    
    Class<?> generated = null;
    try {
      fs.verifyFsLimits(inodes, 1, child);
      INodeDirectoryWithQuota.getRootDir().addChild(child, false);
    } catch (QuotaExceededException e) {
      generated = e.getClass();
    }
    assertEquals(expected, generated);
    return null;
    }
  };
  
  handler.handle();
    
  }
}
