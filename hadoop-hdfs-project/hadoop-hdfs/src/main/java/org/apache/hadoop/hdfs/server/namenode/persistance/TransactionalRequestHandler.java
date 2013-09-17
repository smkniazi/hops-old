package org.apache.hadoop.hdfs.server.namenode.persistance;

import java.io.IOException;
import org.apache.hadoop.hdfs.server.namenode.FSNamesystem;
import org.apache.hadoop.hdfs.server.namenode.Namesystem;
import org.apache.hadoop.hdfs.server.namenode.lock.TransactionLockManager;
import org.apache.hadoop.hdfs.server.namenode.persistance.context.TransactionContextException;
import org.apache.hadoop.hdfs.server.namenode.persistance.context.TransactionLockAcquireFailure;
import org.apache.hadoop.hdfs.server.namenode.persistance.storage.StorageException;
import org.apache.log4j.NDC;

/**
 *
 * @author kamal hakimzadeh<kamal@sics.se>
 */
public abstract class TransactionalRequestHandler extends RequestHandler {
    
    public TransactionalRequestHandler(OperationType opType) {
        super(opType);
    }

    @Override
    protected Object run(boolean writeLock, boolean readLock, Namesystem namesystem) throws IOException {
        boolean systemLevelLock = FSNamesystem.isSystemLevelLockEnabled();
        boolean rowLevelLock = FSNamesystem.rowLevelLock();
        if (systemLevelLock) {
            if (writeLock) {
                namesystem.writeLock();
            }
            if (readLock) {
                namesystem.readLock();
            }
        }
        boolean retry = true;
        boolean rollback = false;
        int tryCount = 0;
        IOException exception = null;
        long txStartTime = 0;
        TransactionLockManager locks = null;

        try {
            while (retry && tryCount < RETRY_COUNT) {
                retry = true;
                rollback = false;
                tryCount++;
                exception = null;

                long oldTime = 0;
                try {
                    // Defines a context for every operation to track them in the logs easily.
                    if (namesystem != null && namesystem instanceof FSNamesystem) {
                        NDC.push("NN (" + namesystem.getNamenodeId() + ") " + opType.name());
                    } else {
                        NDC.push(opType.name());
                    }
                    setUp();
                    txStartTime = System.currentTimeMillis();
                    oldTime = 0;
                    EntityManager.begin();
                    log.debug("tx started");
                    oldTime = System.currentTimeMillis();
                    if (rowLevelLock) {
                        locks = (TransactionLockManager)acquireLock();
                        log.debug("all locks acquired  in " + (System.currentTimeMillis() - oldTime) + " msec");
                        oldTime = System.currentTimeMillis();
                        EntityManager.preventStorageCall();
                    }
                    log.debug("starting in memory processing");
                    Object obj = performTask();
                    log.debug("in memory processig finished  in " + (System.currentTimeMillis() - oldTime) + " msec");
                    oldTime = System.currentTimeMillis();
                    return obj;
                } catch (TransactionContextException ex) {
                    log.error("Could not perfortm task", ex);
                    rollback = true;
                    retry = false;
                } catch(TransactionLockAcquireFailure ex){
                  //Failed to acquire locks. aborting the tx
                  log.error("Failed to acquire the locks. Abort "+ex);
                  rollback = true;
                  retry = false;  // no need to retry as it will fail again
                } catch (PersistanceException ex) {
                    log.error("Tx FAILED. total tx time "+
                            (System.currentTimeMillis() - txStartTime)+
                            " msec. Retry("+
                            retry+") TotalRetryCount("+
                            RETRY_COUNT+") RemainingRetries("+
                            (RETRY_COUNT-tryCount)+")", ex);
                    rollback = true;
                    retry = true;
                } catch (IOException ex) {
                    exception = ex;
                } finally {
                    try {
                        if (!rollback) {
                            EntityManager.commit(locks);
                            log.debug("tx committed. commit took " + (System.currentTimeMillis() - oldTime) + " msec");
                            log.debug("Total time for tx is " + (System.currentTimeMillis() - txStartTime) + " msec");
                        }
                    } catch (StorageException ex) {
                        log.error("Could not commit transaction", ex);
                        rollback = true;
                        retry = true;
                    } finally {
                        try {
                            if (rollback) {
                                try {
                                    EntityManager.rollback();
                                } catch (StorageException ex) {
                                    log.error("Could not rollback transaction", ex);
                                }
                            }
                            if (tryCount == RETRY_COUNT && exception != null) {
                                throw exception;
                            }
                        } finally {
                            NDC.pop();
                            if (namesystem != null && namesystem instanceof FSNamesystem) {
                              ((FSNamesystem)namesystem).performPendingSafeModeOperation();
                            }
                        }
                    }
                }
            }
        } finally {
            if (systemLevelLock) {
                if (writeLock) {
                    namesystem.writeUnlock();
                }
                if (readLock) {
                    namesystem.readUnlock();
                }
            }
        }
        return null;
    }

    public abstract Object acquireLock() throws PersistanceException, IOException;

    @Override
    public TransactionalRequestHandler setParams(Object... params) {
        this.params = params;
        return this;
    }
    
    public void setUp() throws PersistanceException, IOException
    {
      // Do nothing.
      // This can be overriden.
    }
}
