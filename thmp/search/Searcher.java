package thmp.search;

import java.io.Serializable;
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
	
	/**
	 * Containing data serialized during data processing and building. 
	 * To be used later for post processing,
	 * such as combining lists, etc.
	 * Should only contain fields that need to be serialized.
	 */
	public static class SearchConfiguration implements Serializable{
		
		private static final long serialVersionUID = 1408487446908500897L;
		//Where an instance of this class should be serialized.
		private static final String searchConfigurationSerialPath = "src/thmp/data/searchConfiguration.dat";
		//used in ThmHypPairGet. Containing indices of thms that are the first in a bundle. 
		private List<Integer> bundleStartThmIndexList;
		private int totalThmsCount;
		
		public SearchConfiguration(List<Integer> bundleStartThmIndexList_, int totalThmsCount_){
			this.bundleStartThmIndexList = bundleStartThmIndexList_;
			this.totalThmsCount = totalThmsCount_;
		}
		
		public List<Integer> bundleStartThmIndexList(){
			return bundleStartThmIndexList;
		}
		
		public int totalThmsCount(){
			return this.totalThmsCount;
		}
		public static String searchConfigurationSerialPath(){
			return searchConfigurationSerialPath;
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
		//one is created for each tar, then combined for multiple tars, in ProjectionMatrix.java.
		private static final String wordThmIndexMMapSerialFileName = "wordThmIndexMMap.dat";
		private static final String wordThmIndexMMapSerialFilePath = "src/thmp/data/" + wordThmIndexMMapSerialFileName;
		private static final String wordDocFreqMapPath = "src/thmp/data/allThmWordsMap.dat";
		private static final String threeGramsFreqMapPath = "src/thmp/data/threeGramsMap.dat";
		private static final String twoGramsFreqMapPath = "src/thmp/data/twoGramsMap.dat";
		private static final String trueFluffWordsSetPath = "src/thmp/data/trueFluffWordsSet.dat";
		
		public static void set_gatheringDataBoolToTrue(){
			//System.out.println(Arrays.toString(Thread.currentThread().getStackTrace()));
			gatheringDataBool = true;
		}
		
		public static boolean gatheringDataBool(){
			return gatheringDataBool;
		}
		
		public static String wordThmIndexMMapSerialFilePath(){
			return wordThmIndexMMapSerialFilePath;
		}
		
		public static String wordThmIndexMMapSerialFileName(){
			return wordThmIndexMMapSerialFileName;
		}
		
		public static String wordDocFreqMapPath(){
			return wordDocFreqMapPath;
		}
		
		public static String threeGramsFreqMapPath(){
			return threeGramsFreqMapPath;
		}
		
		public static String twoGramsFreqMapPath(){
			return twoGramsFreqMapPath;
		}
		
		public static String trueFluffWordsSetPath(){
			return trueFluffWordsSetPath;
		}
		/** Used to separate the case when gatheringDataBool, where in fact want maps collected
		 * in previous runs, e.g. when using a pre-computed projection matrix for SVD.*/
		public static String previousWordDocFreqMapsPath() {
			return previousWordDocFreqMapsPath;
		}

		public static void set_previousWordDocFreqMapsPath(String path) {
			SearchMetaData.previousWordDocFreqMapsPath = path;
		}
	}
}
