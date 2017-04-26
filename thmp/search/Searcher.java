package thmp.search;

import java.util.Arrays;
import java.util.List;

import thmp.search.Searcher.SearcherState;

/**
 * Search interface to be implemented by various search methods.
 * Interface useful for dynamic dispatch.
 * 
 * @author yihed
 */
public interface Searcher<S> {

	List<Integer> search(String thm, List<Integer> list);
	
	void setSearcherState(SearcherState<S> searcherState_);
	SearcherState<S> getSearcherState();
	
	/**
	 * Searcher state for storing any already-computed vectors,
	 * to avoid computing search vecs multiple times. 
	 * T is the type of the vector, e.g. BigInteger for relational vecs,
	 * and Map<Integer, Integer> for context vec.
	 */
	public static class SearcherState<T>{
		T queryVector;
		
		public SearcherState(T vec){
			queryVector = vec;
		}
		
		public T getQueryVec(){
			return queryVector;
		}
	}
	
	public static class SearchMetaData{
		
		//whether currently gathering data (e.g. in DetectHypothesis.java), 
		//rather than actively searching.
		private static boolean gatheringDataBool;
		/* Used to separate the case when gatheringDataBool, where in fact want maps collected
		 * in previous runs, e.g. when using a pre-computed projection matrix for SVD.*/
		//private static boolean usePreviousWordDocFreqMaps;
		private static String previousWordDocFreqMapsPath;
		
		public static void set_gatheringDataBoolToTrue(){
			//System.out.println(Arrays.toString(Thread.currentThread().getStackTrace()));
			gatheringDataBool = true;
		}
		
		public static boolean gatheringDataBool(){
			return gatheringDataBool;
		}
		
		/* Used to separate the case when gatheringDataBool, where in fact want maps collected
		 * in previous runs, e.g. when using a pre-computed projection matrix for SVD.*/
		public static String previousWordDocFreqMapsPath() {
			return previousWordDocFreqMapsPath;
		}

		public static void set_previousWordDocFreqMapsPath(String path) {
			SearchMetaData.previousWordDocFreqMapsPath = path;
		}
	}
}
