package thmp.search;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.servlet.ServletContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.wolfram.puremath.dbapp.ThmHypUtils;

import thmp.search.SearchCombined.ThmHypPair;
import thmp.search.Searcher.SearchConfiguration;
import thmp.search.TheoremGet.ContextRelationVecBundle;
import thmp.search.TheoremGet.ContextRelationVecPair;
import thmp.utils.FileUtils;

/**
 * Bundle used to cache ThmHypPair's in a LoadingCache.
 * @author yihed
 *
 */
public class ThmHypPairGet{

	private static final LoadingCache<Integer, ThmHypPairBundle> thmBundleCache;
	private static final ServletContext servletContext = FileUtils.getServletContext();
	//private static final String metaDataFilePath = "/src/thmp/hhh";
	private static final Logger logger = LogManager.getLogger(ThmHypPairGet.class);

	/*do binary search on this list to find the index of the first thm the bundles
	  length should be around 313 (as of Oct 2017) */
	private static final List<Integer> bundleStartThmIndexList;
	/**total number of thms*/
	private static final int totalThmsCount;
	private static final int totalBundleNum;
	
	static{
		//deserialize the list set during preprocessing,  
		SearchConfiguration searchConfig = FileUtils.deserializeSearchConfiguration();
		bundleStartThmIndexList = searchConfig.bundleStartThmIndexList();		
		System.out.println("ThmHypPairGet - bundleStartThmIndexList:  "+bundleStartThmIndexList );
		totalThmsCount = searchConfig.totalThmsCount();
		totalBundleNum = bundleStartThmIndexList.size();
		//318 total as of Sept 16
		thmBundleCache = CacheBuilder.newBuilder()
				.maximumSize(500) //~5mb x 500 = 2500 mb
				//.removalListener( null)
				.build(
						new CacheLoader<Integer, ThmHypPairBundle>() {
							public ThmHypPairBundle load(Integer bundleKey){
								System.out.println("ThmHypPairGet - loading a new ThmHypPair bundle with key " + bundleKey);
								return new ThmHypPairBundle(bundleKey);
							}
						}
						);	
	}
	
	/**
	 * Iterates over all possible mx or bundles keys backwards, 
	 * chronologically.
	 */
	public static class MxBundleKeyIterator implements Iterator<Integer>{
		//deliberately don't subtract 1.
		private volatile int currentIndex = totalBundleNum;
		
		@Override
		public boolean hasNext(){			
			return this.currentIndex > 0;
		}
		
		@Override
		public Integer next(){
			return --this.currentIndex;
		}
		
		/**
		 * finds the overall thm index, given the index within the current
		//bundle bundleStartThmIndexList
		 * @param thmIndexInBundle
		 * @return
		 */
		public int findOverallThmIndex(int thmIndexInBundle) {
			return bundleStartThmIndexList.get(this.currentIndex) + thmIndexInBundle;
		}
	}
	
	/**
	 * Iterates over all possible bundles backwards, 
	 * chronologically.
	 */
	public static class ThmCacheIterator implements Iterator<ThmHypPairBundle>{
		//deliberately don't subtract 1.
		private volatile int currentIndex = totalBundleNum;
		
		@Override
		public boolean hasNext() {
			return currentIndex > 0;
		}
		/**
		 * Returns next element in bundles 
		 * @return Could be null.
		 */
		@Override
		public ThmHypPairBundle next() {
			this.currentIndex--;
			try {
				return thmBundleCache.get(currentIndex);
			} catch (ExecutionException e) {
				String msg = "ExecutionException when trying to retrieve bundle! " + e;
				//print for now for local debugging. 
				System.out.println(msg);
				logger.error(msg);
			}
			//try return some sort of singleton instance with private constructor
			return ThmHypPairBundle.PLACEHOLDER_BUNDLE;
		}		
	}
	
	//Bundle used to cache ThmHypPair's in a LoadingCache.
	public static class ThmHypPairBundle implements Serializable, Iterable<ThmHypPair>{
		
		private static final long serialVersionUID = 536853474970343329L;
		//Need to re-partition serialized data, if this number changes!
		//private static final int NUM_THMS_IN_BUNDLE = 10000;
		
		//need to create these directories
		protected static final String BASE_FILE_STR = "src/thmp/data/pe/" 
				+ ThmSearch.TermDocumentMatrix.COMBINED_PARSEDEXPRESSION_LIST_FILE_NAME_ROOT;
		//private static final String BASE_FILE_EXT_STR = ".dat";
		//indicates which file to load in memory. Keys are consecutive, 1, 2,...
		//*not* theorem starting indices in bundles!
		private int bundleKey;
		private List<ThmHypPair> thmPairList;
		private static final ThmHypPairBundle PLACEHOLDER_BUNDLE = new ThmHypPairBundle();
		
