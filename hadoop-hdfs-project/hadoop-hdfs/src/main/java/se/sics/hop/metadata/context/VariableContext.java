package se.sics.hop.metadata.context;

import se.sics.hop.metadata.hdfs.entity.EntityContext;
import java.util.Collection;
import java.util.EnumMap;
import se.sics.hop.metadata.hdfs.entity.CounterType;
import se.sics.hop.metadata.hdfs.entity.FinderType;
import se.sics.hop.exception.PersistanceException;
import se.sics.hop.metadata.hdfs.entity.hop.var.HopVariable;
import se.sics.hop.metadata.hdfs.dal.VariableDataAccess;
import se.sics.hop.exception.LockUpgradeException;
import se.sics.hop.exception.StorageException;
import se.sics.hop.metadata.hdfs.entity.EntityContextStat;
import se.sics.hop.metadata.hdfs.entity.TransactionContextMaintenanceCmds;
import se.sics.hop.transaction.lock.HopsLock;
import se.sics.hop.transaction.lock.HopsVariablesLock;
import se.sics.hop.transaction.lock.TransactionLockTypes;
import se.sics.hop.transaction.lock.TransactionLocks;

/**
 *
 * @author Mahmoud Ismail <maism@sics.se>
 */
public class VariableContext extends EntityContext<HopVariable> {

  private EnumMap<HopVariable.Finder, HopVariable> variables = new EnumMap<HopVariable.Finder, HopVariable>(HopVariable.Finder.class);
  private EnumMap<HopVariable.Finder, HopVariable> modifiedVariables = new EnumMap<HopVariable.Finder, HopVariable>(HopVariable.Finder.class);
  private EnumMap<HopVariable.Finder, HopVariable> newVariables = new EnumMap<HopVariable.Finder, HopVariable>(HopVariable.Finder.class);

  private VariableDataAccess<HopVariable, HopVariable.Finder> da;

  public VariableContext(VariableDataAccess<HopVariable, HopVariable.Finder>  da) {
    this.da = da;
  }

  @Override
  public void add(HopVariable entity) throws PersistanceException {
    newVariables.put(entity.getType(), entity);
    variables.put(entity.getType(), entity);
  }

  @Override
  public void clear() {
    storageCallPrevented = false;
    variables.clear();
    modifiedVariables.clear();
  }

  @Override
  public int count(CounterType<HopVariable> counter, Object... params) throws PersistanceException {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public HopVariable find(FinderType<HopVariable> finder, Object... params) throws PersistanceException {
    HopVariable.Finder varType = (HopVariable.Finder) finder;
    HopVariable var = null;
    if (variables.containsKey(varType)) {
      log("find-" + varType.toString(), CacheHitState.HIT);
      var = variables.get(varType);
    } else {
      log("find-" + varType.toString(), CacheHitState.LOSS);
      aboutToAccessStorage();
      var = da.getVariable(varType);
      variables.put(varType, var);
    }
    return var;
  }

  @Override
  public Collection<HopVariable> findList(FinderType<HopVariable> finder, Object... params) throws PersistanceException {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void prepare(TransactionLocks lks) throws StorageException {
    HopsVariablesLock hlk = (HopsVariablesLock) lks.getLock(HopsLock.Type.Variable);
    checkLockUpgrade(hlk, modifiedVariables);
    checkLockUpgrade(hlk, newVariables);
    da.prepare(newVariables.values(), modifiedVariables.values(), null);
  }

  private void checkLockUpgrade(HopsVariablesLock hlk, EnumMap<HopVariable.Finder, HopVariable> varmap) throws LockUpgradeException {
    for(HopVariable.Finder varType : varmap.keySet()){
      if(!hlk.getVariableLockType(varType).equals(TransactionLockTypes.LockType.WRITE)){
        throw new LockUpgradeException(varType.toString());
      }
    }
  }
  
  @Override
  public void remove(HopVariable var) throws PersistanceException {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void removeAll() throws PersistanceException {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void update(HopVariable var) throws PersistanceException {
    modifiedVariables.put(var.getType(), var);
    variables.put(var.getType(), var);
    log(
            "updated-" + var.getType().toString(),
            CacheHitState.NA,
            new String[]{"value", var.toString()});
  }
  
  @Override
  public EntityContextStat collectSnapshotStat() throws PersistanceException {
    EntityContextStat stat = new EntityContextStat("Variables",newVariables.size(),modifiedVariables.size(),0);
    StringBuilder additionalInfo  = new StringBuilder();
    for(HopVariable variable : newVariables.values()){
      additionalInfo.append("[New: "+variable.getType()+"] ");
    }
    for(HopVariable variable : modifiedVariables.values()){
      additionalInfo.append("[Mod: "+variable.getType()+"] ");
    }
    stat.setAdditionalInfo(additionalInfo.toString());
    return stat;
  }

  @Override
  public void snapshotMaintenance(TransactionContextMaintenanceCmds cmds, Object... params) throws PersistanceException {
    
  }
}
