package se.sics.hop.erasure_coding;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.protocol.HdfsFileStatus;
import org.apache.hadoop.hdfs.protocol.LocatedBlock;
import org.apache.hadoop.hdfs.protocol.LocatedBlocks;
import org.apache.hadoop.hdfs.server.namenode.FSNamesystem;
import org.apache.hadoop.hdfs.server.namenode.INode;
import org.apache.hadoop.util.Daemon;
import org.apache.hadoop.util.StringUtils;
import se.sics.hop.exception.PersistanceException;
import se.sics.hop.metadata.StorageFactory;
import se.sics.hop.metadata.hdfs.dal.EncodingStatusDataAccess;
import se.sics.hop.metadata.lock.ErasureCodingTransactionLockAcquirer;
import se.sics.hop.metadata.lock.HDFSTransactionLockAcquirer;
import se.sics.hop.transaction.EntityManager;
import se.sics.hop.transaction.handler.*;
import se.sics.hop.transaction.lock.TransactionLockTypes;
import se.sics.hop.transaction.lock.TransactionLocks;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.hadoop.util.ExitUtil.terminate;

public class ErasureCodingManager extends Configured{

  static final Log LOG = LogFactory.getLog(ErasureCodingManager.class);

  public static final String ERASURE_CODING_ENABLED_KEY = "se.sics.hop.erasure_coding.enabled";
  public static final String PARITY_FOLDER = "se.sics.hop.erasure_coding.parity_folder";
  public static final String DEFAULT_PARITY_FOLDER = "/parity";
  public static final String ENCODING_MANAGER_CLASSNAME_KEY = "se.sics.hop.erasure_coding.encoding_manager";
  public static final String BLOCK_REPAIR_MANAGER_CLASSNAME_KEY = "se.sics.hop.erasure_coding.block_rapair_manager";
  public static final String RECHECK_INTERVAL_KEY = "se.sics.hop.erasure_coding.recheck_interval";
  public static final int DEFAULT_RECHECK_INTERVAL = 5 * 60 * 1000;
  public static final String ACTIVE_ENCODING_LIMIT_KEY = "se.sics.hop.erasure_coding.active_encoding_limit";
  public static final int DEFAULT_ACTIVE_ENCODING_LIMIT = 10;
  public static final String ACTIVE_REPAIR_LIMIT_KEY = "se.sics.hop.erasure_coding.active_repair_limit";
  public static final int DEFAULT_ACTIVE_REPAIR_LIMIT = 10;
  public static final String REPAIR_DELAY_KEY = "se.sics.hop.erasure_coding.repair_delay";
  public static final int DEFAULT_REPAIR_DELAY_KEY = 30 * 60 * 1000;
  public static final String ACTIVE_PARITY_REPAIR_LIMIT_KEY = "se.sics.hop.erasure_coding.active_parity_repair_limit";
  public static final int DEFAULT_ACTIVE_PARITY_REPAIR_LIMIT = 10;
  public static final String PARITY_REPAIR_DELAY_KEY = "se.sics.hop.erasure_coding.parity_repair_delay";
  public static final int DEFAULT_PARITY_REPAIR_DELAY = 30 * 60 * 1000;
  public static final String DELETION_LIMIT_KEY = "se.sics.hop.erasure_coding.deletion_limit";
  public static final int DEFAULT_DELETION_LIMIT = 100;

  private final FSNamesystem namesystem;
  private final Daemon erasureCodingMonitorThread = new Daemon(new ErasureCodingMonitor());
  private EncodingManager encodingManager;
  private BlockRepairManager blockRepairManager;
  private String parityFolder;
  private final long recheckInterval;
  private final int activeEncodingLimit;
  private int activeEncodings = 0;
  private final int activeRepairLimit;
  private final int activeParityRepairLimit;
  private int activeRepairs = 0;
  private int activeParityRepairs = 0;
  private final int repairDelay;
  private final int parityRepairDelay;
  private final int deletionLimit;

  private static boolean enabled = false;

