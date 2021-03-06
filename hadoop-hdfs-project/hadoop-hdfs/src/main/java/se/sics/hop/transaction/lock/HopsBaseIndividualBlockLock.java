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
import java.util.List;
import org.apache.hadoop.hdfs.server.blockmanagement.BlockInfo;

/**
 *
 * @author Mahmoud Ismail <maism@sics.se>
 */
abstract class HopsBaseIndividualBlockLock extends HopsLock {

  protected final List<BlockInfo> blocks;

  HopsBaseIndividualBlockLock() {
    this.blocks = new ArrayList<BlockInfo>();
  }

  Collection<BlockInfo> getBlocks() {
    return blocks;
  }

  @Override
  protected HopsLock.Type getType() {
    return HopsLock.Type.Block;
  }
}
