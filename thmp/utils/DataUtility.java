package thmp.utils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
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
	 * Compares integers based on two given maps, prioritize map 1.
	 * Useful for e.g. ranking thm indices, based on some scoring map.
	 * Bigger values in map rank higher/come first.
	 */
	public static class IntMapComparator implements Comparator<Integer>{
		
		//prioritize map1. Combine two maps to form new map used to rank. 
		//private Map<Integer, Integer> map1;
		//private Map<Integer, Integer> map2;
		private Map<Integer, Double> map;
		
		/**
		 * 
		 * @param map1_
		 * @param map2_
		 * @param map2Weight, usually <= 1
		 */
		public IntMapComparator(Map<Integer, Integer> map1, Map<Integer, Integer> map2,
				double map2Weight) {
			//private Map<Integer, Integer> map1 = ImmutableMap.copyOf(map1_);
			//this.map2 = ImmutableMap.copyOf(map2_);
			//make new map
			this.map = new HashMap<Integer, Double>();
			for(Map.Entry<Integer, Integer> entry : map1.entrySet()) {
				int key = entry.getKey();
				Integer map2Val = map2.get(key);
				map2Val = map2Val == null ? 0 : map2Val;
				map.put(entry.getKey(), entry.getValue() + map2Weight*map2Val);
			}
		}
		
		//higher score come in earlier when sorting
		@Override
		public int compare(Integer a, Integer b){
			/*Integer aScore1 = map1.get(a);
			Integer bScore1 = map1.get(b);			
			Integer aScore2 = map2.get(a);
			Integer bScore2 = map2.get(b);*/
			Double aScore = map.get(a);
			Double bScore = map.get(b);
			
			if(null == aScore && null == bScore || Integer.valueOf(a) == Integer.valueOf(b)) {
				return 0;
			}else  {
				if(null == aScore) {
					return 1;
				}
				if(null == bScore) {
					return -1;
				}
			}			
			/*if(aScore1 < bScore1) {				
				return 1;
			}else if(aScore1 > bScore1){				
				return -1;
			}else {
				return aScore2 < bScore2 ? 1 : (aScore2 > bScore2 ? -1 : 0);
			}*/
			return aScore < bScore ? 1 : (aScore > bScore ? -1 : 0);
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
