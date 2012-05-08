package edu.mit.csail.whanausip.commontools.threads;

/**
 * Interface for classes that want to run one of their methods in a separate thread
 * We use this instead of Runnable because we want to retrieve return objects
 * 
 * @author ryscheng
 * @date 2009/10/19
 */
public interface MethodThreadInterface {
	/**
	 * Contains the method that needs to be run in a separate thread
	 * 
	 * @param parameters 	Object[] = array of parameters
	 * @return 				Object	 = return Object
	 */
	public Object methodThread(Object[] parameters);
}
