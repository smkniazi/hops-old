package org.apache.hadoop.hdfs;

import com.google.common.collect.Sets;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.protocol.ClientProtocol;
import org.apache.hadoop.hdfs.server.namenode.NameNode;
import org.apache.hadoop.ipc.RPC;
import se.sics.hop.leaderElection.node.ActiveNode;
import se.sics.hop.leaderElection.node.ActiveNodePBImpl;
import se.sics.hop.leaderElection.node.SortedActiveNodeList;

/**
 * **************************************************************
 * This interface is a policy for selecting the appropriate NameNodes (either in
 * round robin or random style or any other style depending on implementation)
 * to perform hdfs read/write operations
 *
 * In case we are not able to load the class provided in the configuration, we
 * load default policy (which is Random selection)
 *
 ****************************************************************
 */
public class NamenodeSelector extends Thread {

    enum NNSelectionPolicy {

        RANDOM("RANDOM"),
        ROUND_ROBIN("ROUND_ROBIN");
        private String description = null;

        private NNSelectionPolicy(String arg) {
            this.description = arg;
        }

        @Override
        public String toString() {
            return description;
        }
    }

    public static class NamenodeHandle {

        final private ClientProtocol namenodeRPCHandle;
        final private ActiveNode namenode;

        NamenodeHandle(ClientProtocol proto, ActiveNode an) {
            this.namenode = an;
            this.namenodeRPCHandle = proto;
        }

        public ClientProtocol getRPCHandle() {
            return this.namenodeRPCHandle;
        }

        public ActiveNode getNamenode() {
            return this.namenode;
        }