		public ThmHypPairBundle(int bundleKey){	
			this.bundleKey = bundleKey;
			String serialFileStr = BASE_FILE_STR + String.valueOf(bundleKey);
			if(servletContext != null){
				serialFileStr = servletContext.getRealPath(serialFileStr);
			}
			thmPairList = deserializeThmHypPairListFromFile(serialFileStr);
		}
		
		private ThmHypPairBundle(){
			thmPairList = Collections.<ThmHypPair>emptyList();
		}
		
		@SuppressWarnings("unchecked")
		private List<ThmHypPair> deserializeThmHypPairListFromFile(String serialFileStr){
			return (List<ThmHypPair>)FileUtils.deserializeListFromFile(serialFileStr);
		}

		@Override
		public Iterator<ThmHypPair> iterator() {
			return this.thmPairList.iterator();
		}		
		
		public ThmHypPairBundle PLACEHOLDER_BUNDLE(){
			return PLACEHOLDER_BUNDLE;
		}

		/**
		 * @return the bundleKey
		 */
		public int getBundleKey() {
			return this.bundleKey;
		}
	}
	
	public static Iterator<ThmHypPairBundle> createThmCacheIterator(){
		return new ThmCacheIterator();
	}
	
	public static MxBundleKeyIterator createMxBundleKeyIterator(){
		return new MxBundleKeyIterator();
	}
	/**
	 * Return the bundle containing thm with index thmIndex
	 * @param thmIndex
	 * @return Can be null!
	 */
	public static ThmHypPairBundle retrieveBundleWithThm(int thmIndex){
		int bundleStartThmIndexListIndex = findBundleBeginIndex(thmIndex);
		try {
			return thmBundleCache.get(bundleStartThmIndexListIndex);
		} catch (ExecutionException e) {
			String msg = "ExecutionException when retrieving from cache! " + e;
			//print for now so visible to local debugging
			System.out.println(msg);
			logger.error(msg);
		}
		return ThmHypPairBundle.PLACEHOLDER_BUNDLE;
	}

	/**
	 * Return the ThmHypPair with index thmIndex.
	 * This is no longer used by main search after moving data to DB. But is still
	 * used by similar thm computation. Also very useful for debugging on byblis. 
	 * @param thmIndex
	 * @return
	 */
	public static ThmHypPair retrieveThmHypPairWithThmFromCache(int thmIndex){
		
		//index inside the bundleStartThmIndexList, to get the index of the starting thm in bundle.
		int bundleStartThmIndexListIndex = findBundleBeginIndex(thmIndex);
		try {
			return thmBundleCache.get(bundleStartThmIndexListIndex)
					.thmPairList.get(thmIndex - bundleStartThmIndexList.get(bundleStartThmIndexListIndex));
		} catch (ExecutionException e) {
			String msg = "ExecutionException when retrieving from cache! " + e;
			//print for now so visible to local debugging
			System.out.println(msg);
			logger.error(msg);
		}
		return ThmHypPair.PLACEHOLDER_PAIR();
	}
	
	/**
	 * Return the ThmHypPair with index thmIndex
	 * @param thmIndex
	 * @return
	 */
	public static ThmHypPair retrieveThmHypPairWithThm(int thmIndex, Connection conn){
		
		try {
			return ThmHypUtils.getThmHypFromDB(thmIndex, conn);
		} catch (SQLException e) {
			logger.error("SQLException while retrieving ThmHypPair! " + Arrays.toString(e.getStackTrace()));
			return ThmHypPair.PLACEHOLDER_PAIR();
		}
	}
	
	/**
	 * Return the ThmHypPair with list of indices.
	 * @param list of thm indices.
	 * @return
	 */
	public static List<ThmHypPair> retrieveThmHypPairWithThm(List<Integer> thmIndexList, Connection conn){
		
		try {
			return ThmHypUtils.getThmHypFromDB(thmIndexList, conn);
		} catch (SQLException e) {
			logger.error("SQLException while retrieving ThmHypPair! " + Arrays.toString(e.getStackTrace()));
			return Collections.emptyList();
		}
	}
	
	/**
	 * Uses binary search to find the starting index of the bundle
	 * containing thm with thmIndex. Index in bundleStartThmIndexList.
	 */
	public static int findBundleBeginIndex(int thmIndex){
		
		int low = 0;
		int high = bundleStartThmIndexList.size();
		int mid = (low+high)/2;
		while(true){
			if(low == high - 1){
				return low;
			}
			int midIndex = bundleStartThmIndexList.get(mid);
			if(midIndex < thmIndex){				
				low = mid;				
			}else if(midIndex > thmIndex){
				high = mid;				
			}else{
				return mid;
			}
			mid = (low+high)/2;			
		}		
	}
	
	/**
	 * Total number of bundles or mx files for all tars collected.
	 * @return
	 */
	public static int totalBundleNum(){
		return totalBundleNum;
	}
	
	/**
	 * Total number of thms in all tars.
	 * @return
	 */
	public static int totalThmsCount(){
		return totalThmsCount;
	}
}
