package thmp.runner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import thmp.search.CollectThm;
import thmp.search.NGramSearch;
import thmp.search.ThreeGramSearch;
import thmp.search.Searcher.SearchMetaData;
import thmp.utils.FileUtils;

/**
 * Build maps that are comprehensive and representative,
 * by calling eg methods in ThmWordsMaps, NGramMaps, etc.
 * @author yihed
 *
 */
public class CreateRepresentativeMaps {

	public static void main(String[] args){
		
	}
	
	/**
	 * Just freq map, not scores, so to be able to update scoring scheme later.
	 * This is the universal map used by context and relation vector creations.
	 * as well as SVD.
	 * @return Map of words and their frequencies. Ordered in decreasing order 
	 * with respect to frequency.
	 */
	private static Map<String, Integer> createDocWordFreqMap(List<String> thmList){
		return CollectThm.ThmWordsMaps.buildDocWordsFreqMap(thmList);
	}
	
	/**
	 * Build and serialize two and three Gram maps.
	 * @return
	 */
	public static void buildAndSerializeNGramMaps(List<String> thmList){
		//nGramMap Map of maps. Keys are words in text, and entries are maps whose keys are 2nd terms
				// in 2 grams, and entries are frequency counts.
				//this field will be exposed to build 3-grams, and so far only for that purpose.
		Map<String, Map<String, Integer>> twoGramTotalOccurenceMap 
			= new HashMap<String, Map<String, Integer>>();
		Map<String, Integer> twoGramFreqMap = NGramSearch.TwoGramSearch
				.gatherAndBuild2GramsMaps(thmList, twoGramTotalOccurenceMap);
		String twoGramsSerialPath = FileUtils.getServletPath(SearchMetaData.twoGramsFreqMapPath());
		List<Map<String, Integer>> twoGramFreqMapList = new ArrayList<Map<String, Integer>>();
		twoGramFreqMapList.add(twoGramFreqMap);
		FileUtils.serializeObjToFile(twoGramFreqMapList, twoGramsSerialPath);
		
		Map<String, Integer> threeGramFreqMap = ThreeGramSearch
				.gatherAndBuild3GramsMap(thmList, twoGramTotalOccurenceMap);
		String threeGramsSerialPath = FileUtils.getServletPath(SearchMetaData.threeGramsFreqMapPath());
		List<Map<String, Integer>> threeGramFreqMapList = new ArrayList<Map<String, Integer>>();
		threeGramFreqMapList.add(threeGramFreqMap);
		FileUtils.serializeObjToFile(threeGramFreqMapList, threeGramsSerialPath);
		
	}

}
