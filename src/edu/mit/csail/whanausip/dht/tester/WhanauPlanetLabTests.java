package edu.mit.csail.whanausip.dht.tester;

import java.security.KeyStore;
import java.util.Hashtable;
import java.util.Random;
import java.util.Set;

import edu.mit.csail.whanausip.commontools.*;
import edu.mit.csail.whanausip.commontools.threads.MethodThreadBatchRun;
import edu.mit.csail.whanausip.dht.WhanauRPCClientStub;

/**
 * Runs tests across the 'mit_whasip' Planetlab slice, 
 * assuming they run instances of WhanauDaemon
 * 
 * Usage:
 * WhanauPlanetLabTests <TESTNAME> <STARTINDEX> <ENDINDEX>
 * 
 * @author ryscheng
 * @date 2010/01/19
 */
public class WhanauPlanetLabTests {
	
	public static Random 	randGen = new Random();
	/**
	 * Due to limits on open files, we address a range of nodes at a time
	 * [startIndex, endIndex] defines which nodes in the key set to call
	 */
	public static int 		startIndex;				
	public static int 		endIndex;
	/**
	 * Limit on the number of threads to spawn
	 */
	public static int 		maxThreads = 1000;
	
	/**
	 * Returns a random value in the hash table
	 * 
	 * @param table Hashtable<String, Object>
	 * @return		Object
	 */
	public static Object getRandomValue(Hashtable<String, Object> table){
		Set<String> keySet = table.keySet();
		String[] keys = keySet.toArray(new String[0]);
		if(keys.length<=0) {
			return null;
		}
		return table.get(keys[WhanauPlanetLabTests.randGen.nextInt(keys.length)]);
	}

	/**
	 * Pretty printer for a hash table
	 * Used for all results from remote calls
	 * Returned in a String
	 * 
	 * @param table Hashtable<String, Object>
	 * @return String
	 */
	public static String printTable(Hashtable<String, Object> table){
		Set<String> keys = table.keySet();
		String result = "PRINTING RESULTS \n";
		for (String key : keys) {
			result += "\\\\\\\\\\"+key+"////////// \n";
			result += table.get(key) + "\n";
			result += "--------------------------- \n";
		}
		result += "TOTAL RESULTS: " + keys.size();
		return result;
	}
	
	/**
	 * Used for most remote methods.
	 * Calls the command with parameters on the nodes in the 
	 * clientList from [startIndex, endIndex]
	 * 
	 * @param clientList	WhanauCommandClientList<String>
	 * @param timeout		int		= timeout for each remote call
	 * @param cmd			String	= command to call
	 * @param param			Object[]= parameters to pass to each
	 * @return Hashtable<String, Object> = results from each call
	 */
	public static Hashtable<String, Object> runRemoteMethod(WhanauCommandClientList<String> clientList, int timeout, String cmd, Object... param) {
		System.out.println("---------------------\n RUNNING "+cmd);
		MethodThreadBatchRun batch = new MethodThreadBatchRun(maxThreads);
		Set<String> keys = clientList.getAllNames();
		int count=0;
		
		for (String key : keys) {
			if (((startIndex <= count) && (count <= endIndex)) || cmd.equals(WhanauDHTConstants.GETPUBKEYHASH_CMD)) {
				batch.addThread(key, clientList, key, cmd, param);
			}
			count++;
		}
		batch.joinTermination(timeout);
		Hashtable<String, Object> table = batch.getFinalResults();
		System.out.println(WhanauPlanetLabTests.printTable(table));
		return table;
	}
	