  public ErasureCodingManager(FSNamesystem namesystem, Configuration conf) {
    super(conf);
    this.namesystem = namesystem;
    this.parityFolder = conf.get(PARITY_FOLDER, DEFAULT_PARITY_FOLDER);
    this.recheckInterval = conf.getInt(RECHECK_INTERVAL_KEY, DEFAULT_RECHECK_INTERVAL);
    this.activeEncodingLimit = conf.getInt(ACTIVE_ENCODING_LIMIT_KEY, DEFAULT_ACTIVE_ENCODING_LIMIT);
    this.activeRepairLimit = conf.getInt(ACTIVE_REPAIR_LIMIT_KEY, DEFAULT_ACTIVE_REPAIR_LIMIT);
    this.activeParityRepairLimit = conf.getInt(ACTIVE_PARITY_REPAIR_LIMIT_KEY, DEFAULT_ACTIVE_PARITY_REPAIR_LIMIT);
    this.repairDelay = conf.getInt(REPAIR_DELAY_KEY, DEFAULT_REPAIR_DELAY_KEY);
    this.parityRepairDelay = conf.getInt(PARITY_REPAIR_DELAY_KEY, DEFAULT_PARITY_REPAIR_DELAY);
    this.deletionLimit = conf.getInt(DELETION_LIMIT_KEY, DEFAULT_DELETION_LIMIT);
    enabled = conf.getBoolean(ERASURE_CODING_ENABLED_KEY, false);
  }

  private boolean loadRaidNodeClasses() {
    try {
      Class<?> encodingManagerClass = getConf().getClass(ENCODING_MANAGER_CLASSNAME_KEY, null);
      if (encodingManagerClass == null || !EncodingManager.class.isAssignableFrom(encodingManagerClass)) {
        throw new ClassNotFoundException(encodingManagerClass + " is not an implementation of " + EncodingManager.class.getCanonicalName());
      }
      Constructor<?> encodingManagerConstructor = encodingManagerClass.getConstructor(
          new Class[] {Configuration.class} );
      encodingManager = (EncodingManager) encodingManagerConstructor.newInstance(getConf());

      Class<?> blockRepairManagerClass = getConf().getClass(BLOCK_REPAIR_MANAGER_CLASSNAME_KEY, null);
      if (blockRepairManagerClass == null || !BlockRepairManager.class.isAssignableFrom(blockRepairManagerClass)) {
        throw new ClassNotFoundException(blockRepairManagerClass + " is not an implementation of " + BlockRepairManager.class.getCanonicalName());
      }
      Constructor<?> blockRepairManagerConstructor = blockRepairManagerClass.getConstructor(
          new Class[] {Configuration.class} );
      blockRepairManager = (BlockRepairManager) blockRepairManagerConstructor.newInstance(getConf());
    } catch (Exception e) {
      LOG.error("Could not load erasure coding classes", e);
      return false;
    }

    return true;
  }

  public void activate() {
    if (!loadRaidNodeClasses()) {
      LOG.error("ErasureCodingMonitor not started. An error occurred during the loading of the encoding library.");
      return;
    }

    erasureCodingMonitorThread.start();
    LOG.info("ErasureCodingMonitor started");
  }

  public void close() {
    try {
      if (erasureCodingMonitorThread != null) {
        erasureCodingMonitorThread.interrupt();
        erasureCodingMonitorThread.join(3000);
      }
    } catch (InterruptedException ie) {
    }
    LOG.info("ErasureCodingMonitor stopped");
  }

  public static boolean isErasureCodingEnabled(Configuration conf) {
    return conf.getBoolean(ERASURE_CODING_ENABLED_KEY, false);
  }

  private class ErasureCodingMonitor implements Runnable {

    @Override
    public void run() {
      while (namesystem.isRunning()) {
        try {
          try {
            if (namesystem.isInSafeMode()) {
              continue;
            }
          } catch (IOException e) {
            LOG.info("In safe mode skipping this round");
          }
          if(namesystem.isLeader()){
            checkActiveEncodings();
            scheduleEncodings();
            checkActiveRepairs();
            scheduleSourceRepairs();
            scheduleParityRepairs();
            garbageCollect();
            checkRevoked();
          }
          try {
            Thread.sleep(recheckInterval);
          } catch (InterruptedException ie) {
            LOG.warn("ErasureCodingMonitor thread received InterruptedException.", ie);
            break;
          }
        } catch (Throwable e) {
          LOG.error(e);
        }
      }
    }
  }

