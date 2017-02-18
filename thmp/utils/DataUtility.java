package thmp.utils;

import java.util.Iterator;

/**
 * Class for utility functions for manipulating data.
 * @author yihed
 */
public class DataUtility {

	public static <T extends Comparable<T>> T findMax(Iterable<T> iterable){
		
		Iterator<T> iter = iterable.iterator();
		if(!iter.hasNext()){
			return null;
		}
		T max = iter.next();
		
		while(iter.hasNext()){
			T next = iter.next();
			if(1 == next.compareTo(max)){
				max = next;
			}
		}
		return max;
	}
	
}
