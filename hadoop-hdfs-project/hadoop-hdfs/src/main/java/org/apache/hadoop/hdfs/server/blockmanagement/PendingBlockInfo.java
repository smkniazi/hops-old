package org.apache.hadoop.hdfs.server.blockmanagement;

import se.sics.hop.metadata.hdfs.entity.FinderType;

/**
 *
 * @author Hooman <hooman@sics.se>
 */
/**
 * An object that contains information about a block that is being replicated.
 * It records the timestamp when the system started replicating the most recent
 * copy of this block. It also records the number of replication requests that
 * are in progress.
 */
public class PendingBlockInfo {

  public static enum Finder implements FinderType<PendingBlockInfo> {

    ByBlockId, ByInodeId, ByInodeIds, All, ByTimeLimit;

    @Override
    public Class getType() {
      return PendingBlockInfo.class;
    }
  }
  private long blockId;
  private int inodeId;
  private long timeStamp;
  private int numReplicasInProgress;

  public PendingBlockInfo(long blockId, int inodeId, long timestamp, int numReplicas) {
    this.blockId = blockId;
    this.inodeId = inodeId;
    this.timeStamp = timestamp;
    this.numReplicasInProgress = numReplicas;
  }

  public long getTimeStamp() {
    return timeStamp;
  }

  void setTimeStamp(long timestamp) {
    timeStamp = timestamp;
  }

  void incrementReplicas(int increment) {
    numReplicasInProgress += increment;
  }

  void decrementReplicas() {
    numReplicasInProgress--;
    assert (numReplicasInProgress >= 0);
  }

  public int getNumReplicas() {
    return numReplicasInProgress;
  }

  /**
   * @return the blockId
   */
  public long getBlockId() {
    return blockId;
  }
  
  public int getInodeId(){
    return inodeId;
  }

  /**
   * @param blockId the blockId to set
   */
  public void setBlockId(long blockId) {
    this.blockId = blockId;
  }

  public void setInodeId(int inodeId) {
    this.inodeId = inodeId;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || !(obj instanceof PendingBlockInfo)) {
      return false;
    }

    PendingBlockInfo other = (PendingBlockInfo) obj;
    if (this.getBlockId() == other.getBlockId()) {
      return true;
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 71 * hash + (int) (this.blockId ^ (this.blockId >>> 32));
    return hash;
  }
}