  private void checkActiveEncodings() {
    LOG.info("Checking active encoding.");
    List<Report> reports = encodingManager.computeReports();
    for (Report report : reports) {
      switch (report.getStatus()) {
        case ACTIVE:
          break;
        case FINISHED:
          LOG.info("Encoding finished for " + report.getFilePath());
          finalizeEncoding(report.getFilePath());
          activeEncodings--;
          break;
        case FAILED:
          LOG.info("Encoding failed for " + report.getFilePath());
          // This would be a problem with multiple name nodes as it is not atomic. Only one thread here.
          updateEncodingStatus(report.getFilePath(), EncodingStatus.Status.ENCODING_FAILED);
          updateEncodingStatus(report.getFilePath(), EncodingStatus.ParityStatus.REPAIR_FAILED);
          activeEncodings--;
          break;
        case CANCELED:
          LOG.info("Encoding canceled for " + report.getFilePath());
          updateEncodingStatus(report.getFilePath(), EncodingStatus.Status.ENCODING_CANCELED);
          activeEncodings--;
          break;
      }
    }
  }

  private void finalizeEncoding(final String path) {
    LOG.info("Finilizing encoding for " + path);
    try {
      final EncodingStatus status = namesystem.getEncodingStatus(path);
      final int parityInodeId = namesystem.findInodeId(parityFolder + "/" + status.getParityFileName());

      HDFSTransactionalRequestHandler handler =
          new HDFSTransactionalRequestHandler(HDFSOperationType.GET_INODE) {
            @Override
            public TransactionLocks acquireLock() throws PersistanceException, IOException {
              ErasureCodingTransactionLockAcquirer tla = new ErasureCodingTransactionLockAcquirer();
              tla.getLocks().
                  addEncodingStatusLock(status.getInodeId()).
                  addINode(TransactionLockTypes.INodeResolveType.PATH,
                      TransactionLockTypes.INodeLockType.WRITE, new String[]{path});
              return tla.acquire();
            }

            @Override
            public Object performTask() throws PersistanceException, IOException {
              // Should be necessary for atomicity
              EncodingStatus encodingStatus = EntityManager.find(EncodingStatus.Finder.ByInodeId, status.getInodeId());
              encodingStatus.setStatus(EncodingStatus.Status.ENCODED);
              encodingStatus.setStatusModificationTime(System.currentTimeMillis());
              encodingStatus.setParityInodeId(parityInodeId);
              encodingStatus.setParityStatus(EncodingStatus.ParityStatus.HEALTHY);
              encodingStatus.setParityStatusModificationTime(System.currentTimeMillis());
              EntityManager.update(encodingStatus);
              return null;
            }
          };
      handler.handle(this);
    } catch (IOException e) {
      LOG.error(StringUtils.stringifyException(e));
    }
  }

  private void updateEncodingStatus(String filePath, EncodingStatus.Status status) {
    try {
      int id = namesystem.findInodeId(filePath);
      namesystem.updateEncodingStatus(filePath, id, status);
    } catch (IOException e) {
      LOG.error(StringUtils.stringifyException(e));
    }
  }

  private void updateEncodingStatus(String filePath, EncodingStatus.ParityStatus status) {
    try {
      int id = namesystem.findInodeId(filePath);
      namesystem.updateEncodingStatus(filePath, id, status);
    } catch (IOException e) {
      LOG.error(StringUtils.stringifyException(e));
    }
  }

