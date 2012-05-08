package edu.mit.csail.whanausip.commontools;

import java.io.Serializable;

/**
 * Stores generic parameterized pairs
 * 
 * @author ryscheng
 * @date 2010/05/10
 */
public class Pair<A, B> implements Serializable{

	private static final 	long 	serialVersionUID = -2894896222204250456L;
	private 				A 		first;
	private 				B 		second;
	

	/**
	 * Initialized a new Pair
	 * @param first 	A = first item
	 * @param second	B = second item
	 */
	public Pair(A first, B second) {
		super();
		this.first = first;
		this.second = second;
	}
	
	/**
	 * Computes a hash for this object
	 * 
	 * @return int = hashcode for this object
	 */
	public int hashCode() {
		int hashFirst = first !=null ? first.hashCode() : 0;
		int hashSecond = first !=null ? second.hashCode() : 0;
		return (hashFirst + hashSecond) * hashSecond + hashFirst;
	}
	
	/**
	 * Checks for equality between 2 pairs
	 * 
	 * @return boolean = true if same contents, false otherwise
	 */
	public boolean equals(Object other) {
		if (other instanceof Pair<?,?>){
			Pair<?,?> otherPair = (Pair<?,?>) other;
			return (	(this.first == otherPair.first ||
						(this.first != null && otherPair.first != null 
							&& this.first.equals(otherPair.first))) 
					&&
						(this.second == otherPair.second || 
						(this.second != null && otherPair.second != null 
							&& this.second.equals(otherPair.second))));
		}
		return false;
	}
	
	/**
	 * Return string representation of pair
	 * 
	 * @return String = string representation
	 */
	public String toString() {
		return "("+first+", "+second+")"; 
	}
	
	/**
	 * Return the first object in pair
	 * 
	 * @return A = first object
	 */
	public A getFirst() {
		return first;
	}
	
	/**
	 * Sets the first object in Pair
	 * 
	 * @param first A = new first Object
	 */
	public void setFirst(A first)  {
		this.first = first;
	}
	
	/**
	 * Return the second object in pair
	 * 
	 * @return B = second object
	 */
	public B getSecond() {
		return second;
	}
	
	/**
	 * Sets the second object in the Pair
	 * 
	 * @param second B = new second Object
	 */
	public void setSecond(B second) {
		this.second = second;
	}

}
