package thmp.runner;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Multiset;
import com.wolfram.puremath.dbapp.DBUtils;
import com.wolfram.puremath.dbapp.SimilarThmUtils;

import thmp.search.ThmHypPairGet;
import thmp.utils.FileUtils;

/**
 * Selects n-grams from theorems.
 * @author yihed
 *
 */
public class SelectThmNGrams {

	//number of thms to serialize at a time
	private static final int numThmPerBundle = 5000;
		
	public static void main(String[] args) {		
		selectNGrams();
	}
	
	/**
	 * Combines different serialized files, and serializes them.
	 */
	private static void selectNGrams() {
		
		
		int totalThmCount = ThmHypPairGet.totalThmsCount();
		int totalBundles = (int)Math.ceil(((double)totalThmCount) / numThmPerBundle);
		
		//1/3 way through, stop adding new ones, for memory purposes, and only add count if already contained
		
		for(int j = 0; j < totalBundles; j++) {
			
			boolean addNewTerms = j < totalBundles/3;
			
			Map<Integer, byte[]> similarThmsMap = new HashMap<Integer, byte[]>();
			int endingIndex = Math.min(totalThmCount, (j+1)*numThmPerBundle);
			int startingIndex = j * numThmPerBundle;
			for(int i = startingIndex; i < endingIndex; i++) {				
				List<Integer> similarThmList = thmp.search.SimilarThmSearch.preComputeSimilarThm( i );
				similarThmsMap.put(i, SimilarThmUtils.indexListToByteArray(similarThmList));
				
				
			}
			String path = DBUtils.SimilarThmsTb.similarThmIndexByteArrayPathNoDat + j + ".dat" ;
			//make map instead of write to file.
			List<Map<Integer, byte[]>> similarThmsMapList = new ArrayList<Map<Integer, byte[]>>();
			similarThmsMapList.add(similarThmsMap);
			thmp.utils.FileUtils.serializeObjToFile(similarThmsMapList, path);
			System.out.println("Done serializing for bundle " + j);
		}
		
	}
	
	/**
	 * account for latex. Select 4 grams given a thm.
	 * @param thm
	 * @param nGramMSet set of n grams already gathered
	 */
	private static void select4Grams(String thm, Multiset<String> nGramMSet,
			boolean allowNewWords) {
		
		String thmLower = thm.toLowerCase();
		
		
		
	}
	
}