	/**
	 * Creates a random graph (Erdos-Renyi Model)
	 * Random picks a number of peers for each node
	 * 
	 * @param clientList 		WhanauCommandClientList<String> = client List
	 * @param pubKeyHashTable	Hashtable<String, Object> 		= public key hashes for 
	 * 															all nodes in clientList
	 * @param numPeers			int								= number of peers for each node
	 */
	public static void addPeers(WhanauCommandClientList<String> clientList, 
								Hashtable<String, Object> pubKeyHashTable,  int numPeers) {
		MethodThreadBatchRun batch;
		Set<String> keys;
		Hashtable<String, Object> table;
		Object[] param = new Object[3];
		int count=0;
		
		System.out.println("---------------------\n CREATING SOCIAL LINKS");
		keys = clientList.getAllNames();
		batch = new MethodThreadBatchRun(maxThreads);
		for (String key : keys) {
			if ((startIndex <= count) && (count <= endIndex)) {
				if (pubKeyHashTable.get(key) != null) {
					for (int i=0;i<numPeers;i++) {
						WhanauRPCClientStub<String> local = clientList.getClient(key);
						String peerKey = clientList.getRandomKey();
						WhanauRPCClientStub<String> peer = clientList.getClient(peerKey);
						String pubKeyHash = (String) pubKeyHashTable.get(peerKey);
						if (pubKeyHash != null) {
							//Forward
							param = new Object[3];
							param[0] = pubKeyHash;
							param[1] = peer.getHostname();
							param[2] = peer.getPort();
							batch.addThread(key+" - "+Integer.toString(i)+" - forward", clientList, key, 
											WhanauDHTConstants.ADDPEER_CMD, param);
							//Backward
							pubKeyHash = (String) pubKeyHashTable.get(key);
							param = new Object[3];
							param[0] = pubKeyHash;
							param[1] = local.getHostname();
							param[2] = local.getPort();
							batch.addThread(key+" - "+Integer.toString(i)+" - backward", clientList, peerKey, 
											WhanauDHTConstants.ADDPEER_CMD, param);
						} else {
							i--;
							System.out.println("Error: No public key for "+peerKey);
						}
					}
				} else {
					System.out.println("Error: No public key for "+key);
				}
			}
			count++;
		}
		batch.joinTermination(WhanauDHTConstants.SMALLCALL_TIMEOUT);
		table = batch.getFinalResults();
		System.out.println(WhanauPlanetLabTests.printTable(table));
	}
	
	/**
	 * Performs a lookup for each node
	 * If target is null, selects random keys to lookup
	 * Otherwise, all look for the target key 
	 * 
	 * @param clientList		WhanauCommandClientList<String> = client List
	 * @param pubKeyHashTable	Hashtable<String, Object> 		= public key hashes for 
	 * 															all nodes in clientList
	 * @param lookupTimeout		int								= timeout for lookup call
	 * @param lookupTryTimeout	int								= timeout for lookupTry call
	 * @param queryTimeout		int								= timeout for query call
	 * @param r					int								= number of lookupTry threads per lookup
	 * @param w					int								= number of steps in random walk
	 * @param target			String							= target key
	 * @return
	 */
	public static Hashtable<String, Object> lookupTest(WhanauCommandClientList<String> clientList, 
									Hashtable<String, Object> pubKeyHashTable, int lookupTimeout, 
									int lookupTryTimeout, int queryTimeout, int r, int w, String target) {
		
		//Perform Lookups
		System.out.println("---------------------\n PERFORMING LOOKUPS");
		Set<String> keys = clientList.getAllNames();
		MethodThreadBatchRun batch = new MethodThreadBatchRun(maxThreads);
		Object[] param;
		int count=0;
		String toLookup;
		
		for (String key : keys) {
			if ((startIndex <= count) && (count <= endIndex)) {
				if (target == null)	toLookup = (String) WhanauPlanetLabTests.getRandomValue(pubKeyHashTable);
				else toLookup = target;
				//if (pubKeyHashTable.containsKey(key)) {
					param = new Object[5];
					param[0] = lookupTryTimeout;
					param[1] = queryTimeout;
					param[2] = r;
					param[3] = w;
					param[4] = toLookup;
					batch.addThread(key+".lookup("+toLookup+")", clientList, key, WhanauDHTConstants.LOOKUP_CMD, param);
				//} else {
					//System.out.println(key+" is unresponsive");
				//}
			}
			count++;
		}
		batch.joinTermination(lookupTimeout);
		Hashtable<String, Object> table = batch.getFinalResults();
		System.out.println(WhanauPlanetLabTests.printTable(table));
		return table;
	}
	
