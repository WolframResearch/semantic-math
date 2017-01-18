package thmp.search;

import java.util.List;

/**
 * Search interface to be implemented by various search methods.
 * Interface useful for dynamic dispatch.
 * 
 * @author yihed
 */
public interface Searcher {

	List<Integer> search(String thm, List<Integer> list);
	
	public static class SearchMetaData{
		
		//whether currently gathering data (e.g. in DetectHypothesis.java), 
		//rather than actively searching.
		private static boolean gatheringDataBool;
		
		public static void set_gatheringDataBoolToTrue(){
			gatheringDataBool = true;
		}
		
		public static boolean gatheringDataBool(){
			return gatheringDataBool;
		}
	}
}