  private void scheduleEncodings() {
    LOG.info("Scheuling encodings.");
    final int limit = activeEncodingLimit - activeEncodings;
    if (limit <= 0) {
      return;
    }

    LightWeightRequestHandler findHandler = new LightWeightRequestHandler(
        EncodingStatusOperationType.FIND_REQUESTED_ENCODINGS) {
      @Override
      public Object performTask() throws PersistanceException, IOException {
        EncodingStatusDataAccess<EncodingStatus> dataAccess = (EncodingStatusDataAccess)
            StorageFactory.getDataAccess(EncodingStatusDataAccess.class);
        return dataAccess.findRequestedEncodings(limit);
      }
    };

    try {
      Collection<EncodingStatus> requestedEncodings = (Collection<EncodingStatus>) findHandler.handle();

      for (EncodingStatus encodingStatus : requestedEncodings) {
        LOG.info("Trying to schedule encoding for " + encodingStatus);
        INode iNode = namesystem.findInode(encodingStatus.getInodeId());
        if (iNode == null) {
          LOG.error("findInode returned null for id " + encodingStatus.getInodeId());
          continue;
        }
        if (iNode.isUnderConstruction()) {
          // It might still be written to the file
          LOG.info("Still under construction. Encoding not scheduled for " + iNode.getId());
          continue;
        }

        String path = namesystem.getPath(iNode);
        if (iNode == null) {
          continue;
        }

        LOG.info("Schedule encoding for " + path);
        UUID parityFileName = UUID.randomUUID();
        encodingManager.encodeFile(encodingStatus.getEncodingPolicy(), new Path(path),
            new Path(parityFolder + "/" + parityFileName.toString()));
        namesystem.updateEncodingStatus(path, encodingStatus.getInodeId(), EncodingStatus.Status.ENCODING_ACTIVE,
            parityFileName.toString());
        activeEncodings++;
      }
    } catch (IOException e) {
      LOG.error(StringUtils.stringifyException(e));
    }
  }

  private void checkActiveRepairs() {
    LOG.info("Checking active repairs.");
    List<Report> reports = blockRepairManager.computeReports();
    for (Report report : reports) {
      switch (report.getStatus()) {
        case ACTIVE:
          break;
        case FINISHED:
          LOG.info("Repair finished for " + report.getFilePath());
          if (isParityFile(report.getFilePath())) {
            checkFixedParity(report.getFilePath());
            activeParityRepairs--;
          } else {
            checkFixedSource(report.getFilePath());
            activeRepairs--;
          }
          break;
        case FAILED:
          LOG.info("Repair failed for " + report.getFilePath());
          if (isParityFile(report.getFilePath())) {
            updateEncodingStatus(report.getFilePath(), EncodingStatus.ParityStatus.REPAIR_FAILED);
            activeParityRepairs--;
          } else {
            updateEncodingStatus(report.getFilePath(), EncodingStatus.Status.REPAIR_FAILED);
            activeRepairs--;
          }
          break;
        case CANCELED:
          LOG.info("Repair canceled for " + report.getFilePath());
          if (isParityFile(report.getFilePath())) {
            updateEncodingStatus(report.getFilePath(), EncodingStatus.ParityStatus.REPAIR_CANCELED);
            activeParityRepairs--;
          } else {
            updateEncodingStatus(report.getFilePath(), EncodingStatus.Status.REPAIR_CANCELED);
            activeRepairs--;
          }
          break;
      }
    }
  }

  private void checkFixedSource(final String path) throws IOException {
    final int inodeId = namesystem.findInodeId(path);

    TransactionalRequestHandler checkFixedHandler = new TransactionalRequestHandler(HDFSOperationType.GET_INODE) {
      @Override
      public TransactionLocks acquireLock() throws PersistanceException, IOException {
        ErasureCodingTransactionLockAcquirer lockAcquirer = new ErasureCodingTransactionLockAcquirer();
        lockAcquirer.getLocks()
            .addEncodingStatusLock(inodeId)
            .addINode(TransactionLockTypes.INodeResolveType.PATH,
                TransactionLockTypes.INodeLockType.WRITE, new String[]{path});
        return lockAcquirer.acquire();
      }

      @Override
      public Object performTask() throws PersistanceException, IOException {
        EncodingStatus status = EntityManager.find(EncodingStatus.Finder.ByInodeId, inodeId);
        if (status.getLostBlocks() == 0) {
          status.setStatus(EncodingStatus.Status.ENCODED);
        } else {
          status.setStatus(EncodingStatus.Status.REPAIR_REQUESTED);
        }
        status.setStatusModificationTime(System.currentTimeMillis());
        EntityManager.update(status);
        return null;
      }
    };
    checkFixedHandler.handle();
  }

