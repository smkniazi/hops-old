package org.apache.hadoop.hdfs.server.blockmanagement;

import java.util.Comparator;
import org.apache.hadoop.hdfs.server.common.HdfsServerConstants;
import org.apache.hadoop.hdfs.server.common.HdfsServerConstants.ReplicaState;
import se.sics.hop.metadata.hdfs.entity.FinderType;
import se.sics.hop.metadata.hdfs.entity.hop.HopIndexedReplica;

/**
 * ReplicaUnderConstruction contains information about replicas while they are
 * under construction. The GS, the length and the state of the replica is as
 * reported by the data-node. It is not guaranteed, but expected, that
 * data-nodes actually have corresponding replicas.
 *
 * @author Kamal Hakimzadeh <kamal@sics.se>
 */
public class ReplicaUnderConstruction extends HopIndexedReplica {

  public static enum Finder implements FinderType<ReplicaUnderConstruction> {

    ByBlockId, ByINodeId, ByINodeIds;

    @Override
    public Class getType() {
      return ReplicaUnderConstruction.class;
    }
  }

  public static enum Order implements Comparator<ReplicaUnderConstruction> {

    ByIndex() {

      @Override
      public int compare(ReplicaUnderConstruction o1, ReplicaUnderConstruction o2) {
        if (o1.getIndex() < o2.getIndex()) {
          return -1;
        } else {
          return 1;
        }
      }
    };
  }
  HdfsServerConstants.ReplicaState state;

  public ReplicaUnderConstruction(ReplicaState state, int storageId, long blockId, int inodeId, int index) {
    super(blockId, storageId, inodeId,  index);
    this.state = state;
  }

  public ReplicaState getState() {
    return state;
  }

  public void setState(ReplicaState state) {
    this.state = state;
  }
}
