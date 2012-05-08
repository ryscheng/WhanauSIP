package edu.mit.csail.whanausip.dht.tester;

import java.util.HashSet;
import java.util.logging.Logger;

import edu.mit.csail.whanausip.commontools.*;
import edu.mit.csail.whanausip.commontools.remote.WhanauRefControl;
import edu.mit.csail.whanausip.dht.*;
import edu.mit.csail.whanausip.dht.kvchecker.KeyValueChecker;
import edu.mit.csail.whanausip.dht.kvchecker.SigningKVChecker;

/**
 * Single local tester for Whanau DHT.
 * Initializes nodes on different ports, setup random graph,
 * run setup, perform lookups
 * 
 * @author ryscheng
 * @date 2009/02/28
 */
public class WhanauLocalTester<T> {

	public static void main(String[] args) {
		int numLookups = 10;
		int numNodes = 20;
		int numPeers = 3;
		int k = WhanauDHTConstants.NUMLAYERS;
		int w = WhanauDHTConstants.W;
		int r = WhanauDHTConstants.RD/5;
		int rl = 10;
		int peerNum, peerNum2;
		long startTime, endTime;
		long setupTime = 0;
		long lookupTime = 0;
		int numFails=0;
		Object result;
		String key;
		String password = "password";
		String logPath = "/home/main/Desktop/log/";
		int startPort = 10001;
		WhanauRefControl<String> node[] = (WhanauRefControl<String>[])new WhanauRefControl[numNodes];
		WhanauSybilNode<String> sybilNode;
		WhanauState<String> states[] = (WhanauState<String>[]) new WhanauState[numNodes];
		Logger log;
		WhanauKeyState keyState;
		KeyValueChecker kvChecker;
		HashSet<String> controlKeys = new HashSet<String>();
		controlKeys.add("asdf");
		String outputString = "";
		outputString += "w="+w+", k="+k+", r="+r+"\n";
		System.out.println("Starting");
		//if (System.getSecurityManager() == null) {
        //    System.setSecurityManager(new RMISecurityManager());
        //}
		//System.setProperty("java.rmi.server.hostname", "localhost");
		try {
			//Create honest nodes
			for (int i=0;i<node.length;i++)	{
				String logFile = logPath+"whanau"+(startPort+i)+".log";
				log = LogUtil.createLogger("whanau"+(startPort+i), logFile);
				keyState = new WhanauKeyState(CryptoTool.generateKeyStore(password), password);
				//kvChecker = new HashingKVChecker<String>(log,keyState);
				kvChecker = new SigningKVChecker<String>(log,keyState,WhanauDHTConstants.DEFAULT_TTL);
				states[i] = new WhanauState<String>("n"+i, kvChecker,"localhost",(startPort+i), keyState, log, logFile);
				controlKeys = new HashSet<String>();
				controlKeys.add(states[i].getPubKeyHash());
				node[i] = (new WhanauVirtualNode<String>(states[i], controlKeys)).getControlRef();
			}
			//Create Sybil Node
			String logFile = logPath+"sybil.log";
			log = LogUtil.createLogger("sybil", logFile);
			keyState = new WhanauKeyState(CryptoTool.generateKeyStore(password), password);
			//kvChecker = new HashingKVChecker<String>(log,keyState);
			kvChecker = new SigningKVChecker<String>(log,keyState,WhanauDHTConstants.DEFAULT_TTL);
			sybilNode = new WhanauSybilNode<String>(new WhanauState<String>("sybil", kvChecker,"localhost",(startPort+numNodes), keyState, log, logFile), null);
			//Add peers
			for (int i=0; i<node.length; i++) {
				for (int j=0;j<numPeers;j++){
					peerNum = node[i].getState().nextRandInt(numNodes);
					if (peerNum != i){
						node[i].addPeer(node[peerNum].getState().getPubKeyHash(),"localhost",(startPort+peerNum));
						node[peerNum].addPeer(node[i].getState().getPubKeyHash(),"localhost",(startPort+i));
					}
				}
				//Add directed edge
				/**
				peerNum = node[i].getState().nextRandInt(numNodes);
				if (peerNum != i){
					node[i].addPeer(node[peerNum].getState().getPubKeyHash(),"localhost",(startPort+peerNum));
				}
				**/
				//Add sybil
				//node[i].addPeer(sybilNode.getState().getPubKeyHash(), "localhost", startPort+numNodes);
			}
			//run setup and lookup tests
			for (int x=0;x<1;x++) {
				//SETUP STAGES
				startTime = System.currentTimeMillis();
				for (int i=0;i<node.length;i++) {
					node[i].runSetupPart1Thread(w, r, r, r);
				}
				for (int i=0;i<node.length;i++) {
					node[i].joinSetupThread();
				}
				for (int i=0;i<node.length;i++) {
					node[i].runSetupPart2Thread(w, r, r, r);
				}
				for (int i=0;i<node.length;i++) {
					node[i].joinSetupThread();
				}
				endTime = System.currentTimeMillis();
				setupTime = endTime - startTime;
				for (int i=0;i<node.length;i++)	{
					if (node[i].getSetupThreadResult() == 0) {
						System.out.println("\t Node Setup Failed for node="+node[i].getState().getLocalPort());
					}
					System.out.println(node[i].getStateStr());
				}
				outputString += "Setup#1 finished in "+setupTime+" ms \n";
				//LOOKUP ON FULL STATE
				for (int i=0;i<numLookups;i++) {
					peerNum = node[0].getState().nextRandInt(numNodes);
					peerNum2 = node[0].getState().nextRandInt(numNodes);
					Object record = node[peerNum2].getState().sampleNodes(1, 0).getFirst().getFirst();
					key = (String) node[peerNum2].getState().getKVChecker().getKeyFromRecord(record);
					startTime = System.currentTimeMillis();
					//result = node[peerNum].lookup(w, key);
					result = node[peerNum].lookup(WhanauDHTConstants.LOOKUP_TIMEOUT,WhanauDHTConstants.SMALLCALL_TIMEOUT,rl,w,key);
					endTime = System.currentTimeMillis();
					if (result == null){
						numFails++;
					}
					else{
						if (node[0].getState().getKVChecker().checkKeyRecord(key, result)){
							lookupTime+= (endTime - startTime);
						}
						else {
							System.out.println("SELF-CERTIFYING ERROR: key="+key+" value="+(String)result);
							System.exit(1);
						}
					}
				}
				outputString += "Numlookups="+numLookups+" fails="+numFails+" avgSuccTime="+(lookupTime/(numLookups-numFails))+"ms \n";
				
				for(int i=0;i<node.length;i++){
					//outputString += node[i].getState().printCryptoStats()+"\n";
					node[i].getState().setSetupStage(0);
				}
				
			}
		}
		catch(Exception e) {
			e.printStackTrace();
			System.err.println(e.getMessage());
		}
		
		System.out.println(outputString);
		System.exit(0);
	}
}
