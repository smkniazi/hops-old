package se.sics.hop.metadata.persistence;

import java.net.MalformedURLException;
import se.sics.hop.metadata.persistence.context.entity.ReplicaUnderConstructionContext;
import se.sics.hop.metadata.persistence.context.entity.ExcessReplicaContext;
import se.sics.hop.metadata.persistence.context.entity.LeaderContext;
import se.sics.hop.metadata.persistence.entity.hop.HopLeader;
import se.sics.hop.metadata.persistence.context.entity.LeaseContext;
import se.sics.hop.metadata.persistence.context.entity.EntityContext;
import java.util.HashMap;
import java.util.Map;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.security.token.block.BlockKey;
import org.apache.hadoop.hdfs.server.blockmanagement.*;
import org.apache.hadoop.hdfs.server.namenode.*;
import se.sics.hop.metadata.persistence.context.Variables;
import se.sics.hop.metadata.persistence.entity.hop.HopVariable;
import se.sics.hop.metadata.persistence.dalwrapper.LeaseDALWrapper;
import se.sics.hop.metadata.persistence.context.entity.BlockInfoContext;
import se.sics.hop.metadata.persistence.context.entity.BlockTokenKeyContext;
import se.sics.hop.metadata.persistence.context.entity.CorruptReplicaContext;
import se.sics.hop.metadata.persistence.context.entity.INodeAttributesContext;
import se.sics.hop.metadata.persistence.context.entity.INodeContext;
import se.sics.hop.metadata.persistence.context.entity.InvalidatedBlockContext;
import se.sics.hop.metadata.persistence.context.entity.LeasePathContext;
import se.sics.hop.metadata.persistence.context.entity.PendingBlockContext;
import se.sics.hop.metadata.persistence.context.entity.ReplicaContext;
import se.sics.hop.metadata.persistence.context.entity.UnderReplicatedBlockContext;
import se.sics.hop.metadata.persistence.context.entity.VariableContext;
import se.sics.hop.metadata.persistence.dal.BlockInfoDataAccess;
import se.sics.hop.metadata.persistence.dal.BlockTokenKeyDataAccess;
import se.sics.hop.metadata.persistence.dal.CorruptReplicaDataAccess;
import se.sics.hop.metadata.persistence.dal.EntityDataAccess;
import se.sics.hop.metadata.persistence.dal.ExcessReplicaDataAccess;
import se.sics.hop.metadata.persistence.dal.INodeAttributesDataAccess;
import se.sics.hop.metadata.persistence.dal.INodeDataAccess;
import se.sics.hop.metadata.persistence.dal.InvalidateBlockDataAccess;
import se.sics.hop.metadata.persistence.dal.LeaderDataAccess;
import se.sics.hop.metadata.persistence.dal.LeaseDataAccess;
import se.sics.hop.metadata.persistence.dal.LeasePathDataAccess;
import se.sics.hop.metadata.persistence.dal.PendingBlockDataAccess;
import se.sics.hop.metadata.persistence.dal.ReplicaDataAccess;
import se.sics.hop.metadata.persistence.dal.ReplicaUnderConstructionDataAccess;
import se.sics.hop.metadata.persistence.dal.StorageInfoDataAccess;
import se.sics.hop.metadata.persistence.dal.UnderReplicatedBlockDataAccess;
import se.sics.hop.metadata.persistence.dal.VariableDataAccess;
import se.sics.hop.metadata.persistence.dalwrapper.BlockInfoDALWrapper;
import se.sics.hop.metadata.persistence.dalwrapper.BlockTokenDALWrapper;
import se.sics.hop.metadata.persistence.dalwrapper.INodeAttributeDALWrapper;
import se.sics.hop.metadata.persistence.dalwrapper.INodeDALWrapper;
import se.sics.hop.metadata.persistence.dalwrapper.PendingBlockInfoDALWrapper;
import se.sics.hop.metadata.persistence.dalwrapper.ReplicaUnderConstructionDALWrapper;
import se.sics.hop.metadata.persistence.dalwrapper.StorageInfoDALWrapper;
import se.sics.hop.metadata.persistence.entity.hop.HopCorruptReplica;
import se.sics.hop.metadata.persistence.entity.hop.HopExcessReplica;
import se.sics.hop.metadata.persistence.entity.hop.HopIndexedReplica;
import se.sics.hop.metadata.persistence.entity.hop.HopInvalidatedBlock;
import se.sics.hop.metadata.persistence.entity.hop.HopLeasePath;
import se.sics.hop.metadata.persistence.entity.hop.HopUnderReplicatedBlock;

/**
 *
 * @author Hooman <hooman@sics.se>
 * @author Mahmoud Ismail <maism@sics.se>
 */
public class StorageFactory {
  
  private static boolean isInitialized = false;
  private static DALStorageFactory dStorageFactory;
  private static Map<Class, EntityDataAccess> dataAccessWrappers = new HashMap<Class, EntityDataAccess>();
  

  public static StorageConnector getConnector() {
    return dStorageFactory.getConnector();
  }

  public static void setConfiguration(Configuration conf) {
    if(isInitialized)  return;
     Variables.registerDefaultValues();
     String jarFile="/home/mahmoud/src/hopstart/hop-metadata-dal-impl-ndb/target/hop-metadata-dal-impl-ndb-1.0-SNAPSHOT-jar-with-dependencies.jar";
    try {
      dStorageFactory = DALDriver.load(jarFile, "se.sics.hop.metadata.persistence.ndb.NdbStorageFactory");
    } catch (MalformedURLException ex) {
      ex.printStackTrace();
    } catch (ClassNotFoundException ex) {
      ex.printStackTrace();
    } catch (InstantiationException ex) {
     ex.printStackTrace();
    } catch (IllegalAccessException ex) {
      ex.printStackTrace();
    }
     dStorageFactory.setConfiguration(null);
    isInitialized = true;
  }

