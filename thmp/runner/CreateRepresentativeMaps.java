package thmp.runner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import thmp.parse.DetectHypothesis.DefinitionListWithThm;
import thmp.parse.ParsedExpression;
import thmp.search.CollectThm;
import thmp.search.NGramSearch;
import thmp.search.ThreeGramSearch;
import thmp.search.WordFrequency;
import thmp.search.Searcher.SearchMetaData;
import thmp.utils.FileUtils;
import thmp.utils.WordForms;
import thmp.utils.WordForms.WordFreqComparator;

/**
 * Build maps that are comprehensive and representative,
 * by calling eg methods in ThmWordsMaps, NGramMaps, etc.
 * NOTE: some methods are commented out at certain times,
 * not because they are never used, but because they are 
 * not invoked in a particular run. Could change to arg
 * supplied to main().
 * 
 * @author yihed
 *
 */
public class CreateRepresentativeMaps {

	private static final Pattern NEWLINE_PATTERN = Pattern.compile("(.+)(?:\r\n|\n)$");
	
	public static void main(String[] args){
		//read thms from parsed expressions serialized file
		if(args.length == 0){
			System.out.println("Enter a serialized file of parsed expressions!");
			return;
		}
		//e.g. "combinedParsedExpressionsList.dat"
		String peFilePath = args[0];
		//String peFilePath = "src/thmp/data/parsedExpressionList.dat";
		boolean process = false;
		if(process){
			List<String> thmList = extractThmListFromPEList(peFilePath);
			//System.out.println("thmLIst "+thmList);		
			if(false) buildAndSerialize2GramMaps(thmList);
			if(false) buildAndSerialize23GramMaps(thmList);
			if(true) createDocWordFreqMap(thmList);
			if(false) buildAndSerializeTrueFluffWordsSet(thmList);
		}
		if(true) harnessCuratedWords();
	}	
	