  private void checkFixedParity(final String path) throws IOException {
    final int inodeId = namesystem.findInodeId(path);

    TransactionalRequestHandler checkFixedHandler = new TransactionalRequestHandler(HDFSOperationType.GET_INODE) {
      @Override
      public TransactionLocks acquireLock() throws PersistanceException, IOException {
        ErasureCodingTransactionLockAcquirer lockAcquirer = new ErasureCodingTransactionLockAcquirer();
        lockAcquirer.getLocks()
            .addEncodingStatusLock(inodeId)
            .addINode(TransactionLockTypes.INodeResolveType.PATH,
                TransactionLockTypes.INodeLockType.WRITE, new String[]{path});
        return lockAcquirer.acquire();
      }

      @Override
      public Object performTask() throws PersistanceException, IOException {
        EncodingStatus status = EntityManager.find(EncodingStatus.Finder.ByParityInodeId, inodeId);
        if (status.getLostParityBlocks() == 0) {
          status.setParityStatus(EncodingStatus.ParityStatus.HEALTHY);
        } else {
          status.setParityStatus(EncodingStatus.ParityStatus.REPAIR_REQUESTED);
        }
        status.setParityStatusModificationTime(System.currentTimeMillis());
        EntityManager.update(status);
        return null;
      }
    };
    checkFixedHandler.handle();
  }

  private void scheduleSourceRepairs() {
    LOG.info("Scheduling repairs");
    final int limit = activeRepairLimit - activeRepairs;
    if (limit <= 0) {
      return;
    }

    LightWeightRequestHandler findHandler = new LightWeightRequestHandler(
        EncodingStatusOperationType.FIND_REQUESTED_REPAIRS) {
      @Override
      public Object performTask() throws PersistanceException, IOException {
        EncodingStatusDataAccess<EncodingStatus> dataAccess = (EncodingStatusDataAccess)
            StorageFactory.getDataAccess(EncodingStatusDataAccess.class);
        return dataAccess.findRequestedRepairs(limit);
      }
    };

    try {
      Collection<EncodingStatus> requestedRepairs = (Collection<EncodingStatus>) findHandler.handle();
      for (EncodingStatus encodingStatus : requestedRepairs) {
        LOG.info("Scheduling source repair  for " + encodingStatus);
        if (System.currentTimeMillis() - encodingStatus.getStatusModificationTime() < repairDelay) {
          LOG.info("Skipping source repair. Delay not reached: " + repairDelay);
          continue;
        }

        if (encodingStatus.isParityRepairActive()) {
          LOG.info("Skipping source repair. Parity repair is active");
          continue;
        }

        INode iNode = namesystem.findInode(encodingStatus.getInodeId());
        String path = namesystem.getPath(iNode);
        if (iNode == null) {
          LOG.info("Skipping source repair. Path could not be found.");
          continue;
        }
        // Set status before doing something. In case the file is recovered inbetween we don't have an invalid status.
        // If starting repair fails somehow then this should be detected by a timeout later.
        namesystem.updateEncodingStatus(path, iNode.getId(), EncodingStatus.Status.REPAIR_ACTIVE);
        LOG.info("Status set to source repair active " + encodingStatus);
        blockRepairManager.repairSourceBlocks(encodingStatus.getEncodingPolicy().getCodec(), new Path(path),
            new Path(parityFolder + "/" + encodingStatus.getParityFileName()));
        LOG.info("Scheulded job for source repair " + encodingStatus);
        activeRepairs++;
      }
    } catch (IOException e) {
      LOG.error(e);
    }
  }