        @Override
        public String toString() {
            return "[RPC handle connected to " + namenode.getInetSocketAddress() + "] ";
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof NamenodeSelector.NamenodeHandle)) {
                return false;
            } else {
                NamenodeSelector.NamenodeHandle that = (NamenodeSelector.NamenodeHandle) obj;
                boolean res = this.namenode.equals(that.getNamenode());
                return res;
            }
        }
        
        
    };
    /* List of name nodes */
    private List<NamenodeSelector.NamenodeHandle> nnList = new CopyOnWriteArrayList<NamenodeSelector.NamenodeHandle>();
    private List<NamenodeSelector.NamenodeHandle> blackListedNamenodes = new CopyOnWriteArrayList<NamenodeSelector.NamenodeHandle>();
    private static Log LOG = LogFactory.getLog(NamenodeSelector.class);
    private final URI defaultUri;
    private final NamenodeSelector.NNSelectionPolicy policy;
    private final Configuration conf;
    private boolean periodicNNListUpdate = true;
    private final Object wiatObjectForUpdate = new Object();
    private final int namenodeListUpdateTimePeriod;
    Random rand = new Random();
    

    //only for testing
    NamenodeSelector(Configuration conf, ClientProtocol namenode) throws IOException {
        this.defaultUri = null;
        ActiveNode dummyActiveNamenode = new ActiveNodePBImpl(1,"localhost","127.0.0.1",9999, "0.0.0.0:50070");
        this.nnList.add(new NamenodeSelector.NamenodeHandle(namenode, dummyActiveNamenode));
        this.conf = conf;
        this.policy = NamenodeSelector.NNSelectionPolicy.ROUND_ROBIN;
        this.namenodeListUpdateTimePeriod = -1;
    }

    NamenodeSelector(Configuration conf, URI defaultUri) throws IOException {
        this.defaultUri = defaultUri;
        this.conf = conf;
        rand.setSeed(System.currentTimeMillis());
        
        namenodeListUpdateTimePeriod = conf.getInt(DFSConfigKeys.DFS_CLIENT_REFRESH_NAMENODE_LIST_IN_MS_KEY, DFSConfigKeys.DFS_CLIENT_REFRESH_NAMENODE_LIST_IN_MS_DEFAULT);

        // Getting appropriate policy
        // supported policies are 'RANDOM' and 'ROUND_ROBIN'
        String policyName = conf.get(DFSConfigKeys.DFS_NAMENODE_SELECTOR_POLICY_KEY, "ROUND_ROBIN");
        if (policyName == NamenodeSelector.NNSelectionPolicy.RANDOM.toString()) {
            policy = NamenodeSelector.NNSelectionPolicy.RANDOM;
        } else if (policyName == NamenodeSelector.NNSelectionPolicy.ROUND_ROBIN.toString()) {
            policy = NamenodeSelector.NNSelectionPolicy.ROUND_ROBIN;
        } else {
            policy = NamenodeSelector.NNSelectionPolicy.ROUND_ROBIN;
        }
        LOG.debug("Client's namenode selection policy is "+policy);

        //get the list of Namenodes
        createNamenodeClinetsFromList();

        //start periodic Namenode list update thread.
        start();
    }

    @Override
    public void run() {
        while (periodicNNListUpdate) {
            try {
                //first sleep and then update
                synchronized (wiatObjectForUpdate) {
                    wiatObjectForUpdate.wait(namenodeListUpdateTimePeriod);
                }
                if (periodicNNListUpdate) {
                    periodicNamenodeClientsUpdate();
                }
            } catch (Exception ex) {
                LOG.warn(ex);
                //ex.printStackTrace();
            }
        }
        LOG.debug("Shuting down client");
    }
    
    private void asyncNNListUpdate(){
        synchronized (wiatObjectForUpdate) {
            wiatObjectForUpdate.notify();
        }
    }

    public synchronized void close(){
        stopPeriodicUpdates();

        //close all clients
        for (NamenodeSelector.NamenodeHandle namenode : nnList) {
            ClientProtocol rpc = namenode.getRPCHandle();
            nnList.remove(namenode);
            RPC.stopProxy(rpc);
        }
    }

    public void stopPeriodicUpdates() {
        periodicNNListUpdate = false;
        synchronized (wiatObjectForUpdate) {
            wiatObjectForUpdate.notify();
        }
    }
    
  public List<NamenodeSelector.NamenodeHandle> getAllNameNode() throws IOException {
    if (nnList == null || nnList.isEmpty()) {
      asyncNNListUpdate();
      throw new NoAliveNamenodeException();
    }
    return nnList;
  }

  public NamenodeHandle getLeadingNameNode() throws IOException {
    // The first one is supposed to be the leader. An exception is thrown if none is available.
    return getAllNameNode().get(0);
  }
       
    /**
     * Gets the appropriate namenode for a read/write operation
     *
     * @return DFSClient
     */
    int rrIndex = 0;

    public NamenodeSelector.NamenodeHandle getNextNamenode() throws IOException {
      
      
                
        if (nnList == null || nnList.isEmpty()) {
            asyncNNListUpdate();
            throw new NoAliveNamenodeException("Every thing is null why");
        }

        NamenodeSelector.NamenodeHandle handle = getNextNNBasedOnPolicy();
        if (handle == null || handle.getRPCHandle() == null) {
            //update the list right now 
            asyncNNListUpdate();
            throw new NoAliveNamenodeException(" Started an asynchronous update of the namenode list. ");
        }
        //LOG.debug("Returning " + handle + " for next RPC call. RRIndex " + index);
        //LOG.debug(printNamenodes());
        return handle;
    }

    private synchronized NamenodeSelector.NamenodeHandle getNextNNBasedOnPolicy() {
        if (policy == NamenodeSelector.NNSelectionPolicy.RANDOM) {
            for (int i = 0; i < 10; i++) {
                int index = rand.nextInt(nnList.size());
                NamenodeSelector.NamenodeHandle handle = nnList.get(index);
                if (!this.blackListedNamenodes.contains(handle)) {
                    return handle;
                }
            }
            return null;
        } else if (policy == NamenodeSelector.NNSelectionPolicy.ROUND_ROBIN) {
            for (int i = 0; i < nnList.size()+1; i++) {
                rrIndex = (++rrIndex) % nnList.size();
                NamenodeSelector.NamenodeHandle handle = nnList.get(rrIndex);
                if (!this.blackListedNamenodes.contains(handle)) {
                    //LOG.debug("rrIndex is "+rrIndex+" namenodes are "+printNamenodes());
                    return handle;
                }
            }
            return null;
        } else {
            throw new UnsupportedOperationException("Namenode selection policy is not supported. Selected policy is " + policy);
        }
    }

    String printNamenodes() {
        String nns = "Client is connected to namenodes: ";
        for (NamenodeSelector.NamenodeHandle namenode : nnList) {
            nns += namenode + ", ";
        }

        nns += " Black Listed Nodes are ";
        for (NamenodeSelector.NamenodeHandle namenode : this.blackListedNamenodes) {
            nns += namenode + ", ";
        }
        return nns;
    }

    public int getTotalConnectedNamenodes() {
        return nnList.size();
    }

    /**
     * try connecting to the default uri and get the list of NN from there if it
     * fails then read the list of NNs from the config file and connect to them
     * for list of Namenodes in the syste.
     */
    private void createNamenodeClinetsFromList() throws IOException {
        SortedActiveNodeList anl = null;
        ClientProtocol handle = null;
        if (defaultUri != null) {
            try {
                NameNodeProxies.ProxyAndInfo<ClientProtocol> proxyInfo = NameNodeProxies.createProxy(conf, defaultUri, ClientProtocol.class);
                handle = proxyInfo.getProxy();
                if (handle != null) {
                    anl = handle.getActiveNamenodesForClient();
                    RPC.stopProxy(handle);
                }
            } catch (Exception e) {
                LOG.warn("Failed to get list of NN from default NN. Default NN was " + defaultUri);
                RPC.stopProxy(handle);
            }
        }

        if (anl == null) { // default failed, now try the list of NNs from the config file
            String[] namenodes = getNamenodesFromConfigFile(conf);
            LOG.debug("Trying to the list of NN from the config file  " + Arrays.toString(namenodes));
            if (namenodes.length > 0) {
                for (int i = 0; i < namenodes.length; i++) {
                    try {
                        LOG.debug("Trying to connect to  " + namenodes[i]);
                        URI uri = new URI(namenodes[i]);
                        NameNodeProxies.ProxyAndInfo<ClientProtocol> proxyInfo = NameNodeProxies.createProxy(conf, uri, ClientProtocol.class);
                        handle = proxyInfo.getProxy();
                        if (handle != null) {
                            anl = handle.getActiveNamenodesForClient();
                            RPC.stopProxy(handle);
                        }
                        if (anl != null && !anl.getActiveNodes().isEmpty()) {
                            
                            break; // we got the list
                        }
                    } catch (Exception e) {
                        LOG.error(e);
                        e.printStackTrace();
                        if(handle!=null){
                            RPC.stopProxy(handle);
                        }
                    }
                }
            }
        }
        if (anl != null) {
            LOG.debug("Refreshing the Namenode handles");
            refreshNamenodeList(anl);
        }else{
            LOG.debug("No new namenodes were found");   
        }
    }

    /**
     * we already have a list of NNs in 'clinets' try contacting these namenodes
     * to get a fresh list of namenodes if all of the namenodes in the 'clients'
     * map fail then call the 'createDFSClientsForFirstTime' function. with will
     * try to connect to defaults namenode provided at the initialization phase.
     */
    private synchronized void periodicNamenodeClientsUpdate() throws IOException {
        SortedActiveNodeList anl = null;
        if (!nnList.isEmpty()) {
            for (NamenodeSelector.NamenodeHandle namenode : nnList) { //TODO dont try with black listed nodes
                try {
                    ClientProtocol handle = namenode.getRPCHandle();
                    anl = handle.getActiveNamenodesForClient();
                    if (anl == null || anl.size() == 0) {
                        anl = null;
                        continue;
                    } else {
                        // we got a fresh list of anl
                        refreshNamenodeList(anl);
                        return;
                    }
                } catch (IOException e) {
                    continue;
                }
            }
        }

        if (anl == null) { // try contacting default NNs
            createNamenodeClinetsFromList();
        }
    }

    private String[] getNamenodesFromConfigFile(Configuration conf) {
        String namenodes = conf.get(DFSConfigKeys.DFS_NAMENODES_RPC_ADDRESS_KEY);
        if (namenodes == null || namenodes.length() == 0) {
            return new String[0];
        }
        return namenodes.split(",");
    }

    private synchronized void refreshNamenodeList(SortedActiveNodeList anl) {
        if (anl == null) {
            return;
        }
        //NOTE should not restart a valid client

        //find out which client to start and stop  
        //make sets objects of old and new lists
        Set<InetSocketAddress> oldClients = Sets.newHashSet();
        for (NamenodeSelector.NamenodeHandle namenode : nnList) {
            ActiveNode ann = namenode.getNamenode();
            oldClients.add(ann.getInetSocketAddress());
        }

        //new list
        Set<InetSocketAddress> updatedClients = Sets.newHashSet();
        for (ActiveNode ann : anl.getActiveNodes()) {
            updatedClients.add(ann.getInetSocketAddress());
        }

        //START_HOP_CODE
        Sets.SetView<InetSocketAddress> deadNNs = Sets.difference(oldClients, updatedClients);
        Sets.SetView<InetSocketAddress> newNNs = Sets.difference(updatedClients, oldClients);

        // stop the dead threads 
        if (deadNNs.size() != 0) {
            for (InetSocketAddress deadNNAddr : deadNNs) {
                removeDFSClient(deadNNAddr);
            }
        }

        // start threads for new NNs
        if (newNNs.size() != 0) {
            for (InetSocketAddress newNNAddr : newNNs) {
                addDFSClient(newNNAddr, anl.getActiveNode(newNNAddr));
            }
        }

        //clear black listed nodes
        this.blackListedNamenodes.clear();
        //LOG.debug(printNamenodes());
    }

    //thse are synchronized using external methods
    private void removeDFSClient(InetSocketAddress address) {
        NamenodeSelector.NamenodeHandle handle = getNamenodeHandle(address);
        if (handle != null) {
            if (nnList.remove(handle)) {
                RPC.stopProxy(handle.getRPCHandle());
            } else {
                LOG.warn("Failed to Remove RPC proxy for " + address);
            }
        }
    }
    //thse are synchronized using external methods

    private void addDFSClient(InetSocketAddress address, ActiveNode ann) {
        if (address == null || ann == null) {
            LOG.warn("Unable to add proxy for namenode. ");
            return;
        }
        try {
            URI uri = NameNode.getUri(address);
            NameNodeProxies.ProxyAndInfo<ClientProtocol> proxyInfo = NameNodeProxies.createProxy(conf, uri, ClientProtocol.class);
            ClientProtocol handle = proxyInfo.getProxy();
            nnList.add(new NamenodeSelector.NamenodeHandle(handle, ann));
        } catch (IOException e) {
            LOG.warn("Unable to Start RPC proxy for " + address);
        }
    }

    private boolean pingNamenode(ClientProtocol namenode) {
        try {
            namenode.ping();
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private NamenodeSelector.NamenodeHandle getNamenodeHandle(InetSocketAddress address) {
        for (NamenodeSelector.NamenodeHandle handle : nnList) {
            if (handle.getNamenode().getInetSocketAddress().equals(address)) {
                return handle;
            }
        }
        return null;
    }

    public void blackListNamenode(NamenodeSelector.NamenodeHandle handle) {
        if (!this.blackListedNamenodes.contains(handle)) {
            this.blackListedNamenodes.add(handle);
        }

        //LOG.debug(printNamenodes());

        //if a bad namenode is detected then update the list of Namenodes in the system
        synchronized (wiatObjectForUpdate) {
            wiatObjectForUpdate.notify();
        }
    }
    
    public static void main(String []args){
        List<String> list = new CopyOnWriteArrayList<String>();
        String str1 = new String("String1");
        list.add(str1);
        String retVal1 = list.get(0);
        list.remove(str1);
        list.clear();
        
        
        System.out.println(retVal1);
    }
}
