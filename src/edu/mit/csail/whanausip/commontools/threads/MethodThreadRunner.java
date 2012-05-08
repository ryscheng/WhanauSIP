package edu.mit.csail.whanausip.commontools.threads;

/**
 * This is a wrapper for the method to be run,
 * as well as the return result
 * 
 * @author ryscheng
 * @date 2009/03/26
 */
public class MethodThreadRunner implements Runnable {

	private MethodThreadInterface 	obj;		//Object containing method to run
	private Object 					result;		//Stores result from methodThread(...)
	private Object[] 				parameters;	//Parameters to methodThread(...)
	private String 					id;			//Unique identifier for this Runnable
	
	/**
	 * Initializes a new thread to run setup on whanau.
	 * 
	 * @param obj 			MethodThreadInterface 	= obj who wants a method to be run on 
	 * 													a separate thread 
	 * @param parameters 	Object 					= parameters to obj.methodthread(...)
	 * @param id 			String 					= unique string to identify this thread
	 */
	public MethodThreadRunner(String id, MethodThreadInterface obj, Object... parameters){
		this.obj = obj;
		this.parameters = parameters;
		this.id = id;
		this.result = null;
	}
	
	/**
	 * Check what the result from obj.methodThread(...) is
	 * 
	 * @return Object
	 */
	public Object getResult(){
		return this.result;
	}
	
	/**
	 * Sets the result of methodThread(...)
	 * 
	 * @param result Object = result
	 */
	private void setResult(Object result){
		this.result = result;
	}
	
	/**
	 * Return the identifier for this instance
	 * 
	 * @return String = ID
	 */
	public String getID(){
		return this.id;
	}
	
	/**
	 * Runs then method and stores the result
	 */
	public void run() {
		this.setResult(this.obj.methodThread(this.parameters));
	}
}
