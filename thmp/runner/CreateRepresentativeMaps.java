package thmp.runner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import thmp.parse.DetectHypothesis.DefinitionListWithThm;
import thmp.parse.ParsedExpression;
import thmp.search.CollectThm;
import thmp.search.NGramSearch;
import thmp.search.ThreeGramSearch;
import thmp.search.WordFrequency;
import thmp.search.Searcher.SearchMetaData;
import thmp.utils.FileUtils;

/**
 * Build maps that are comprehensive and representative,
 * by calling eg methods in ThmWordsMaps, NGramMaps, etc.
 * 
 * @author yihed
 *
 */
public class CreateRepresentativeMaps {

	public static void main(String[] args){
		//read thms from parsed expressions serialized file
		if(args.length == 0){
			System.out.println("Enter a serialized file of parsed expressions!");
			return;
		}
		//e.g. "combinedParsedExpressionsList.dat"
		String peFilePath = args[0];
		//String peFilePath = "src/thmp/data/parsedExpressionList.dat";
		List<String> thmList = extractThmListFromPEList(peFilePath);
		//System.out.println("thmLIst "+thmList);
		buildAndSerialize23GramMaps(thmList);
		if(false) buildAndSerializeTrueFluffWordsSet(thmList);
	}	
	
	private static List<String> extractThmListFromPEList(String peFilePath){
		List<String> thmList = new ArrayList<String>();
		@SuppressWarnings("unchecked")
		List<ParsedExpression> peList = (List<ParsedExpression>)FileUtils.deserializeListFromFile(peFilePath);
		System.out.println("CreateRepresentativeMaps - peList.size() " +peList.size());
		for(ParsedExpression pe : peList){
			DefinitionListWithThm defListWithThm = pe.getDefListWithThm();
			//System.out.println("defListWithThm "+defListWithThm);
			thmList.add(defListWithThm.getDefinitionStr() + " " + defListWithThm.getThmStr());
		}
		return thmList;
	}
	
	/**
	 * Just freq map, not scores, so to be able to update scoring scheme later.
	 * This is the universal map used by context and relation vector creations.
	 * as well as SVD.
	 * The map is on the order of O(10^4)
	 * @return Map of words and their frequencies. Ordered in decreasing order 
	 * with respect to frequency.
	 */
	private static Map<String, Integer> createDocWordFreqMap(List<String> thmList){
		//
		return CollectThm.ThmWordsMaps.buildDocWordsFreqMap(thmList);
	}
	
	/**
	 * Builds and return true fluff words set. English words such as
	 * "is", "the", etc. Math words occur much more frequently in math corpus than 
	 * their avg freq in English, e.g. "representation", "group".
	 * @param thmList A representative set of thms.
	 */
	private static void buildAndSerializeTrueFluffWordsSet(List<String> thmList){
		
		Map<String, Integer> corpusWordFreqMap = new HashMap<String, Integer>();
		//fills corpusWordFreqMap
		int TOTAL_CORPUS_WORD_COUNT = WordFrequency.extractThmAllWordsFrequency(thmList, corpusWordFreqMap);		
		Set<String> trueFluffWordsSet = new HashSet<String>();
		WordFrequency.ComputeFrequencyData.buildTrueFluffWordsSet(trueFluffWordsSet, 
				corpusWordFreqMap, TOTAL_CORPUS_WORD_COUNT);
		
		String trueFluffWordsSetPath = FileUtils.getPathIfOnServlet(SearchMetaData.trueFluffWordsSetPath());
		List<Set<String>> trueFluffWordsSetList = new ArrayList<Set<String>>();
		trueFluffWordsSetList.add(trueFluffWordsSet);
		
		FileUtils.serializeObjToFile(trueFluffWordsSetList, trueFluffWordsSetPath);
		//for inspection
		String trueFluffWordsSetTxtPath = trueFluffWordsSetPath.substring(0, trueFluffWordsSetPath.length()-3) + "txt";
		FileUtils.writeToFile(trueFluffWordsSet, trueFluffWordsSetTxtPath);
	}
	
	/**
	 * Build and serialize two Gram maps.
	 * @return
	 */
	public static void buildAndSerialize2GramMaps(List<String> thmList){
		//nGramMap Map of maps. Keys are words in text, and entries are maps whose keys are 2nd terms
		// in 2 grams, and entries are frequency counts.
		//this field will be exposed to build 3-grams, and so far only for that purpose.
		Map<String, Map<String, Integer>> twoGramTotalOccurenceMap 
			= new HashMap<String, Map<String, Integer>>();
		Map<String, Integer> twoGramFreqMap = NGramSearch.TwoGramSearch
				.gatherAndBuild2GramsMaps(thmList, twoGramTotalOccurenceMap);
		String twoGramsSerialPath = FileUtils.getPathIfOnServlet(SearchMetaData.twoGramsFreqMapPath());
		List<Map<String, Integer>> twoGramFreqMapList = new ArrayList<Map<String, Integer>>();
		twoGramFreqMapList.add(twoGramFreqMap);
		FileUtils.serializeObjToFile(twoGramFreqMapList, twoGramsSerialPath);
		//for inspection
		String twoGramsTxtPath = twoGramsSerialPath.substring(0, twoGramsSerialPath.length()-3) + "txt";
		FileUtils.writeToFile(twoGramFreqMap, twoGramsTxtPath);		
	}
	
