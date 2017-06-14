package thmp.search;

import java.io.Serializable;
import java.util.List;

import javax.servlet.ServletContext;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import thmp.search.SearchCombined.ThmHypPair;
import thmp.search.TheoremGet.ContextRelationVecBundle;
import thmp.search.TheoremGet.ContextRelationVecPair;
import thmp.utils.FileUtils;

/**
 * Bundle used to cache ThmHypPair's in a LoadingCache.
 * @author yihed
 *
 */
public class ThmHypPairGet {

	private static final LoadingCache<Integer, ThmHypPairBundle> thmBundleCache;
	private static final ServletContext servletContext = FileUtils.getServletContext();
	private static final String metaDataFilePath = "/src/thmp/hhh";
	/*do binary search on this list to find the index of the first thm the bundles
	  length should be around 100 */
	private static final List<Integer> bundleStartThmIndexList;
			
	static{
		//set the list 
		bundleStartThmIndexList = deserializeStartThmIndexList();
		thmBundleCache = CacheBuilder.newBuilder()
				.maximumSize(50) //30mb x 50 = 1500 mb
				.removalListener( null)
				.build(
						new CacheLoader<Integer, ThmHypPairBundle>() {
							public ThmHypPairBundle load(Integer bundleKey){
								System.out.println("ThmHypPairGet - loading a new ThmHypPair bundle with key " + bundleKey);
								return new ThmHypPairBundle(bundleKey);
							}
						}
						);
	
	}
	
	//Bundle used to cache ThmHypPair's in a LoadingCache.
	public static class ThmHypPairBundle implements Serializable{
		
		private static final long serialVersionUID = 536853474970343329L;
		//Need to re-partition serialized data, if this number changes!
		//private static final int NUM_THMS_IN_BUNDLE = 10000;//10000;
		
		//need to create these directories
		protected static final String BASE_FILE_STR = "src/thmp/data/pe/" + ThmSearch.TermDocumentMatrix.CONTEXT_VEC_PAIR_LIST_FILE_NAME;
		//private static final String BASE_FILE_EXT_STR = ".dat";
		//Name of serialized file. 
		private String serialFileStr;
		//indicates which file to load in memory.
		private int bundleKey;
		private List<ThmHypPair> thmPairList;
		
		public ThmHypPairBundle(int bundleKey){
			
			String serialFileStr = BASE_FILE_STR + String.valueOf(bundleKey);
			if(servletContext != null){
				serialFileStr = servletContext.getRealPath(serialFileStr);
			}
			thmPairList = deserializeThmHypPairListFromFile(serialFileStr);
		}
		
		@SuppressWarnings("unchecked")
		List<ThmHypPair> deserializeThmHypPairListFromFile(String serialFileStr){
			return (List<ThmHypPair>)FileUtils.deserializeListFromFile(serialFileStr);
		}
		
	}
	
	/**
	 * Return the bundle containing thm with index thmIndex
	 * @param thmIndex
	 * @return
	 */
	public static ThmHypPairBundle retrieveBundleWithThm(int thmIndex){
		int bundleStartThmIndexListIndex = findBundleBeginIndex(thmIndex);
		return thmBundleCache.get(bundleStartThmIndexListIndex);
	}
	
	public static ThmHypPair retrieveThmHypPairWithThm(int thmIndex){
		int bundleStartThmIndexListIndex = findBundleBeginIndex(thmIndex);
		ThmHypPairBundle bundle = thmBundleCache.get(bundleStartThmIndexListIndex);
		//
		
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
	private static List<String> deserializeStartThmIndexList(){
		String path = metaDataFilePath;
		if(null != servletContext){
			path = servletContext.getRealPath(path);
		}
		return ;
	}

}