  private void scheduleParityRepairs() {
    LOG.info("Scheduling parity repairs");
    final int limit = activeParityRepairLimit - activeParityRepairs;
    if (limit <= 0) {
      return;
    }

    LightWeightRequestHandler findHandler = new LightWeightRequestHandler(
        EncodingStatusOperationType.FIND_REQUESTED_PARITY_REPAIRS) {
      @Override
      public Object performTask() throws PersistanceException, IOException {
        EncodingStatusDataAccess<EncodingStatus> dataAccess = (EncodingStatusDataAccess)
            StorageFactory.getDataAccess(EncodingStatusDataAccess.class);
        return dataAccess.findRequestedParityRepairs(limit);
      }
    };

    try {
      Collection<EncodingStatus> requestedRepairs = (Collection<EncodingStatus>) findHandler.handle();
      for (EncodingStatus encodingStatus : requestedRepairs) {
        LOG.info("Scheduling parity repair  for " + encodingStatus);
        if (System.currentTimeMillis() - encodingStatus.getParityStatusModificationTime() < parityRepairDelay) {
          LOG.info("Skipping  parity repair. Delay not reached: " + parityRepairDelay);
          continue;
        }

        if (encodingStatus.getStatus().equals(EncodingStatus.Status.ENCODED) == false) {
          // Only repair parity for non-broken source files. Otherwise repair source file first.
          LOG.info("Skipping parity repair. Source file not healthy.");
          continue;
        }

        INode iNode = namesystem.findInode(encodingStatus.getInodeId());
        String path = namesystem.getPath(iNode);
        if (iNode == null) {
          LOG.info("Skipping parity repair. Path could not be found.");
          continue;
        }
        // Set status before doing something. In case the file is recovered inbetween we don't have an invalid status.
        // If starting repair fails somehow then this should be detected by a timeout later.
        namesystem.updateEncodingStatus(path, iNode.getId(), EncodingStatus.ParityStatus.REPAIR_ACTIVE);
        LOG.info("Status set to parity repair active " + encodingStatus);
        blockRepairManager.repairParityBlocks(encodingStatus.getEncodingPolicy().getCodec(), new Path(path),
            new Path(parityFolder + "/" + encodingStatus.getParityFileName()));
        LOG.info("Scheulded job for parity repair " + encodingStatus);
        activeRepairs++;
      }
    } catch (IOException e) {
      LOG.error(e);
    }
  }

  private void garbageCollect() throws IOException {
    LOG.info("Starting garbage collection");
    LightWeightRequestHandler findHandler = new LightWeightRequestHandler(
        EncodingStatusOperationType.FIND_DELETED) {
      @Override
      public Object performTask() throws PersistanceException, IOException {
        EncodingStatusDataAccess<EncodingStatus> dataAccess = (EncodingStatusDataAccess)
            StorageFactory.getDataAccess(EncodingStatusDataAccess.class);
        return dataAccess.findDeleted(deletionLimit);
      }
    };
    Collection<EncodingStatus> markedAsDeleted = (Collection<EncodingStatus>) findHandler.handle();
    for (EncodingStatus status : markedAsDeleted) {
      LOG.info("Trying to collect " + status);
      try {
        namesystem.deleteWithTransaction(parityFolder + "/" + status.getParityFileName(), false);
        namesystem.removeEncodingStatus(status);
      } catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  private void checkRevoked() throws IOException {
    LOG.info("Checking replication for revocations");
    LightWeightRequestHandler findHandler = new LightWeightRequestHandler(
        EncodingStatusOperationType.FIND_REVOKED) {
      @Override
      public Object performTask() throws PersistanceException, IOException {
        EncodingStatusDataAccess<EncodingStatus> dataAccess = (EncodingStatusDataAccess)
            StorageFactory.getDataAccess(EncodingStatusDataAccess.class);
        return dataAccess.findRevoked();
      }
    };
    Collection<EncodingStatus> markedAsRevoked = (Collection<EncodingStatus>) findHandler.handle();
    for (EncodingStatus status : markedAsRevoked) {
      LOG.info("Checking replication for revoked status: " + status);
      String path = namesystem.getPath(status.getInodeId());
      int replication = namesystem.getFileInfo(path, true).getReplication();
      LocatedBlocks blocks = namesystem.getBlockLocations(path, 0, Long.MAX_VALUE, false, true, true);
      if (checkReplication(blocks, replication)) {
        LOG.info("Revocation successful for " + status);
        namesystem.deleteWithTransaction(parityFolder + "/" + status.getParityFileName(), false);
        namesystem.removeEncodingStatus(path, status);
      }
    }
  }

  private boolean checkReplication(LocatedBlocks blocks, int replication) {
    for (LocatedBlock locatedBlock : blocks.getLocatedBlocks()) {
      if (locatedBlock.getLocations().length != replication) {
        return false;
      }
    }
    return true;
  }

  public boolean isParityFile(String path) {
    Pattern pattern = Pattern.compile(parityFolder + ".*");
    Matcher matcher = pattern.matcher(path);
    if (matcher.matches()) {
      return true;
    }
    return false;
  }

  public static boolean isEnabled() {
    return enabled;
  }
}
