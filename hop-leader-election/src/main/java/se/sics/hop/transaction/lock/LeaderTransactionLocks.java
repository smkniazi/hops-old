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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Mahmoud Ismail <maism@sics.se>
 * @author Steffen Grohsschmiedt <steffeng@sics.se>
 */
public class LeaderTransactionLocks implements TransactionLocks {
 
  private final Map<HopsLock.Type, HopsLock> locks;
  
  public LeaderTransactionLocks(){
    this.locks = new EnumMap<HopsLock.Type, HopsLock>(HopsLock.Type.class);
  }
  
  @Override
  public TransactionLocks add(HopsLock lock){
    if(locks.containsKey(lock.getType()))
      throw new IllegalArgumentException("The same lock cannot be added " +
          "twice!");
    
    locks.put(lock.getType(), lock);
    return this;
  }

   @Override
  public TransactionLocks add(Collection<HopsLock> locks) {
    for(HopsLock lock : locks){
      add(lock);
    }
    return this;
  }
   
  @Override
  public boolean containsLock(HopsLock.Type lock) {
    return locks.containsKey(lock);
  }
  
  @Override
  public HopsLock getLock(HopsLock.Type type) throws LockNotAddedException{
    if (!locks.containsKey(type)) {
      throw new LockNotAddedException("Trying to get a lock which was not " +
          "added.");
    }
    return locks.get(type);
  }
    
  public List<HopsLock> getSortedLocks(){
    List<HopsLock> lks = new ArrayList<HopsLock>(locks.values());
    Collections.sort(lks);
    return lks;
  }

 
}