	/**
	 * Build and serialize two and three Gram maps.
	 * Two and three grams combined, since three gram building requires frequency data
	 * generated when building two grams.
	 * @return
	 */
	public static void buildAndSerialize3GramMaps(List<String> thmList){
		//nGramMap Map of maps. Keys are words in text, and entries are maps whose keys are 2nd terms
		// in 2 grams, and entries are frequency counts.
		//this field will be exposed to build 3-grams, and so far only for that purpose.
		Map<String, Map<String, Integer>> twoGramTotalOccurenceMap 
			= new HashMap<String, Map<String, Integer>>();
		Map<String, Integer> twoGramFreqMap = NGramSearch.TwoGramSearch
				.gatherAndBuild2GramsMaps(thmList, twoGramTotalOccurenceMap);
		/*String twoGramsSerialPath = FileUtils.getPathIfOnServlet(SearchMetaData.twoGramsFreqMapPath());
		List<Map<String, Integer>> twoGramFreqMapList = new ArrayList<Map<String, Integer>>();
		twoGramFreqMapList.add(twoGramFreqMap);
		FileUtils.serializeObjToFile(twoGramFreqMapList, twoGramsSerialPath);
		//for inspection
		String twoGramsTxtPath = twoGramsSerialPath.substring(0, twoGramsSerialPath.length()-3) + "txt";
		FileUtils.writeToFile(twoGramFreqMap, twoGramsTxtPath);*/
				
		Map<String, Integer> threeGramFreqMap = ThreeGramSearch
				.gatherAndBuild3GramsMap(thmList, twoGramTotalOccurenceMap);
		String threeGramsSerialPath = FileUtils.getPathIfOnServlet(SearchMetaData.threeGramsFreqMapPath());		
		List<Map<String, Integer>> threeGramFreqMapList = new ArrayList<Map<String, Integer>>();
		threeGramFreqMapList.add(threeGramFreqMap);
		FileUtils.serializeObjToFile(threeGramFreqMapList, threeGramsSerialPath);		
		//for inspection
		String threeGramsTxtPath = threeGramsSerialPath.substring(0, threeGramsSerialPath.length()-3) + "txt";
		FileUtils.writeToFile(threeGramFreqMap, threeGramsTxtPath);
	}

	/**
	 * Builds both two and three grams maps.
	 * @param thmList
	 */
	public static void buildAndSerialize23GramMaps(List<String> thmList){
		//nGramMap Map of maps. Keys are words in text, and entries are maps whose keys are 2nd terms
		// in 2 grams, and entries are frequency counts.
		//this field will be exposed to build 3-grams, and so far only for that purpose.
		Map<String, Map<String, Integer>> twoGramTotalOccurenceMap 
			= new HashMap<String, Map<String, Integer>>();
		Map<String, Integer> twoGramFreqMap = NGramSearch.TwoGramSearch
				.gatherAndBuild2GramsMaps(thmList, twoGramTotalOccurenceMap);
		String twoGramsSerialPath = FileUtils.getPathIfOnServlet(SearchMetaData.twoGramsFreqMapPath());
		List<Map<String, Integer>> twoGramFreqMapList = new ArrayList<Map<String, Integer>>();
		twoGramFreqMapList.add(twoGramFreqMap);
		FileUtils.serializeObjToFile(twoGramFreqMapList, twoGramsSerialPath);
		//for inspection
		String twoGramsTxtPath = twoGramsSerialPath.substring(0, twoGramsSerialPath.length()-3) + "txt";
		FileUtils.writeToFile(twoGramFreqMap, twoGramsTxtPath);
				
		Map<String, Integer> threeGramFreqMap = ThreeGramSearch
				.gatherAndBuild3GramsMap(thmList, twoGramTotalOccurenceMap);
		String threeGramsSerialPath = FileUtils.getPathIfOnServlet(SearchMetaData.threeGramsFreqMapPath());		
		List<Map<String, Integer>> threeGramFreqMapList = new ArrayList<Map<String, Integer>>();
		threeGramFreqMapList.add(threeGramFreqMap);
		FileUtils.serializeObjToFile(threeGramFreqMapList, threeGramsSerialPath);		
		//for inspection
		String threeGramsTxtPath = threeGramsSerialPath.substring(0, threeGramsSerialPath.length()-3) + "txt";
		FileUtils.writeToFile(threeGramFreqMap, threeGramsTxtPath);
	}
}