  public static Map<Class, EntityContext> createEntityContexts() {
    Map<Class, EntityContext> entityContexts = new HashMap<Class, EntityContext>();

    BlockInfoContext bic = new BlockInfoContext((BlockInfoDataAccess) getDataAccessWrapper(BlockInfoDataAccess.class));
    entityContexts.put(BlockInfo.class, bic);
    entityContexts.put(BlockInfoUnderConstruction.class, bic);
    entityContexts.put(ReplicaUnderConstruction.class, new ReplicaUnderConstructionContext((ReplicaUnderConstructionDataAccess) getDataAccessWrapper(ReplicaUnderConstructionDataAccess.class)));
    entityContexts.put(HopIndexedReplica.class, new ReplicaContext((ReplicaDataAccess) getDataAccess(ReplicaDataAccess.class)));
    entityContexts.put(HopExcessReplica.class, new ExcessReplicaContext((ExcessReplicaDataAccess) getDataAccess(ExcessReplicaDataAccess.class)));
    entityContexts.put(HopInvalidatedBlock.class, new InvalidatedBlockContext((InvalidateBlockDataAccess) getDataAccess(InvalidateBlockDataAccess.class)));
    entityContexts.put(Lease.class, new LeaseContext((LeaseDataAccess) getDataAccessWrapper(LeaseDataAccess.class)));
    entityContexts.put(HopLeasePath.class, new LeasePathContext((LeasePathDataAccess) getDataAccess(LeasePathDataAccess.class)));
    entityContexts.put(PendingBlockInfo.class, new PendingBlockContext((PendingBlockDataAccess) getDataAccessWrapper(PendingBlockDataAccess.class)));

    INodeContext inodeContext = new INodeContext((INodeDataAccess) getDataAccessWrapper(INodeDataAccess.class));
    entityContexts.put(INode.class, inodeContext);
    entityContexts.put(INodeDirectory.class, inodeContext);
    entityContexts.put(INodeFile.class, inodeContext);
    entityContexts.put(INodeDirectoryWithQuota.class, inodeContext);
    entityContexts.put(INodeSymlink.class, inodeContext);
    entityContexts.put(INodeFileUnderConstruction.class, inodeContext);

    entityContexts.put(HopCorruptReplica.class, new CorruptReplicaContext((CorruptReplicaDataAccess) getDataAccess(CorruptReplicaDataAccess.class)));
    entityContexts.put(HopUnderReplicatedBlock.class, new UnderReplicatedBlockContext((UnderReplicatedBlockDataAccess) getDataAccess(UnderReplicatedBlockDataAccess.class)));
    entityContexts.put(HopVariable.class, new VariableContext((VariableDataAccess) getDataAccess(VariableDataAccess.class)));
    entityContexts.put(HopLeader.class, new LeaderContext((LeaderDataAccess) getDataAccess(LeaderDataAccess.class)));
    entityContexts.put(BlockKey.class, new BlockTokenKeyContext((BlockTokenKeyDataAccess) getDataAccessWrapper(BlockTokenKeyDataAccess.class)));
    entityContexts.put(INodeAttributes.class, new INodeAttributesContext((INodeAttributesDataAccess) getDataAccessWrapper(INodeAttributesDataAccess.class)));

    getDataAccessWrapper(StorageInfoDataAccess.class);

    return entityContexts;
  }

  private static EntityDataAccess getDataAccessWrapper(Class type) {
    EntityDataAccess daW = null;
    if (dataAccessWrappers.containsKey(type)) {
      return dataAccessWrappers.get(type);
    }
    if (type == BlockInfoDataAccess.class) {
      daW = new BlockInfoDALWrapper((BlockInfoDataAccess) getDataAccess(BlockInfoDataAccess.class));
    } else if (type == ReplicaUnderConstructionDataAccess.class) {
      daW = new ReplicaUnderConstructionDALWrapper((ReplicaUnderConstructionDataAccess) getDataAccess(ReplicaUnderConstructionDataAccess.class));
    } else if (type == LeaseDataAccess.class) {
      daW = new LeaseDALWrapper((LeaseDataAccess) getDataAccess(LeaseDataAccess.class));
    } else if (type == PendingBlockDataAccess.class) {
      daW = new PendingBlockInfoDALWrapper((PendingBlockDataAccess) getDataAccess(PendingBlockDataAccess.class));
    } else if (type == INodeDataAccess.class) {
      daW = new INodeDALWrapper((INodeDataAccess) getDataAccess(INodeDataAccess.class));
    } else if (type == BlockTokenKeyDataAccess.class) {
      daW = new BlockTokenDALWrapper((BlockTokenKeyDataAccess) getDataAccess(BlockTokenKeyDataAccess.class));
    } else if (type == INodeAttributesDataAccess.class) {
      daW = new INodeAttributeDALWrapper((INodeAttributesDataAccess) getDataAccess(INodeAttributesDataAccess.class));
    } else if (type == StorageInfoDataAccess.class) {
      daW = new StorageInfoDALWrapper((StorageInfoDataAccess) getDataAccess(StorageInfoDataAccess.class));
    }
    dataAccessWrappers.put(type, daW);
    return daW;
  }

  public static EntityDataAccess getDataAccess(Class type) {
    if (dataAccessWrappers.containsKey(type)) {
      return dataAccessWrappers.get(type);
    }
    return dStorageFactory.getDataAccess(type);
  }
}
