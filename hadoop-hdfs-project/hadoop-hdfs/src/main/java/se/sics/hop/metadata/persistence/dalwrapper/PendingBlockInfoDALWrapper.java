/*
 * Copyright 2013 Apache Software Foundation.
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
package se.sics.hop.metadata.persistence.dalwrapper;

import java.util.Collection;
import java.util.List;
import org.apache.hadoop.hdfs.server.blockmanagement.PendingBlockInfo;
import se.sics.hop.metadata.persistence.DALWrapper;
import se.sics.hop.metadata.persistence.dal.PendingBlockDataAccess;
import se.sics.hop.metadata.persistence.entity.hdfs.HopPendingBlockInfo;
import se.sics.hop.metadata.persistence.exceptions.StorageException;

/**
 *
 * @author Mahmoud Ismail <maism@sics.se>
 */
public class PendingBlockInfoDALWrapper extends DALWrapper<PendingBlockInfo, HopPendingBlockInfo> {

  private final PendingBlockDataAccess dataAccces;

  public PendingBlockInfoDALWrapper(PendingBlockDataAccess dataAccess) {
    this.dataAccces = dataAccess;
  }

  public List<PendingBlockInfo> findByTimeLimitLessThan(long timeLimit) throws StorageException {
    return (List<PendingBlockInfo>) convertDALtoHDFS(dataAccces.findByTimeLimitLessThan(timeLimit));
  }

  public List<PendingBlockInfo> findAll() throws StorageException {
    return (List<PendingBlockInfo>) convertDALtoHDFS(dataAccces.findAll());
  }

  public PendingBlockInfo findByPKey(long blockId) throws StorageException {
    return convertDALtoHDFS(dataAccces.findByPKey(blockId));
  }

  public int countValidPendingBlocks(long timeLimit) throws StorageException {
    return dataAccces.countValidPendingBlocks(timeLimit);
  }

  public void prepare(Collection<PendingBlockInfo> removed, Collection<PendingBlockInfo> newed, Collection<PendingBlockInfo> modified) throws StorageException {
    dataAccces.prepare(convertHDFStoDAL(removed), convertHDFStoDAL(newed), convertHDFStoDAL(modified));
  }

  public void removeAll() throws StorageException {
    dataAccces.removeAll();
  }

  @Override
  public HopPendingBlockInfo convertHDFStoDAL(PendingBlockInfo hdfsClass) throws StorageException {
    return new HopPendingBlockInfo(hdfsClass.getBlockId(), hdfsClass.getTimeStamp(), hdfsClass.getNumReplicas());
  }

  @Override
  public PendingBlockInfo convertDALtoHDFS(HopPendingBlockInfo dalClass) throws StorageException {
    return new PendingBlockInfo(dalClass.getBlockId(), dalClass.getTimeStamp(), dalClass.getNumReplicas());
  }
}
