/*
 * Copyright 2014 Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package se.sics.hop.transaction.lock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Iterables;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hdfs.server.namenode.INode;
import org.apache.hadoop.hdfs.server.namenode.INodeAttributes;
import org.apache.hadoop.hdfs.server.namenode.INodeDirectoryWithQuota;
import se.sics.hop.exception.PersistanceException;
import se.sics.hop.exception.StorageException;
import se.sics.hop.metadata.hdfs.dal.BlockInfoDataAccess;
import se.sics.hop.metadata.hdfs.entity.hdfs.HopINodeCandidatePK;
import se.sics.hop.transaction.EntityManager;
import static se.sics.hop.transaction.lock.HopsLock.DEFAULT_LOCK_TYPE;

/**
 *
 * @author Mahmoud Ismail <maism@sics.se>
 */
public abstract class HopsBaseINodeLock extends HopsLock {
  protected final static Log LOG = LogFactory.getLog(HopsBaseINodeLock.class);

  private final Map<INode, TransactionLockTypes.INodeLockType> allLockedInodesInTx;
  private final ResolvedINodesMap resolvedINodesMap;

  protected static TransactionLockTypes.INodeLockType DEFAULT_INODE_LOCK_TYPE = TransactionLockTypes.INodeLockType.READ_COMMITTED;

  public static void setDefaultLockType(TransactionLockTypes.INodeLockType defaultLockType) {
    DEFAULT_INODE_LOCK_TYPE = defaultLockType;
  }
    
  protected HopsBaseINodeLock() {
    this.allLockedInodesInTx = new HashMap<INode, TransactionLockTypes.INodeLockType>();
    this.resolvedINodesMap = new ResolvedINodesMap();
  }

  private class ResolvedINodesMap {
    private final Map<String, PathRelatedINodes> pathToPathINodes =
        new HashMap<String, PathRelatedINodes>();
    private final Collection<INode> individualInodes = new ArrayList<INode>();

    private class PathRelatedINodes {
      private List<INode> pathINodes;
      private List<INode> childINodes;
    }

    private PathRelatedINodes getWithLazyInit(String path) {
      if (!pathToPathINodes.containsKey(path)) {
        PathRelatedINodes pathRelatedINodes = new PathRelatedINodes();
        pathToPathINodes.put(path, pathRelatedINodes);
        return pathRelatedINodes;
      }
      return pathToPathINodes.get(path);
    }

    private void putPathINodes(String path, List<INode> iNodes) {
      PathRelatedINodes pathRelatedINodes = getWithLazyInit(path);
      pathRelatedINodes.pathINodes = iNodes;
    }

    private void putChildINodes(String path, List<INode> iNodes) {
      PathRelatedINodes pathRelatedINodes = getWithLazyInit(path);
      pathRelatedINodes.childINodes = iNodes;
    }

    private void putIndividualINode(INode iNode) {
      individualInodes.add(iNode);
    }

    private List<INode> getPathINodes(String path) {
      return pathToPathINodes.get(path).pathINodes;
    }

    private List<INode> getChildINodes(String path) {
      return pathToPathINodes.get(path).childINodes;
    }

    public Iterable<INode> getAll() {
      Iterable iterable = null;
      for (PathRelatedINodes pathRelatedINodes : pathToPathINodes.values()) {
        List<INode> pathINodes = pathRelatedINodes.pathINodes == null ? 
                Collections.EMPTY_LIST : pathRelatedINodes.pathINodes;
        List<INode> childINodes = pathRelatedINodes.childINodes == null ?
            Collections.EMPTY_LIST : pathRelatedINodes.childINodes;
        if (iterable == null) {
          iterable = Iterables.concat(pathINodes, childINodes);
        } else {
          iterable = Iterables.concat(iterable, pathINodes, childINodes);
        }
      }
      if(iterable == null)
        iterable = Collections.EMPTY_LIST;
      return Iterables.concat(iterable, individualInodes);
    }
  }

  Iterable<INode> getAllResolvedINodes() {
    return resolvedINodesMap.getAll();
  }

  void addPathINodes(String path, List<INode> iNodes) {
    resolvedINodesMap.putPathINodes(path, iNodes);
  }

  void addChildINodes(String path, List<INode> iNodes) {
    resolvedINodesMap.putChildINodes(path, iNodes);
  }

  void addIndividualINode(INode iNode) {
    resolvedINodesMap.putIndividualINode(iNode);
  }

  List<INode> getPathINodes(String path) {
    return resolvedINodesMap.getPathINodes(path);
  }

  INode getTargetINode(String path) {
    List<INode> list = resolvedINodesMap.getPathINodes(path);
    return list.get(list.size() - 1);
  }

  List<INode> getChildINodes(String path) {
    return resolvedINodesMap.getChildINodes(path);
  }

  public TransactionLockTypes.INodeLockType getLockedINodeLockType(INode inode) {
    return allLockedInodesInTx.get(inode);
  }

  protected INode find(TransactionLockTypes.INodeLockType lock, String name, int parentId) throws PersistanceException {
    setINodeLockType(lock);
    INode inode = EntityManager.find(INode.Finder.ByPK_NameAndParentId, name, parentId);
    addLockedINodes(inode, lock);
    return inode;
  }

  protected INode find(
          TransactionLockTypes.INodeLockType lock,
          int id)
          throws PersistanceException {
    setINodeLockType(lock);
    INode inode = EntityManager.find(INode.Finder.ByINodeID, id);
    addLockedINodes(inode, lock);
    return inode;
  }

  protected void addLockedINodes(
          INode inode, TransactionLockTypes.INodeLockType lock) {
    if (inode == null) {
      return;
    }
    TransactionLockTypes.INodeLockType oldLock = allLockedInodesInTx.get(inode);
    if (oldLock == null || oldLock.compareTo(lock) < 0) {
      allLockedInodesInTx.put(inode, lock);
    }
  }

  protected void setINodeLockType(TransactionLockTypes.INodeLockType lock) throws StorageException {
    switch (lock) {
      case WRITE:
      case WRITE_ON_PARENT:
        EntityManager.writeLock();
        break;
      case READ:
        EntityManager.readLock();
        break;
      case READ_COMMITTED:
        EntityManager.readCommited();
        break;
    }
  }

  protected void acquireINodeAttributes() throws PersistanceException {
    List<HopINodeCandidatePK> pks = new ArrayList<HopINodeCandidatePK>();
    for (INode inode : getAllResolvedINodes()) {
      if (inode instanceof INodeDirectoryWithQuota) {
        HopINodeCandidatePK pk = new HopINodeCandidatePK(inode.getId());
        pks.add(pk);
      }
    }
    acquireLockList(DEFAULT_LOCK_TYPE, INodeAttributes.Finder.ByPKList, pks);
  }
    
  //TODO check if it does work or not and connect it to memchached
  // or use memcached to balance the reading by starting from reverse order of the path
  protected void setPartitionKey() {
    //      setPartitioningKey(PathMemcache.getInstance().getPartitionKey(locks.getInodeParam()[0]));
  }

  protected void setPartitioningKey(Integer inodeId) throws StorageException {
    if (inodeId == null || !isSetPartitionKeyEnabled()) {
      LOG.warn("Transaction Partition Key is not Set");
    } else {
      //set partitioning key
      Object[] key = new Object[2];
      key[0] = inodeId;
      key[1] = new Long(0);

      EntityManager.setPartitionKey(BlockInfoDataAccess.class, key);
      LOG.debug("Setting Partitioning Key to be " + inodeId);
    }
  }

  @Override
  protected final Type getType() {
    return Type.INode;
  }
}
