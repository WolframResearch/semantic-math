package thmp.search;

import java.io.Serializable;
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
	  length should be around 100 */
	private static final List<Integer> bundleStartThmIndexList;
	private static final int totalThmsCount;
	private static final int totalBundleNum;
	
	static{
		//deserialize the list set during preprocessing,  
		SearchConfiguration searchConfig = deserializeSearchConfiguration();
		bundleStartThmIndexList = searchConfig.bundleStartThmIndexList();		
		System.out.println("ThmHypPairGet - bundleStartThmIndexList:  "+bundleStartThmIndexList );
		totalThmsCount = searchConfig.totalThmsCount();
		totalBundleNum = bundleStartThmIndexList.size();
		thmBundleCache = CacheBuilder.newBuilder()
				.maximumSize(500) //~1.5mb x 50 = 750 mb
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
		//private static final int NUM_THMS_IN_BUNDLE = 10000;//10000;
		
		//need to create these directories
		protected static final String BASE_FILE_STR = "src/thmp/data/pe/" 
				+ ThmSearch.TermDocumentMatrix.COMBINED_PARSEDEXPRESSION_LIST_FILE_NAME_ROOT;
		//private static final String BASE_FILE_EXT_STR = ".dat";
		//Name of serialized file. 
		//private String serialFileStr; <--how is this used??
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
	
	public static Iterator<Integer> createMxBundleKeyIterator(){
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
	 * Return the ThmHypPair with index thmIndex
	 * @param thmIndex
	 * @return
	 */
	public static ThmHypPair retrieveThmHypPairWithThm(int thmIndex){
		
		//index inside the bundleStartThmIndexList, to get the index of the starting thm in bundle.
		int bundleStartThmIndexListIndex = findBundleBeginIndex(thmIndex);
		//System.out.println("ThmHypPairGet thmIndex: "+thmIndex);
		//System.out.println("ThmHypPairGet bundleStartThmIndexListIndex: "+bundleStartThmIndexListIndex);
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
	 * Uses binary search to find the starting index of the bundle
	 * containing thm with thmIndex. Index in bundleStartThmIndexList.
	 */
	public static int findBundleBeginIndex(int thmIndex){
		//
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
	 * Deserialize meta data
	 * @return
	 */
	private static SearchConfiguration deserializeSearchConfiguration(){
		//String path = metaDataFilePath;
		String path = SearchConfiguration.searchConfigurationSerialPath();
		if(null != servletContext){
			path = servletContext.getRealPath(path);
		}
		@SuppressWarnings("unchecked")
		SearchConfiguration searchConfig 
			= ((List<SearchConfiguration>)FileUtils.deserializeListFromFile(path)).get(0);
		return searchConfig;
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
