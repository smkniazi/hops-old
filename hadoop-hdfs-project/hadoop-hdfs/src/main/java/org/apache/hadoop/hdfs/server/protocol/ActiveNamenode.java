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
package org.apache.hadoop.hdfs.server.protocol;

import java.net.InetSocketAddress;
import org.apache.hadoop.net.NetUtils;

/**
 *
 * @author jdowling
 */
public class ActiveNamenode implements Comparable<ActiveNamenode> {

    private final long id;
    private final String hostname;
    private final String ipAddress;
    private final int port;
    
    public ActiveNamenode(long id, String hostname, String ipAddress, int port) {
        this.id = id;
        this.hostname = hostname;
        this.ipAddress = ipAddress;
        this.port = port;
    }

    public String getHostname() {
        return hostname;
    }

    public long getId() {
        return id;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }
    
    public InetSocketAddress getInetSocketAddress(){
//        return new InetSocketAddress(ipAddress, port);
        return NetUtils.createSocketAddrForHost(ipAddress, port);
    }

    @Override
    public boolean equals(Object obj) {
        // objects are equal if the belong to same NN
        // namenode id is not taken in to account
        // sometimes the id of the namenode may change even without 
        //namenode restart
        if(!(obj instanceof  ActiveNamenode))
        { return false; }
        ActiveNamenode that = (ActiveNamenode)obj;
        if(this.getInetSocketAddress().equals(that.getInetSocketAddress())){
            return true;
        }else{
            return false;
        }
    }

  @Override
  public int compareTo(ActiveNamenode o) {

    if (id < o.getId()) {
      return -1;
    } else if (id == o.getId()) {
      return 0;
    } else if (id > o.getId()) {
      return 1;
    } else {
      throw new IllegalStateException("I write horrible code");
    }
  }

  @Override
  public String toString() {
    return "Active NN ("+id+") address "+getInetSocketAddress();
  }   
}