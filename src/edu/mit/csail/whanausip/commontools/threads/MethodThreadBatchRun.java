package edu.mit.csail.whanausip.commontools.threads;

import java.util.Hashtable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Runs a batch of MethodThreadRunners with timeouts
 * 
 * @author ryscheng
 * @date 2010/01/20
 */
public class MethodThreadBatchRun {

	private Hashtable<String, MethodThreadRunner> 	runners;	
					//Stores all of the runnable MethodThreadRunners 
					//	that need to be processed
	private ExecutorService 						threadPool;	
					//Stores a pool of threads to run batch with
	
	public MethodThreadBatchRun() {
		this.runners = new Hashtable<String, MethodThreadRunner>();
		/**
		 * Use cachedThreadPool in real operations
		 * Using a fixed pool may speed up tests in local testing 
		 **/
		this.threadPool = Executors.newCachedThreadPool();
		//this.threadPool = Executors.newFixedThreadPool(3);
	}
	public MethodThreadBatchRun(int fixedNumThreads) {
		this.runners = new Hashtable<String, MethodThreadRunner>();
		//this.threadPool = Executors.newCachedThreadPool();
		this.threadPool = Executors.newFixedThreadPool(fixedNumThreads);
	}
	
	/**
	 * Adds a new runnable method and begins execution immediately
	 * 
	 * @param key 		String 					= unique identifier for this thread
	 * @param method 	MethodThreadInterface 	= object containing the method that
	 * 											  needs to be run
	 * @param param 	Object[] 				= Stores parameters to feed to the method
	 */
	public void addThread(String key, MethodThreadInterface method, Object... param) {
		MethodThreadRunner currThread;
		currThread = new MethodThreadRunner(key, method, param);
		this.runners.put(key, currThread);
		this.threadPool.execute(currThread);
	}
	
	/**
	 * Waits a fixed amount of time.
	 * Tries to finish first, then wait remaining time.
	 * Cannot add new threads after this point
	 * 
	 * @param timeout int = time (milliseconds) to wait for threads
	 */
	public void joinFixedTime(int timeout){
		long startTime = System.currentTimeMillis();
		this.threadPool.shutdown();
		try {
			this.threadPool.awaitTermination(timeout, TimeUnit.MILLISECONDS);
			long waitTime = startTime + timeout - System.currentTimeMillis();
			if (waitTime > 0){
				synchronized(this){
					this.wait(waitTime);
				}
			}
		} catch (InterruptedException e) {
			/** @todo Interrupted? Why?**/
		}
	}
	
	/**
	 * Either waits until all threads have terminated, 
	 * or until timeout value (in seconds)
	 * Cannot add new threads after this point
	 * 
	 * @param timeout int = time (milliseconds) to wait for threads
	 */
	public void joinTermination(int timeout){
		this.threadPool.shutdown();
		try {
			this.threadPool.awaitTermination(timeout, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			/** @todo Interrupted? Why?**/
		}
	}
	
	/**
	 * Forcibly shuts down all the threads (even if not complete)
	 * Aggregates results into a Hashtable
	 * 
	 * @return Hashtable<String,Object> = 	key=threadIdentifier, 
	 * 										value=returned value from method
	 */
	public Hashtable<String,Object> getFinalResults(){
		Hashtable<String, Object> finalResults = new Hashtable<String,Object>();
		Object result;
		
		this.threadPool.shutdownNow();
		for (String key : this.runners.keySet()) {
			result = this.runners.get(key).getResult();
			if (result != null) {
				finalResults.put(key, this.runners.get(key).getResult());
			}
		}
		
		return finalResults;
	}
	
	/**
	 * Gets the number of threads in this batch
	 * 
	 * @return int = number of threads
	 */
	public int getNumThreads(){
		return runners.size();
	}

}
