package thmp.utils;

import java.util.Comparator;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class for utility functions for manipulating data.
 * @author yihed
 */
public class DataUtility {
	//could be math-ph/1315233
	private static final Pattern FILE_NAME_PATTERN1 = Pattern.compile("([A-Z\\-a-z]+)([\\d]+)");
	private static final Pattern FILE_NAME_PATTERN2 = Pattern.compile("[\\d]+\\.[\\d]+");
	
	/**
	 * O(n) time to find the max element of an iterable.
	 * @param iterable
	 * @return
	 */
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
	
	public static class ReverseIntComparator implements Comparator<Integer>{
		
		@Override
		public int compare(Integer a, Integer b){
			return a < b ? 1 : (a > b ? -1 : 0);
		}
	}
	
	/**
	 * 
	 * @param srcFileName, two forms, e.g. math0211002, or 4387.86213
	 * @return
	 */
	public static String createArxivURLFromFileName(String srcFileName){
		//StringBuilder urlSB = new StringBuilder("https://arxiv.org/abs/");
		String url = srcFileName;
		String paperRef = null;
		Matcher matcher;
		if((matcher=FILE_NAME_PATTERN1.matcher(srcFileName)).matches()){
			//turn into e.g. "https://arxiv.org/abs/math/0211144"
			paperRef = matcher.group(1) + "/" + matcher.group(2);
			//urlSB.append(matcher.group(1));
		}else if((matcher=FILE_NAME_PATTERN2.matcher(srcFileName)).matches()){
			//turn into e.g. "https://arxiv.org/abs/4387.86213"
			paperRef = srcFileName;
			//urlSB.append(srcFileName);
		}
		if(null != paperRef){
			url = "https://arxiv.org/abs/" + paperRef;
		}
		return url;
	}
	
}