	private static List<String> extractThmListFromPEList(String peFilePath){
		List<String> thmList = new ArrayList<String>();
		@SuppressWarnings("unchecked")
		List<ParsedExpression> peList = (List<ParsedExpression>)FileUtils.deserializeListFromFile(peFilePath);
		System.out.println("CreateRepresentativeMaps - peList.size() " + peList.size());
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
	 * The map is on the order of O(10^4) (May 2017)
	 * @return Map of words and their frequencies. Ordered in decreasing order 
	 * with respect to frequency.
	 */
	private static void createDocWordFreqMap(List<String> thmList){
		Map<String, Integer> wordFreqMap = CollectThm.ThmWordsMaps.buildDocWordsFreqMap(thmList);
		
		/*for(Map.Entry<String, Integer> entry : wordFreqMap.entrySet()){
			String normalizedForm = WordForms.normalizeWordForm(entry.getKey());
			wordFreqMap.put(normalizedForm, entry.getValue());
		}*/
		//write map to file for inspection
		FileUtils.writeToFile(wordFreqMap, "src/thmp/data/bigWordFreqMap.txt");
		List<Map<String, Integer>> wordFreqMapList = new ArrayList<Map<String, Integer>>();
		wordFreqMapList.add(wordFreqMap);
		FileUtils.serializeObjToFile(wordFreqMapList, "src/thmp/data/bigWordFreqMap.dat");
		System.out.println("wordFreqMap.size " + wordFreqMap.size());
		
		//split into different files for cursory human inspection
		int totalNumWords = wordFreqMap.size();
		Iterator<String> iter = wordFreqMap.keySet().iterator();
		int numWordsPerFile = 5000;
		int numRounds = totalNumWords/numWordsPerFile+1;
		String vocabFileBaseStr = "src/thmp/data/vocab/vocab";
		for(int j = 0; j < numRounds; j++){
			int startIndex = j*numWordsPerFile;
			int endIndex = Math.min((j+1)*numWordsPerFile, totalNumWords);
			String fileStr = vocabFileBaseStr + j +".txt";
			StringBuilder sb = new StringBuilder(5000);
			for(int i = startIndex; i < endIndex; i++){
				//carriage return instead of newline, for windows people
						/*sb.append(iter.next()).append("\r\n");*/
				sb.append(iter.next()).append("\n");
			}
			FileUtils.writeToFile(sb, fileStr);
		}				
	}	
	
	/**
	 * Combine, process, and serialize curated words.
	 */
	private static void harnessCuratedWords(){
		//list of filenames
		String[] srcFileNameAr = new String[]{"vocab0_edited.txt","vocab1_edited.txt",
				"vocab2_edited.txt","vocab3_edited.txt","vocab4_edited.txt",
				"vocab5_edited.txt","vocab6_edited.txt"};
		for(int i = 0; i < srcFileNameAr.length; i++){
			srcFileNameAr[i] = "src/thmp/data/vocab/" + srcFileNameAr[i];
		}
		List<String> curatedWordsList = FileUtils.readLinesFromFiles(Arrays.asList(srcFileNameAr));
		System.out.println("curatedWordsList.size "+curatedWordsList.size());
		@SuppressWarnings("unchecked")
		Map<String, Integer> bigWordFreqMap 
			= ((List<Map<String,Integer>>)FileUtils.deserializeListFromFile("src/thmp/data/bigWordFreqMap.dat")).get(0);
		
		bigWordFreqMap = new HashMap<String, Integer>(bigWordFreqMap);
		///		
		//TreeMap<String, Integer> tMap = (TreeMap<String, Integer>)bigWordFreqMap;
		//System.out.println("tMap.comparator()).wordFreqMap" + ((WordFreqComparator)tMap.comparator()).wordFreqMap );
		///
		
		///////////////////////////
		Map<String, Integer> prunedBigWordFreqMap = pruneWords(curatedWordsList, bigWordFreqMap);
		List<Map<String,Integer>> prunedBigWordFreqMapList = new ArrayList<>();
		prunedBigWordFreqMapList.add(prunedBigWordFreqMap);
		FileUtils.serializeObjToFile(prunedBigWordFreqMapList, "src/thmp/data/bigWordFreqPrunedMap.dat");
		FileUtils.writeToFile(prunedBigWordFreqMap, "src/thmp/data/bigWordFreqPrunedMap.txt");
	}
	
	/**
	 * remove duplicates, process words, match hand-selected words with
	 * their frequency in freqMap given, serialize resulting map.
	 * @param srcFileStrList
	 * @param bigWordFreqMap frequency map from which the curated words are derived.
	 */
	private static Map<String, Integer> pruneWords(List<String> curatedWordsList, Map<String, Integer> bigWordFreqMap){
		Map<String, Integer> prunedBigWordFreqMap = new HashMap<String, Integer>();
		//words not present. I.e. created in curation process.
		Set<String> wordsNotPresentSet = new HashSet<String>();
		int sum = 0;
		int count = 0;
		//System.out.println("curatedWordsList.size() "+curatedWordsList.size() + " first word "+ curatedWordsList.get(0));
		/*for(int i = 0; i < 100; i++){
			String word = curatedWordsList.get(i);
			System.out.println("\""+word+"\"");
			System.out.println(word.length());
		}*/
		System.out.println("bigWordFreqMap.size() "+bigWordFreqMap.size());
		
		/*System.out.println("entries to bigWordFreqMap:");
		int counter = 0;
		for(Map.Entry<String, Integer> entry : bigWordFreqMap.entrySet()){
			counter++;
			if(counter == 10) break;
			System.out.println("\""+entry.getKey()+"\"  " + entry.getValue());
		}
		System.out.println("Get with keys:");
		for(String key : bigWordFreqMap.keySet()){
			counter++;
			if(counter == 20) break;
			System.out.println("\""+key+"\"  " + bigWordFreqMap.get(key));
		}*/
		for(String word : curatedWordsList){
			if(WordForms.getWhiteEmptySpacePattern().matcher(word).matches()){
				continue;
			}
			Matcher m;
			if((m=NEWLINE_PATTERN.matcher(word)).matches()){
				word = m.group(1);
			}
			word = WordForms.stripSurroundingWhiteSpace(word);
			word = WordForms.normalizeWordForm(word);
			Integer freq = bigWordFreqMap.get(word);
			if(null != freq){
				prunedBigWordFreqMap.put(word, freq);
				sum += freq;
				count++;
			}else{
				wordsNotPresentSet.add(word);				
			}
		}
		if(count > 0){
			int avgFreq = sum/count;
			avgFreq = avgFreq == 0 ? 2 : avgFreq;
			for(String absentWord : wordsNotPresentSet){
				prunedBigWordFreqMap.put(absentWord, avgFreq);
			}	
			Collection<String> stemWordsMapVals = WordForms.deserializeStemWordsMap(null).values();
			for(String shortenedWord : stemWordsMapVals){
				if(!prunedBigWordFreqMap.containsKey(shortenedWord)){
					prunedBigWordFreqMap.put(shortenedWord, avgFreq);
				}
			}			
		}		
		System.out.println("CreateRepresentativeMaps - recognized word count "+count);
		return prunedBigWordFreqMap;		
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
		extract2Grams(thmList);		
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
				
		extract3Grams(thmList, twoGramTotalOccurenceMap);
	}

	/**
	 * Builds both two and three grams maps.
	 * @param thmList
	 */
	public static void buildAndSerialize23GramMaps(List<String> thmList){
		Map<String, Map<String, Integer>> twoGramTotalOccurenceMap = extract2Grams(thmList);		
		extract3Grams(thmList, twoGramTotalOccurenceMap);
	}

	/**
	 * @param thmList
	 * @param twoGramTotalOccurenceMap
	 */
	private static void extract3Grams(List<String> thmList,
			Map<String, Map<String, Integer>> twoGramTotalOccurenceMap) {
		Map<String, Integer> threeGramFreqMap = ThreeGramSearch
				.gatherAndBuild3GramsMap(thmList, twoGramTotalOccurenceMap);
		
		Map<String, Integer> tempThreeGramFreqMap = new HashMap<String, Integer>();
		for(Map.Entry<String, Integer> threeGramEntry : threeGramFreqMap.entrySet()){
			String singularForm = WordForms.getSingularForm(threeGramEntry.getKey());
			int singularFormLen = singularForm.length();
			//pretty horrible to do this here. But there should only be termporary, as the 
			//changes are propagated through different maps.
			if(singularFormLen > 4 && singularForm.substring(singularFormLen-4).equals("sery")){
				singularForm = singularForm.substring(0, singularFormLen-4) + "series";
			}else if(singularFormLen > 7 && singularForm.substring(singularFormLen-7).equals("matrice")){
				singularForm = singularForm.substring(0, singularFormLen-7) + "matrix";
			}			
			tempThreeGramFreqMap.put(singularForm, threeGramEntry.getValue());
		}
		threeGramFreqMap = tempThreeGramFreqMap;
		
		String threeGramsSerialPath = FileUtils.getPathIfOnServlet(SearchMetaData.threeGramsFreqMapPath());		
		List<Map<String, Integer>> threeGramFreqMapList = new ArrayList<Map<String, Integer>>();
		threeGramFreqMapList.add(threeGramFreqMap);
		FileUtils.serializeObjToFile(threeGramFreqMapList, threeGramsSerialPath);		
		//for inspection
		String threeGramsTxtPath = threeGramsSerialPath.substring(0, threeGramsSerialPath.length()-3) + "txt";
		FileUtils.writeToFile(threeGramFreqMap, threeGramsTxtPath);
	}

	/**
	 * @param thmList
	 * @return
	 */
	private static Map<String, Map<String, Integer>> extract2Grams(List<String> thmList) {
		//nGramMap Map of maps. Keys are words in text, and entries are maps whose keys are 2nd terms
		// in 2 grams, and entries are frequency counts.
		//this field will be exposed to build 3-grams, and so far only for that purpose.
		Map<String, Map<String, Integer>> twoGramTotalOccurenceMap 
			= new HashMap<String, Map<String, Integer>>();
		Map<String, Integer> twoGramFreqMap = NGramSearch.TwoGramSearch
				.gatherAndBuild2GramsMaps(thmList, twoGramTotalOccurenceMap);
		Map<String, Integer> tempTwoGramFreqMap = new HashMap<String, Integer>();
		for(Map.Entry<String, Integer> twoGram : twoGramFreqMap.entrySet()){
			String singularForm = WordForms.getSingularForm(twoGram.getKey());
			// /*one-off, But only termporary, as the 
			//changes are propagated through different maps.
			int singularFormLen = singularForm.length();
			if(singularFormLen > 4 && singularForm.substring(singularFormLen-4).equals("sery")){
				singularForm = singularForm.substring(0, singularFormLen-4) + "series";
			}else if(singularFormLen > 7 && singularForm.substring(singularFormLen-7).equals("matrice")){
				singularForm = singularForm.substring(0, singularFormLen-7) + "matrix";
			}
			//*/
			tempTwoGramFreqMap.put(singularForm, twoGram.getValue());
		}
		twoGramFreqMap = tempTwoGramFreqMap;
		String twoGramsSerialPath = FileUtils.getPathIfOnServlet(SearchMetaData.twoGramsFreqMapPath());
		List<Map<String, Integer>> twoGramFreqMapList = new ArrayList<Map<String, Integer>>();
		twoGramFreqMapList.add(twoGramFreqMap);
		FileUtils.serializeObjToFile(twoGramFreqMapList, twoGramsSerialPath);
		//for inspection
		String twoGramsTxtPath = twoGramsSerialPath.substring(0, twoGramsSerialPath.length()-3) + "txt";
		FileUtils.writeToFile(twoGramFreqMap, twoGramsTxtPath);
		return twoGramTotalOccurenceMap;
	}
}
