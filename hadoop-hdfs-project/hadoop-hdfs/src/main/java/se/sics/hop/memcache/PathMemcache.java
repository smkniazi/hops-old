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
package se.sics.hop.memcache;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import net.spy.memcached.internal.OperationCompletionListener;
import net.spy.memcached.internal.OperationFuture;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.DFSConfigKeys;
import org.apache.hadoop.hdfs.server.namenode.INode;
import org.apache.hadoop.hdfs.server.namenode.INodeDirectory;
import se.sics.hop.exception.PersistanceException;
import se.sics.hop.metadata.StorageFactory;
import se.sics.hop.metadata.hdfs.dal.INodeDataAccess;
import se.sics.hop.transaction.handler.HDFSOperationType;
import se.sics.hop.transaction.handler.LightWeightRequestHandler;

/**
 *
 * @author Mahmoud Ismail <maism@sics.se>
 */
public class PathMemcache {

  private class CacheEntry {

    private int[] inodeIds;

    public CacheEntry(int[] inodeIds) {
      this.inodeIds = inodeIds;
    }

    int getPartitionKey() {
      return inodeIds[inodeIds.length - 1];
    }

    int[] getParentIds() {
      return PathMemcache.getParentIds(inodeIds);
    }
  }
  private static final Log LOG = LogFactory.getLog(PathMemcache.class);
  private static PathMemcache instance = null;
  private MemcachedClientPool mcpool;
  private boolean isEnabled;
  private int keyExpiry;
  private String keyPrefix;
  private final HashMap<String, CacheEntry> cache = new HashMap<String, CacheEntry>();

  private PathMemcache() {
  }

  public static PathMemcache getInstance() {
    if (instance == null) {
      instance = new PathMemcache();
    }
    return instance;
  }

  public void setConfiguration(Configuration conf) throws IOException {
    int numberOfConnections = conf.getInt(DFSConfigKeys.DFS_MEMCACHE_CONNECTION_POOL_SIZE, DFSConfigKeys.DFS_MEMCACHE_CONNECTION_POOL_SIZE_DEFAULT);
    String server = conf.get(DFSConfigKeys.DFS_MEMCACHE_SERVER, DFSConfigKeys.DFS_MEMCACHE_SERVER_DEFAULT);
    keyExpiry = conf.getInt(DFSConfigKeys.DFS_MEMCACHE_KEY_EXPIRY_IN_SECONDS, DFSConfigKeys.DFS_MEMCACHE_KEY_EXPIRY_IN_SECONDS_DEFAULT);
    keyPrefix = conf.get(DFSConfigKeys.DFS_MEMCACHE_KEY_PREFIX, DFSConfigKeys.DFS_MEMCACHE_KEY_PREFIX_DEFAULT);
    isEnabled = conf.getBoolean(DFSConfigKeys.DFS_MEMCACHE_ENABLED, DFSConfigKeys.DFS_MEMCACHE_ENABLED_DEFAULT);
    if (isEnabled) {
      mcpool = new MemcachedClientPool(numberOfConnections, server);
    }
  }

  public void set(final String path, final INode[] inodes) {
    if (isEnabled) {
      final String key = getKey(path);
      final int[] inodeIds = getINodeIds(inodes);
      mcpool.poll().set(key, keyExpiry, inodeIds).addListener(new OperationCompletionListener() {
        @Override
        public void onComplete(OperationFuture<?> f) throws Exception {
          LOG.debug("SET for path (" + path + ")  " + key + "=" + Arrays.toString(inodeIds));
        }
      });
    }
  }

  public void get(String path) throws IOException {
    if (isEnabled) {
      int[] inodeIds = (int[]) mcpool.poll().get(getKey(path));
      if (inodeIds != null) {
        LOG.debug("GET for path (" + path + ")  got value = " + Arrays.toString(inodeIds));
        verifyINodes(path, inodeIds);
      }
    }
  }

  public Integer getPartitionKey(String path) {
    if (isEnabled) {
      LOG.debug("GET PARTITION KEY for path (" + path + ")");
      CacheEntry ce = cache.get(path);
      if (ce != null) {
        return ce.getPartitionKey();
      }
    }
    return null;
  }

  public Pair<String[], int[]> getNameAndParentIds(String path) {
    if (isEnabled) {
      LOG.debug("GET NAME_AND_PARENTIDS for path (" + path + ")");
      CacheEntry ce = cache.get(path);
      if (ce != null) {
        String[] names = getNamesWithoutRoot(path);
        int[] parentIds = ce.getParentIds();
        return new Pair<String[], int[]>(names, parentIds);
      }
    }
    return null;
  }

  private void verifyINodes(final String path, final int[] inodeIds) throws IOException {
    if (checkINodes(path, inodeIds)) {
      LOG.debug("GET verified the data we got from memcached with the database data");
      cache.put(path, new CacheEntry(inodeIds));
    } else {
      final String key = getKey(path);
      mcpool.poll().delete(key).addListener(new OperationCompletionListener() {
        @Override
        public void onComplete(OperationFuture<?> f) throws Exception {
          LOG.debug("DELETE for path (" + path + ")  " + key + "=" + Arrays.toString(inodeIds));
        }
      });
    }
  }

  private boolean checkINodes(String path, int[] inodeIds) throws IOException {
    final String[] names = getNamesWithoutRoot(path);
    final int[] parentIds = getParentIds(inodeIds);

    boolean verified = false;
    if (names.length == parentIds.length) {
      List<INode> inodes = getINodes(names, parentIds);
      if (inodes.size() == names.length) {
        boolean noChangeInInodes = true;
        for (int i = 0; i < inodes.size(); i++) {
          INode inode = inodes.get(i);
          noChangeInInodes = inode.getLocalName().equals(names[i]) && inode.getParentId() == parentIds[i] && inode.getId() == inodeIds[i + 1];
          if (!noChangeInInodes) {
            break;
          }
        }
        verified = noChangeInInodes;
      }
    }
    return verified;
  }

  private List<INode> getINodes(final String[] names, final int[] parentIds) throws IOException {
    return (List<INode>) new LightWeightRequestHandler(HDFSOperationType.GET_INODES_BATCH) {
      @Override
      public Object performTask() throws PersistanceException, IOException {
        INodeDataAccess da = (INodeDataAccess) StorageFactory.getDataAccess(INodeDataAccess.class);
        StorageFactory.getConnector().beginTransaction();
        List<INode> inodes = da.getINodesPkBatched(names, parentIds);
        StorageFactory.getConnector().commit();
        return inodes;
      }
    }.handle();
  }

  private static String[] getNamesWithoutRoot(String path) {
    String[] names = INodeDirectory.getPathNames(path);
    return Arrays.copyOfRange(names, 1, names.length);
  }

  private static int[] getParentIds(int[] inodeIds) {
    return Arrays.copyOfRange(inodeIds, 0, inodeIds.length - 1);
  }

  private String getKey(String path) {
    return keyPrefix + DigestUtils.sha256Hex(path);
  }

  private int[] getINodeIds(INode[] inodes) {
    int[] inodeIds = new int[inodes.length];
    for (int i = 0; i < inodes.length; i++) {
      inodeIds[i] = inodes[i].getId();
    }
    return inodeIds;
  }
}