	/**
	 * Checks for which test and runs it
	 * 
	 * @param args String[]
	 * 	args[0] = test name
	 *  args[1] = start index
	 *  args[2] = end index
	 */
	public static void main(String[] args) {
		Runtime.getRuntime().gc();
		// TODO Auto-generated method stub
		//int whanauPort = 9000;
		//int numPorts = 10;
		int whanauPort = 9009;
		int numPorts = 1;
		int numPeers = 5;
		int w = 7;
		int r = 50;
		int rl = 60;
		String targetKeyFile = "lib/keys/1.jks";
		String targetPubKeyHash;
		String sybilKeyFile = "lib/keys/1.jks";
		String sybilPubKeyHash;
		String sybilHost = "18.26.4.169";
		int sybilPort = 9009;
		int lookupTimeout = 30000;
		int lookupTryTimeout = 5000;
		int queryTimeout = 4000;
		String nodeListFile = "lib/nodes.txt";
		String adminKeysPath = "lib/keys/0.jks";
		String password = WhanauDHTConstants.DEFAULT_PASSWORD;
		//Check for enough arguments
		if (args.length < 1) {
			System.out.println("Usage: \n WhanauPlanetLabTests <testname> <startIndex> <endIndex>");
			return;
		}
		
		try {
			targetPubKeyHash = CryptoTool.getPublicKeyHashFromFile(targetKeyFile, password);
			sybilPubKeyHash = CryptoTool.getPublicKeyHashFromFile(sybilKeyFile, password);
			WhanauPlanetLabTests.startIndex = Integer.parseInt(args[1]);
			WhanauPlanetLabTests.endIndex = Integer.parseInt(args[2]);
			KeyStore keyStore = CryptoTool.loadKeyStore(adminKeysPath, password);
			WhanauCommandClientList<String> clientList = new WhanauCommandClientList<String>(keyStore, password, nodeListFile, whanauPort, numPorts);
			Set<String> keys = clientList.getAllNames();
			System.out.println("ALL NODES:");
			for (String key : keys) {
				System.out.println(key);
			}
			
			if (args[0].equals("GETSTATESTR")) {
				//Get State and Finish Up
				WhanauPlanetLabTests.runRemoteMethod(clientList, WhanauDHTConstants.SMALLCALL_TIMEOUT, WhanauDHTConstants.GETSTATESTR_CMD);
			} else if (args[0].equals("GETLOGSTR")) {
				WhanauPlanetLabTests.runRemoteMethod(clientList, WhanauDHTConstants.SMALLCALL_TIMEOUT, WhanauDHTConstants.GETLOGSTR_CMD);
			} else if (args[0].equals("ADDPEER")) {
				//Get Public Key Hashes
				Hashtable<String, Object> pubKeyHashTable = WhanauPlanetLabTests.runRemoteMethod(clientList, WhanauDHTConstants.SMALLCALL_TIMEOUT, WhanauDHTConstants.GETPUBKEYHASH_CMD);
				//Create Graph (addPeer)
				WhanauPlanetLabTests.addPeers(clientList, pubKeyHashTable, numPeers);
			} else if (args[0].equals("REMOVEALLPEERS")) {
				WhanauPlanetLabTests.runRemoteMethod(clientList, WhanauDHTConstants.SMALLCALL_TIMEOUT, WhanauDHTConstants.REMOVEALLPEERS_CMD);
			} else if (args[0].equals("SETUPPART1")) {
				WhanauPlanetLabTests.runRemoteMethod(clientList, WhanauDHTConstants.SMALLCALL_TIMEOUT, WhanauDHTConstants.RUNSETUPPART1THREAD_CMD, w, r, r, r);
			} else if (args[0].equals("SETUPPART2")) {
				WhanauPlanetLabTests.runRemoteMethod(clientList, WhanauDHTConstants.SMALLCALL_TIMEOUT, WhanauDHTConstants.RUNSETUPPART2THREAD_CMD, w, r, r, r);
			} else if (args[0].equals("GETSETUPTHREADRESULT")) {
				Hashtable<String, Object> table = WhanauPlanetLabTests.runRemoteMethod(clientList, WhanauDHTConstants.SMALLCALL_TIMEOUT, WhanauDHTConstants.GETSETUPTHREADRESULT_CMD);
				long total=0;
				long count=0;
				for (String key : table.keySet()) {
					if ((table.get(key) instanceof Long) && ((Long)table.get(key) != 0)) {
						total += (Long) table.get(key);
						count++;
					}
				}
				System.out.println("Average Setup Time = " + (total / count) + " ms");
			} else if (args[0].equals("LOOKUP")){
				//Hashtable<String, Object> pubKeyHashTable = WhanauPlanetLabTests.runRemoteMethod(clientList, WhanauDHTConstants.SMALLCALL_TIMEOUT, WhanauDHTConstants.GETPUBKEYHASH_CMD);
				//WhanauPlanetLabTests.lookupTest(clientList, pubKeyHashTable, lookupTimeout, lookupTryTimeout, queryTimeout, rl, w, null);
				WhanauPlanetLabTests.lookupTest(clientList, null, lookupTimeout, lookupTryTimeout, queryTimeout, rl, w, targetPubKeyHash);
			} else if (args[0].equals("ADDSYBIL")) {
				WhanauPlanetLabTests.runRemoteMethod(clientList, WhanauDHTConstants.SMALLCALL_TIMEOUT, WhanauDHTConstants.ADDPEER_CMD, sybilPubKeyHash, sybilHost, sybilPort);
			}
			
			System.out.println("DONE");
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

}
