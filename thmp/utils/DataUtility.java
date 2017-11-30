package thmp.utils;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.ImmutableMap;

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
		
		//map to prioritize
		private Map<Integer, Integer> map1;
		private Map<Integer, Integer> map2;
		
		public IntMapComparator(Map<Integer, Integer> map1_, Map<Integer, Integer> map2_) {
			this.map1 = ImmutableMap.copyOf(map1_);
			this.map2 = ImmutableMap.copyOf(map2_);
		}
		
		@Override
		public int compare(Integer a, Integer b){
			Integer aScore1 = map1.get(a);
			Integer bScore1 = map1.get(b);
			
			Integer aScore2 = map2.get(a);
			Integer bScore2 = map2.get(b);
			
			if(null == aScore1 && null == bScore1) {
				return 0;
			}else if(null == aScore1) {
				return 1;
			}else if(null == bScore1) {
				return -1;
			}
			
			if(aScore1 < bScore1) {
				//experiment with this!!!
				if(bScore2 > bScore2*3./2) {
					return 1;
				}else {
					return -1;
				}
			}else if(aScore1 > bScore1){
				if(bScore2 > bScore2*3./2) {
					return 1;
				}else {
					return -1;
				}
			}else {
				return aScore2 < bScore2 ? 1 : (aScore2 > bScore2 ? -1 : 0);
			}
			//return aScore1 < bScore1 ? 1 : (aScore1 > bScore1 ? -1 : 0);
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
