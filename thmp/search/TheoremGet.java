package thmp.search;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.servlet.ServletContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import thmp.utils.FileUtils;

/**
 * Retrieves theorems from either cache or disk. 
 * Uses a Loading Cache to keep recently-queried results.
 * @author yihed
 *
 */
public class TheoremGet {
	
	private static final LoadingCache<Integer, ContextRelationVecBundle> vecBundleCache;
	private static final Logger logger = LogManager.getLogger(TheoremGet.class);
	private static final ServletContext servletContext = FileUtils.getServletContext();
	
	static{
		vecBundleCache = CacheBuilder.newBuilder()
				//.maximumSize(400) //number of entries
				.maximumSize(40) //65mb x 40 = 2600mb
				.build(
						new CacheLoader<Integer, ContextRelationVecBundle>() {
							public ContextRelationVecBundle load(Integer bundleKey){
								System.out.println("TheoremGet - loading a new vec bundle with key " + bundleKey);
								return new ContextRelationVecBundle(bundleKey);
						}});		
	}
	
	/**
	 * Get the ContextRelationVecs corresponding to a thm given its index.
	 * @param thmIndex
	 * @return
	 */
	public static ContextRelationVecPair getContextRelationVecFromIndex(int thmIndex){
		int bundleKey = ContextRelationVecBundle.getContextVecBundleKey(thmIndex);
		ContextRelationVecBundle bundle;
		try{
			bundle = vecBundleCache.get(bundleKey);
		}catch(ExecutionException e){
			String msg = "ExecutionException when getting thm from LoadingCache! for index: " + thmIndex;
			//print for testing locally
			System.out.println(msg);
			logger.error(msg);
			return ContextRelationVecPair.PLACEHOLDER_CONTEXT_RELATION_VEC;
		}
		return bundle.getContextRelationVecsFromIndex(thmIndex-bundleKey*ContextRelationVecBundle.numThmsInBundle());
	}
	
	/**
	 * Bundle of context and relation vectors. To be serialized and cached.
	 */
	public static class ContextRelationVecBundle implements Serializable{
		
		private static final long serialVersionUID = 760047710418503324L;
		//Need to re-partition serialized data, if this number changes!
		private static final int NUM_THMS_IN_BUNDLE = 10000;//10000;
		protected static final String BASE_FILE_STR = "src/thmp/data/vecs/" + ThmSearch.TermDocumentMatrix.CONTEXT_VEC_PAIR_LIST_FILE_NAME;
		//private static final String BASE_FILE_EXT_STR = ".dat";
		//Name of serialized file. 
		private String serialFileStr;
		//indicates which file to load in memory.
		private int bundleKey;
		private List<ContextRelationVecPair> vecsList;
		
		public ContextRelationVecBundle(int startingIndex){
			bundleKey = startingIndex/NUM_THMS_IN_BUNDLE;
			serialFileStr = constructSerialFilePath(bundleKey);
			vecsList = deserializeContextVecListFromFile(serialFileStr);			
		}
		
		private static String constructSerialFilePath(int bundleKey){
			String path = BASE_FILE_STR + String.valueOf(bundleKey);// + BASE_FILE_EXT_STR;
			if(servletContext != null){
				path = servletContext.getRealPath(path);
			}
			return path;
		}
		
		@SuppressWarnings("unchecked")
		private List<ContextRelationVecPair> deserializeContextVecListFromFile(String serialFileStr){			
			return (List<ContextRelationVecPair>)FileUtils.deserializeListFromFile(serialFileStr);
		}
		
		/**
		 * @param thmIndexRemainder after subtracting the starting index.
		 * @return
		 */
		public ContextRelationVecPair getContextRelationVecsFromIndex(int thmIndexRemainder){
			return vecsList.get(thmIndexRemainder);
		}
		
		/**
		 * @return the serialFileStr
		 */
		public String getSerialFileStr() {
			return serialFileStr;
		}

		public List<ContextRelationVecPair> getVecsList() {
			return vecsList;
		}
		
		/**
		 * @return the bundleKey
		 */
		public int getBundleKey() {
			return bundleKey;
		}
		
		public static int getContextVecBundleKey(int thmIndex){
			int startingIndex = thmIndex/NUM_THMS_IN_BUNDLE;
			//int endingIndex = startingIndex + NUM_THMS_IN_BUNDLE;
			return startingIndex;
		}		
		
		public static int numThmsInBundle(){
			return NUM_THMS_IN_BUNDLE;
		}		
	}
	
	/** Key containing range. Should just use starting index */
	@Deprecated
	public static class ContextRelationBundleKey{
		
		private int startingIndex;
		private int endingIndex;
		
		public ContextRelationBundleKey(int startingIndex_, int endingIndex_){
			this.startingIndex = startingIndex_;
			this.endingIndex = endingIndex_;
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + endingIndex;
			result = prime * result + startingIndex;
			return result;
		}

		@Override
		public boolean equals(Object other){
			if(this == other){
				return true;
			}
			if(!(other instanceof ContextRelationBundleKey)){
				return false;
			}
			ContextRelationBundleKey otherKey = (ContextRelationBundleKey)other;
			if(otherKey.startingIndex != this.startingIndex){
				return false;
			}
			if(otherKey.endingIndex != this.endingIndex){
				return false;
			}
			return true;
		}		
	}
	
	/** class of combination of context and relation vectors */
	public static class ContextRelationVecPair implements Serializable{
		
		private static final long serialVersionUID = 5655823515836997828L;
		//private String contextVecStr;
		private Map<Integer, Integer> contextVecMap;
		
		//relational vector, see RelationVec.java.
		private BigInteger relationVec;
		private static final ContextRelationVecPair PLACEHOLDER_CONTEXT_RELATION_VEC;
		
		static{
			BigInteger relationVector = thmp.parse.RelationVec.getPlaceholderRelationVec();
			//int[] contextVecString = ParseState.PLACEHOLDER_CONTEXT_VEC();
			PLACEHOLDER_CONTEXT_RELATION_VEC = new ContextRelationVecPair(Collections.<Integer, Integer>emptyMap(), relationVector);
		}
		
		public ContextRelationVecPair(Map<Integer, Integer> contextVecMap_, BigInteger relationVec_){			
			//this.contextVecStr = GenerateContextVector.contextVecIntArrayToString(combinedContextVec);
			this.contextVecMap = contextVecMap_;
			this.relationVec = relationVec_;
		}
		
		public Map<Integer, Integer> contextVecMap(){
			return this.contextVecMap;
		}
		public BigInteger relationVec(){
			return this.relationVec;
		}
		public static ContextRelationVecPair PlaceholderContextRelationVecs(){
			return PLACEHOLDER_CONTEXT_RELATION_VEC;
		}		
	}
	
}
