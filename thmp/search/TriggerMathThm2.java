package thmp.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;

import thmp.parse.Maps;
import thmp.parse.TheoremContainer;
import thmp.parse.ThmP1;
import thmp.parse.DetectHypothesis.DefinitionListWithThm;
import thmp.search.CollectThm.ThmList;
import thmp.search.SearchCombined.ThmHypPair;
import thmp.search.SearchWordPreprocess.WordWrapper;
import thmp.utils.FileUtils;
import thmp.utils.GatherRelatedWords;
import thmp.utils.GatherRelatedWords.RelatedWords;
import thmp.utils.WordForms;
import thmp.utils.WordForms.WordFreqComparator;

public class TriggerMathThm2 {

	/**
	 * Functionally equivalent to TriggerMathThm, but internally the term-thm map is built 
	 * differently. Instead of term -> theorems correspondence, we have theorem -> terms.
	 * Take inner product of trigger words and matrix of keywords. A column
	 * contains a set of words that trigger a particular MathObj, such as radius
	 * of convergence and root likely in column for function. And ideals for
	 * column for ring.
	 * 
	 * @author yihed
	 */

	/* List of mathObj's, in order they are inserted into mathObjMx */
	////private static final List<String> mathObjList;
	
	/* These three maps follow consistent ordering */
	/*private static final List<String> webDisplayThmList;
	private static final ImmutableList<String> webDisplayThmHypOnlyList;
	//private static final ImmutableList<String> webDisplayThmNoHypList;
	private static final ImmutableList<String> webDisplayThmSrcFileList;*/
	//////private static final ImmutableList<ThmHypPair> webDisplayThmHypPairList;
	
	/**
	 * Dictionary of keywords -> their index/row number in mathObjMx.
	 * ImmutableMap. Java indexing, starts at 0.
	 */
	private static final Map<String, Integer> keywordIndexDict;
	
	//private static final int LIST_INDEX_SHIFT = 1;
	/**
	 * Map of math objects, eg function, field, with list of keywords. don't
	 * need this either
	 */
	// private static final Multimap<String, String> mathObjMultimap;
	//private static final Pattern BRACES_PATTERN
	/* Matrix of keywords. */
	//private static final int[][] mathObjMx;
	///****private static double[][] mathObjMx;
	private static StringBuilder sparseArrayInputSB;

	private static final Logger logger = LogManager.getLogger(TriggerMathThm2.class);
	//list of thms, same order as in thmList, and the words with their frequencies.
	///private static final List<ImmutableMap<String, Integer>> thmWordsMapList;
	
	//private static final ImmutableMap<String, Integer> docWordsFreqMapNoAnno = CollectThm.ThmWordsMaps.get_docWordsFreqMapNoAnno();
	private static final ImmutableMap<String, Integer> docWordsFreqMapNoAnno;
	private static final Map<String, GatherRelatedWords.RelatedWords> relatedWordsMap;
	
	/**
	 * Debug variables
	 */
	private static final boolean DEBUG = false;
	private static final double RELATED_WORD_MULTIPLICATION_FACTOR = 4/5.0;
	private static final double DEFAULT_NORMALIZED_WORD_SCORE = 0.3;
	
	static {
		
		//////ImmutableList<String> thmList = CollectThm.ThmList.allThmsWithHypList();
		relatedWordsMap = CollectThm.ThmWordsMaps.getRelatedWordsMap();
		//relatedWordsMap = FileUtils.deserializeListFromFile("");
		//docWordsFreqMapNoAnno should already been ordered based on frequency, more frequently-
		//occuring words come earlier. This based on set of theorems in previous run.
		docWordsFreqMapNoAnno = CollectThm.ThmWordsMaps.get_docWordsFreqMapNoAnno();
		//***keywordIndexDict = ImmutableMap.copyOf(CollectThm.ThmWordsMaps.createContextKeywordIndexDict(docWordsFreqMapNoAnno));
		keywordIndexDict = CollectThm.ThmWordsMaps.get_CONTEXT_VEC_WORDS_INDEX_MAP();
		///thmWordsMapList = CollectThm.ThmWordsMaps.get_thmWordsFreqListNoAnno();
		
		/* Don't need to build the giant mathObjMx if not generating data.	*/	
		if(!Searcher.SearchMetaData.gatheringDataBool()){
			//re-order the list so the most frequent words appear first, as optimization
				//so that search words can match the most frequently-occurring words.
				/*WordFreqComparator comp = new WordFreqComparator(docWordsFreqMapNoAnno);
				//words and their frequencies in wordDoc matrix.
				Map<String, Integer> keyWordIndexTreeMap = new TreeMap<String, Integer>(comp);
				keyWordIndexTreeMap.putAll(keywordIndexMap);*/
				/*This has already taken into account whether we are generating data or performing search */
				
				/*if(Searcher.SearchMetaData.gatheringDataBool()){
					docWordsFreqMapNoAnno = CollectThm.ThmWordsMaps.get_docWordsFreqMapNoAnno();
				}else{
					docWordsFreqMapNoAnno = CollectThm.ThmWordsMaps.get_CONTEXT_VEC_WORDS_FREQ_MAP_fromData();
				}*/
			//get from serialized data
			//keywordIndexDict = ;	
			//mathObjMx = new double[keywordIndexDict.size()][thmWordsMapList.size()];
		}else{
		//List of theorems, each of which
		//contains map of keywords and their frequencies in this theorem.
		//thmWordsMapList = CollectThm.ThmWordsMaps.get_thmWordsFreqListNoAnno();
		
			// ImmutableList.Builder<String> keywordList = ImmutableList.builder();
			//List<String> keywordList = new ArrayList<String>();
		
		// which keyword corresponds to which index	in the keywords list
		// ImmutableMap.Builder<String, Integer> keyDictBuilder = ImmutableMap.builder();
		//map of String (keyword), and integer (index in keywordList) of keyword.
			//Map<String, Integer> keywordIndexMap = new HashMap<String, Integer>(); ///<--this is not necessary!
		//ImmutableList.Builder<String> mathObjListBuilder = ImmutableList.builder();
		//to be list that contains the theorems, in the order they are inserted
		//ImmutableList.Builder<String> mathObjListBuilder = ImmutableList.builder();
		
		//System.out.println("keywordList: "+ keywordList + " keywordIndexMap: "+ keywordIndexMap);
		//should already been ordered in CollectThm
		//Map<String, Integer> docWordsFreqMapNoAnno = CollectThm.ThmWordsMaps.get_docWordsFreqMapNoAnno();
		
			//**Commented out June 10, 2017
		// math object pre map. keys are theorems, values are keywords in that thm.
			//Multimap<String, String> mathObjMMap = ArrayListMultimap.create();
			//adds thms from CollectThm.thmWordsList. The thm name is its index in thmWordsList.
			//addThmsFromList(keywordList, keywordIndexMap, mathObjMMap);
			//follows order in keywordIndexDict
			//keywordList = new ArrayList<String>(keywordIndexDict.keySet());
		//keyword index map not needed
		
		//keywordIndexDict = ImmutableMap.copyOf(keywordIndexMap);
		
		//mathObjMx = new int[keywordList.size()][mathObjMMap.keySet().size()];	
		//mathObjMx = new int[keywordList.size()][thmWordsList.size()];
			//mathObjMx = new double[keywordList.size()][thmWordsMapList.size()];
			//System.out.println("TriggerMathThm2 - number of keywords: " + keywordList.size());
			//System.out.println("TriggerMathThm2 - number of theorems deserialized (i.e in previous round): " 
					//+ thmWordsMapList.size());
			//List<int[]> coordinatesList = new ArrayList<int[]>();
			//List<Double> weightsList = new ArrayList<Double>();
		//pass in thmList to ensure the right order (insertion order) of thms 
		//is preserved or MathObjList and mathObjMMap. Multimaps don't preserve insertion order
			/*should just pass in mathObjMMap!! */
			/*buildMathObjMx is filled in already.*/
			/*buildMathObjMx(mathObjMMap, thmList, coordinatesList, weightsList); 
			//mathObjMx now presented as sparse array
			sparseArrayInputSB = constructSparseArrayInputString(coordinatesList, weightsList);*/
		}
		
		//mathObjList = ImmutableList.copyOf(thmList);
		
		/*webDisplayThmList = CollectThm.ThmList.allThmsWithHypList();
		webDisplayThmHypOnlyList = CollectThm.ThmList.allHypList();
		webDisplayThmSrcFileList = CollectThm.ThmList.allThmSrcFileList();*/
		//webDisplayThmNoHypList = CollectThm.ThmList.allThmsNoHypList();
		////////webDisplayThmHypPairList = CollectThm.ThmList.allThmHypPairList();
		/*for(int i = 0; i < thmWordsList.size(); i++){
			System.out.println(mathObjList.get(i));
			System.out.println(thmWordsList.get(i));
		}*/
	}	
	
	/**
	 * Add keyword/term to mathObjList, in the process of building keywordList, mathObjMMap, etc.
	 * MathObj's are theorems.
	 * @param keywords
	 * @param keywordList
	 * @param keywordIndexMap
	 * @param mathObjMMap
	 */
	private static void addKeywordToMathObj(String[] keywords, List<String> keywordList,
			Map<String, Integer> keywordIndexMap, Multimap<String, String> mathObjMMap
			) {
		
		if (keywords.length == 0){
			return;		
		}
		
		//theorem
		String thm = keywords[0];
		//System.out.println("THM " + thm);
		for(int i = 1; i < keywords.length; i++){
			String keyword = keywords[i];
			mathObjMMap.put(thm, keyword); //version with tex, unprocessed
			//add each keyword in. words should already have annotation.
			if(!keywordIndexMap.containsKey(keyword)){
				keywordIndexMap.put(keyword, keywordList.size());
				keywordList.add(keyword);
			}			
		}		
		//System.out.println("AFTER mathObjMMap" + mathObjMMap);
		/*keyDictBuilder.put(keyword, keywordList.size());
		//*keyDictBuilder.put(keyword, keywordList.size());
		//System.out.println("Building: " + keyword);
		
		keywordList.add(keywords[0]);		
		
		for (int i = 1; i < keywords.length; i++) {
			//keys are theorems, values are keywords
			mathObjMMap.put(keywords[i], keyword);
			//mathObjMMap.put(thm, keywords[i]);
		}*/
	}
	
	/**
	 * Adds thms by calling addKeywordToMathObj on CollectThm.thmWordsList.
	 * Weighed by frequencies.
	 * @deprecated
	 * @param keywords
	 * @param keywordList
	 * @param keywordIndexMap Empty map to be filled. Words and their indices in keywordList.
	 * @param mathObjMMap Multimap of theorems and their words, key should not be thm!!
	 */
	/*private static void addThmsFromList(List<String> keywordList,
			Map<String, Integer> keywordIndexMap, Multimap<String, String> mathObjMMap
			){
		//thmWordsList has annotations, such as hyp or stm
		//ImmutableList<ImmutableMap<String, Integer>> thmWordsList = CollectThm.get_thmWordsListNoAnno();
		//System.out.println("---thmWordsList " + thmWordsList);
		ImmutableList<String> thmList = CollectThm.ThmList.allThmsWithHypList();
		//System.out.println("---thmList " + thmList);
		
		//index of thm in thmWordsList, to be used as part of name
		//thm words and their frequencies.
		int thmIndex = 0;
		//key is thm, values are indices of words that show up in thm. Use list of list instead!
		for(ImmutableMap<String, Integer> wordsMap : thmWordsMapList){			
			//make whole thm text as name <--should make the index the key!			
			String thmName = thmList.get(thmIndex++);
			List<String> keyWordsList = new ArrayList<String>();
			keyWordsList.add(thmName);
			keyWordsList.addAll(wordsMap.keySet());
			//System.out.println("wordsMap " +wordsMap);
			//thms added to mathObjMMap are *not* in the same order as thms are iterated here! Because Multimaps don't 
			//need to preserve insertion order!				
			addKeywordToMathObj(keyWordsList.toArray(new String[keyWordsList.size()]), keywordList, keywordIndexMap, mathObjMMap);
		}
	}*/
	
	/**
	 * e.g. {{1, 2}, {2, 3}} -> {4, 1}
	 * @param coordinatesList
	 * @param weightsList
	 * @return without trailing ';'
	 */
	private static StringBuilder constructSparseArrayInputString(List<int[]> coordinatesList, List<Double> weightsList){
		//should pack these!
		StringBuilder sparseArraySB = new StringBuilder(80000);
		sparseArraySB.append('{');
		//append coordinates
		for(int[] coordinatePair : coordinatesList){
			assert coordinatePair.length == 2 : "Length of coordinate pair array must be 2!";
			sparseArraySB.append('{');
			sparseArraySB.append(coordinatePair[0]).append(',').append(coordinatePair[1]);
			sparseArraySB.append("},");
		}
		sparseArraySB = sparseArraySB.deleteCharAt(sparseArraySB.length()-1);
		sparseArraySB.append('}');
		
		//append weights
		//change the first and last char's, which are brackets.
		//weightsList.toString();
		sparseArraySB.append("->{");
		//turn the weightsList to a string
		for(double d : weightsList){
			sparseArraySB.append(d).append(",");
		}
		sparseArraySB = sparseArraySB.deleteCharAt(sparseArraySB.length()-1);
		sparseArraySB.append('}');
		return sparseArraySB;
	}
	
	private static void gatherTermDocumentMxEntries(List<? extends TheoremContainer> defThmList,
			List<int[]> coordinatesList, List<Double> weightsList) {
		//map of annotated words and their scores. Previous run's scores
		Map<String, Integer> wordsScoreMap = CollectThm.ThmWordsMaps.get_wordsScoreMapNoAnno();
		gatherTermDocumentMxEntries(defThmList, coordinatesList, weightsList, wordsScoreMap);	
	}
	
	/**
	 * Gather the TermDocumentMatrix entries to be turned into SparseArray.
	 * Weigh inversely based on word frequencies extracted from 
	 * CollectThm.java.
	 * Use given list of words and given list of thms, in that precise order!
	 * Be careful about thm ordering used! Since col index in term document mx depends on it!
	 * 
	 * @param defThmList
	 * @param coordinatesList
	 * @param weightsList Should be empty on input. To record list of scores for the words
	 * added, for each thm.
	 * @param wordsScoreMap
	 */
	private static void gatherTermDocumentMxEntries(List<? extends TheoremContainer> defThmList,
			List<int[]> coordinatesList, List<Double> weightsList, Map<String, Integer> wordsScoreMap) {
		
		Iterator<? extends TheoremContainer> defThmListIter = defThmList.iterator();
		
		//list of coordinate pairs containing non zero, used to construct sparse array.
		//each array has size 2.
		//List<int[]> coordinatesList = new ArrayList<int[]>(); //<--put in argument!
		//List<Double> weightsList = new ArrayList<Double>();
		//int[][] d = new int[mathObjMx.length][2];
		
		int thmCounter = 0;
		//int keywordIndexNullCounter = 0;
		while (defThmListIter.hasNext()) {
			String thm = defThmListIter.next().getEntireThmStr();
			//get collection of words.
			//String[] curMathObjCol = WordForms.splitThmIntoSearchWords(thm.toLowerCase());
			String[] thmAr = WordForms.splitThmIntoSearchWords(thm.toLowerCase());
			
			//int queryVecLen = ThmSearch.ThmSearchQuery.getQUERY_VEC_LENGTH();
			int queryVecLen = TriggerMathThm2.mathThmMxRowDim();
			//logger.info("TriggerMathThm - queryVecLen: " + queryVecLen);
			//System.out.println("TriggerMathThm2 - query vector length: " + queryVecLen);
			//double[] queryVec = new double[queryVecLen];
			double norm = 0;
			//highest weight amongst the single words
			double highestWeight = 0;
			//List<Integer> priorityWordsIndexList = new ArrayList<Integer>();
			List<IndexScorePair> indexScorePairList = new ArrayList<IndexScorePair>();
			//get norm first, form unnormalized vector, then divide terms by log of norm
			for (int i = 0; i < thmAr.length; i++) {
				String term = thmAr[i];
				double newNorm;
				//this also normalizes the word
				newNorm = addToNorm(wordsScoreMap, indexScorePairList, norm, i, term, queryVecLen);
				if(newNorm - norm > highestWeight){
					highestWeight = newNorm - norm;
				}
				//search 2 & 3-grams
				if(i < thmAr.length-1){
					String nextTermCombined = term + " " + thmAr[i+1];
					nextTermCombined = WordForms.normalizeTwoGram(nextTermCombined);
					newNorm = addToNorm(wordsScoreMap, indexScorePairList, newNorm, i, nextTermCombined, queryVecLen);	
					//System.out.println("combined word: " + nextTermCombined + ". norm: " + newNorm);
					
					if(i < thmAr.length-2){
						String threeTermsCombined = nextTermCombined + " " + thmAr[i+2];
						newNorm = addToNorm(wordsScoreMap, indexScorePairList, newNorm, i, threeTermsCombined, queryVecLen);
					}
				}
				norm = newNorm;
			}			
			//****************************
			/*June 2017 double norm = 0;
			for (String keyword : curMathObjCol) {				
				if(!keywordIndexDict.containsKey(keyword)){
					keyword = WordForms.getSingularForm(keyword);
					if(!keywordIndexDict.containsKey(keyword)){
						keyword = WordForms.normalizeWordForm(keyword);						
						if(!keywordIndexDict.containsKey(keyword)){
							continue;
						}
					}					
				}				
				gatheredWordsList.add(keyword);
				//Integer keyWordIndex = keywordDict.get(keyword);
				Integer wordScore = wordsScoreMap.get(keyword);
				if(null == wordScore){
					//wordsScoreMap is now from previous parse run, so might not have this word.
					//if the dataset has not stabilized across runs. <--actually, now uses same
					//set of words. June 2017.
					wordScore = 1;
				}
				norm += Math.pow(wordScore, 2);
				//should not be null, as wordsScoreMap was created using same list of thms.
				//if(wordScore == null) continue;
				//weigh each word based on *local* frequency, ie word freq in sentence, not whole doc.
				//mathObjMx[keyWordIndex][mathObjCounter] = wordScore;
			}*/ ///*********************
			
			//Sqrt is usually too large, so take log instead of sqrt.
			//norm = norm < 3 ? 1 : (int)Math.sqrt(norm);
			//This *must* be consistent with forming query vectors.
			/**Experiment June 26, 2017 norm = Math.sqrt(Math.sqrt(norm)); 
			norm = norm == 0 ? 1 : norm;*/
			norm  = 1;
			for(IndexScorePair pair : indexScorePairList){
				int keyWordIndex = pair.index;
				double keyWordScore = pair.score;
				double newScore = keyWordScore/norm; //need to be conssitent
				if(0. == newScore){
					//factor out constatn after experimentation !
					newScore = DEFAULT_NORMALIZED_WORD_SCORE;
				}
				coordinatesList.add(new int[]{keyWordIndex, thmCounter});
				weightsList.add(newScore);
			}
			
			///***********
			/**** June 2017
			 * for (String keyword : gatheredWordsList) {
				Integer keyWordIndex = keywordIndexDict.get(keyword);
				//keywordIndexDict should contain keyword at this point.
				if(null == keyWordIndex){
					//keywordIndexNullCounter++;
					continue;
				}
				Integer wordScore = wordsScoreMap.get(keyword);
				if(null == wordScore){
					//wordsScoreMap is now from previous parse run, so might not have this word.
					//if the dataset has not stabilized across runs.
					wordScore = 1;
				}
				//divide by log of norm
				//could be very small! ie 0 after rounding.
				double newScore = ((double)wordScore)/norm;
				//int newScore = wordScore;
				if(newScore == 0 && wordScore != 0){
					newScore = .1;
				}
				coordinatesList.add(new int[]{keyWordIndex, thmCounter}) ;
				weightsList.add(newScore);
			}*/
			thmCounter++;
		}
	}
	
	/**
	 * Same as creareQuery, no annotation.
	 * Create query row vector, weighed using word scores,
	 * and according to norm. To be compared to columns 
	 * of term document matrix.
	 * @param thm
	 * @return
	 */
	public static String createQueryNoAnno(String thm){
		//System.out.println("TriggerMathThm - keywordIndexDict map: " + keywordIndexDict);
		//String[] thmAr = thm.split("\\s+|,|;|\\.");
		//***String[] thmAr = thm.split(WordForms.splitDelim());
		String[] thmAr = WordForms.splitThmIntoSearchWords(thm);
		////////////////
		//map of non-annotated words and their scores. Use get_wordsScoreMapNoAnno 
		//and not CONTEXT_VEC_WORDS_MAP.
		Map<String, Integer> wordsScoreMap = CollectThm.ThmWordsMaps.get_wordsScoreMapNoAnno();	
		//System.out.println("wordsScoreMap inside TriggerMathThm: " + wordsScoreMap);
		//should eliminate unnecessary words first, then send to get wrapped.
		//<--can only do that if leave the hyp words in, eg if.
		
		//***String priorityWords = ConstantsInSearch.get_priorityWords();
		//keywordDict is annotated with "hyp"/"stm"
		//int dictSz = keywordIndexDict.keySet().size();
		int queryVecLen = ThmSearch.ThmSearchQuery.getQUERY_VEC_LENGTH();
		logger.info("TriggerMathThm - queryVecLen: " + queryVecLen);
		//System.out.println("TriggerMathThm2 - query vector length: " + queryVecLen);
		double[] queryVec = new double[queryVecLen];
		double norm = 0;
		//highest weight amongst the single words
		double highestWeight = 0;
		//***List<Integer> priorityWordsIndexList = new ArrayList<Integer>();
		List<IndexScorePair> indexScorePairList = new ArrayList<IndexScorePair>();
		//get norm first, form unnormalized vector, then divide terms by log of norm
		for (int i = 0; i < thmAr.length; i++) {
			String term = thmAr[i];
			double newNorm;
			//this also normalizes the word
			newNorm = addToNorm(wordsScoreMap, indexScorePairList, norm, i, term, queryVecLen);
			if(newNorm - norm > highestWeight){
				highestWeight = newNorm - norm;
			}
			//search 2 & 3-grams
			if(i < thmAr.length-1){
				String nextTermCombined = term + " " + thmAr[i+1];
				nextTermCombined = WordForms.normalizeTwoGram(nextTermCombined);
				newNorm = addToNorm(wordsScoreMap, indexScorePairList, newNorm, i, nextTermCombined, queryVecLen);	
				//System.out.println("combined word: " + nextTermCombined + ". norm: " + newNorm);
				
				if(i < thmAr.length-2){
					String threeTermsCombined = nextTermCombined + " " + thmAr[i+2];
					newNorm = addToNorm(wordsScoreMap, indexScorePairList, newNorm, i, threeTermsCombined, queryVecLen);
				}
			}						
			norm = newNorm;
		}
		//short-circuit if no relevant term was detected in input thm
		//rather than return a list of results that are close to the 0-vector
		//but doesn't make sense.
		if(norm == 0) return "";
		
		for(IndexScorePair pair : indexScorePairList){
			queryVec[pair.index] = pair.score;			
		}
		
		//fill in the priority words with highestWeight
		/*//*****for(int index : priorityWordsIndexList){
			Integer rowIndex = keywordIndexDict.get(thmAr[index]);
			if(rowIndex != null){
				double prevWeight = queryVec[rowIndex];
				double weightDiff = highestWeight - prevWeight;
				queryVec[rowIndex] = highestWeight;
				norm += weightDiff;
			}
		}*/		
		//avoid division by 0 apocalypse, when it was log
		
		//norm = norm < 3 ? 1 : (int)Math.log(norm);
		/**Experiment June 2017 norm = Math.sqrt(Math.sqrt(norm));
		norm = norm == 0 ? 1 : norm;*/
		
		norm  = 1;
		//divide entries of triggerTermsVec by norm
		for(int i = 0; i < queryVec.length; i++){
			double prevScore = queryVec[i]; 
			if(0 != prevScore){			
				queryVec[i] = prevScore/norm;				
				//avoid completely obliterating a word 
				if(queryVec[i] == 0){
					//triggerTermsVec[i] = 1;
					queryVec[i] = DEFAULT_NORMALIZED_WORD_SCORE;
				}
			}
		}		
		//transform into query list String 
		StringBuilder sb = new StringBuilder();
		sb.append("{{");
		for(int j = 0; j < queryVecLen; j++){
			String t = j == queryVecLen-1 ? queryVec[j] + "" : queryVec[j] + ", ";
			sb.append(t);
		}
		sb.append("}}");
		//System.out.println("query vector " + sb);
		return sb.toString();
	}
	
	/**
	 * Index in query vector, ie vector with length the number of keywords,
	 * and value is the score;
	 */
	private static class IndexScorePair{
		int index;
		double score;
		IndexScorePair(int index_, double score_){
			this.index = index_;
			this.score = score_;
		}
		int index(){
			return this.index;
		}
		
		double score(){
			return this.score;
		}
	}
	
	/**
	 * Auxiliary method to createQueryNoAnno. Adds word weight
	 * to norm, when applicable.
	 * 
	 * @param wordsScoreMap
	 * @param triggerTermsVec
	 * @param indexScoreArList List of arrays of two, index, and score.
	 * @param norm
	 * @param i
	 * @param term
	 * @param queryVecLen
	 * @return
	 */
	private static double addToNorm(Map<String, Integer> wordsScoreMap, //String[] thmAr, 
			List<IndexScorePair> indexScorePairList,
			double norm, int i, String term, int queryVecLen) {
		Integer termScore = wordsScoreMap.get(term);
		//get singular forms		
		if(termScore == null){
			term = WordForms.getSingularForm(term);
			termScore = wordsScoreMap.get(term);
		}
		//expand this to differentiate between synoynms and antonyms?! to have different scores
		List<String> termRelatedWordsList = null;
		//check for related words! Increase the scores for related words & antonyms,
		//not as high, maybe half the score.
		boolean relatedWordsFound = false;
		if(null != termScore){	
			RelatedWords relatedWords = relatedWordsMap.get(term);
			if(null != relatedWords){
				termRelatedWordsList = relatedWords.getCombinedList();
				relatedWordsFound = true;
			}			
		}
		else{		
			term = WordForms.normalizeWordForm(term);
			termScore = wordsScoreMap.get(term);			
		}		
		//if no entry in relatedWordsMap found above, check again
		if(null != termScore && !relatedWordsFound){
			RelatedWords relatedWords = relatedWordsMap.get(term);
			if(null != relatedWords){
				termRelatedWordsList = relatedWords.getCombinedList();
			}	
		}		
		//triggerTermsVec[rowIndex] = termScore;
		/*keywordDict starts indexing from 0!*/
		Integer rowIndex = keywordIndexDict.get(term);				
		//it's possible that rowIndex >= queryVecLen
		if(termScore != null && rowIndex != null && rowIndex < queryVecLen){
			//just adding the termscore, without squaring, works better
			//norm += Math.pow(termScore, 2);
			norm += Math.pow(termScore, 2);
			///why this??
			//***thmAr[i] = term;
			//shouldn't be null, since termScore!=null
			//int rowIndex = keywordDict.get(term);
			//triggerTermsVec[rowIndex] = termScore;
			//keywordDict starts indexing from 0!
			//****triggerTermsVec[rowIndex] = termScore;
			indexScorePairList.add(new IndexScorePair(rowIndex, termScore));
			//System.out.println("TriggerMathThm2.java: term just added: " + term + ". " + rowIndex 
				//	+ ". termScore: " + termScore);
		}
		//add vector entries for related words
		if(null != termRelatedWordsList){
			//this multiplication factor is in experimentation
			Double relatedWordScore = termScore == null ? 0 : termScore*RELATED_WORD_MULTIPLICATION_FACTOR;				
			for(String relatedWord : termRelatedWordsList){
				//relatedWordScore = relatedWordScore == 0 ? wordsScoreMap.get(relatedWord) : relatedWordScore;
				if(0 == relatedWordScore){
					if(wordsScoreMap.containsKey(relatedWord)){
						relatedWordScore = wordsScoreMap.get(relatedWord)*RELATED_WORD_MULTIPLICATION_FACTOR;
					}else{
						continue;
					}
				}
				//the key & related words should have *already* been normalized,
				//when getting deserialized, to use consistent set of words as keywordIndexDict.
				Integer relatedWordRowIndex = keywordIndexDict.get(relatedWord);
				if(relatedWordRowIndex >= queryVecLen){
					continue;
				}
				if(null != relatedWordRowIndex){
					//triggerTermsVec[relatedWordRowIndex] = relatedWordScore;
					indexScorePairList.add(new IndexScorePair(relatedWordRowIndex, relatedWordScore));
					norm += Math.pow(relatedWordScore, 2);
				}					
			}
		}		
		return norm;
	}

	/**
	 * Obtains mathThmMx.
	 * @nullable
	 * @return
	 */
	/*public static double[][] mathThmMx(){
		return mathObjMx;
	}*/
	
	/**
	 * Number of words used in term-doc mx.
	 * @return
	 */
	public static int mathThmMxRowDim(){
		return keywordIndexDict.size();
	}
	
	/**
	 * Number of thms used in term-doc mx.
	 * @return
	 */
	/*public static int mathThmMxColDim(){
		return ThmList.numThms();
	}*/
	
	/**
	 * String representation of term-document matrix, .
	 * @return
	 */
	public static StringBuilder sparseArrayInputSB(){
		return sparseArrayInputSB;
	}
	
	public static StringBuilder sparseArrayInputSB(List<? extends TheoremContainer> thmList){
		
		List<int[]> coordinatesList = new ArrayList<int[]>();
		List<Double> weightsList = new ArrayList<Double>();		
		gatherTermDocumentMxEntries(thmList, coordinatesList, weightsList);
		/*mathObjMx now presented as sparse array*/		
		return constructSparseArrayInputString(coordinatesList, weightsList);
	}
	
	/**
	 * @param thmList
	 * @param docWordsFreqMap Use specified frequency map to re-create scores.
	 * @return
	 */
	public static StringBuilder sparseArrayInputSB(List<? extends TheoremContainer> thmList,
			Map<String, Integer> docWordsFreqMap){
		
		List<int[]> coordinatesList = new ArrayList<int[]>();
		List<Double> weightsList = new ArrayList<Double>();
		Map<String, Integer> wordsScoreMap = new HashMap<String, Integer>();
		
		CollectThm.ThmWordsMaps.buildScoreMapNoAnno(wordsScoreMap, docWordsFreqMap);			
		gatherTermDocumentMxEntries(thmList, coordinatesList, weightsList, wordsScoreMap);
		
		/*mathObjMx now presented as sparse array*/	
		return constructSparseArrayInputString(coordinatesList, weightsList);
	}
	
	/**
	 * Map of words to their corresponding row index in term-document matrix.
	 * This has been ordered, such that more frequently used words fall to the 
	 * beginning when iterating through the map.
	 * This is ordered version of CollectThm.ThmWordsMaps.get_docWordsFreqMapNoAnno().
	 * @return
	 */
	/*public static Map<String, Integer> allThmsKeywordIndexDict(){
		return keywordIndexDict;
	}*/

	public static int keywordDictSize(){
		return keywordIndexDict.size();
	}	
	
	/**
	 * words and their indices in term document matrix (mathObjMx). Same set of words 
	 * as those in docWordsMx   noanno
	 * @return
	 */
	public static Map<String, Integer> keywordIndexDict(){
		return keywordIndexDict;
	}
	
	/**
	 * Get theorem given its index (column number) in mathThmMx. Not the displayed web version.
	 * @param index 0-based index
	 * @return
	 */
	/*public static String getThm(int index){
		System.out.print("Thm index: " + index + "\t");
		//index is 1-based indexing, not 0-based.
		
		return mathObjList.get(index);
	}*/
	
	/**
	 * Get theorem given its index (column number) in mathThmMx. For web display.
	 * without \index, \label etc.
	 * @param index 0-based index
	 * @return
	 */
	/*public static String getWebDisplayThm(int index){
		System.out.print("Thm index: " + index + "\t");
		//index is 1-based indexing, not 0-based.
		return webDisplayThmHypPairList.get(index).hypStr() + " " 
				+ webDisplayThmHypPairList.get(index).thmStr();
	}*/
	
	/**
	 * Get hypotheses only for thm with index index.
	 * @param index 0-based index
	 * @return
	 */
	/***public static String getWebDisplayThmHypOnly(int index){
		//System.out.print("Thm index: " + index + "\t");
		return webDisplayThmHypPairList.get(index).hypStr();
	}*/
	
	/**
	 * Get theorem string only with index index, without hyp.
	 * @param index 0-based index
	 * @return
	 */
	/*public static String getWebDisplayThmNoHyp(int index){
		//System.out.print("Thm index: " + index + "\t");
		return webDisplayThmHypPairList.get(index).thmStr();
	}
	
	public static String getWebDisplayThmSrcFile(int index){
		return webDisplayThmHypPairList.get(index).srcFileName();
	}*/
	
	/*public static ThmHypPair getWedDisplayThmHypPair(int index){
		return webDisplayThmHypPairList.get(index);
	}*/
	/*public static void main(String[] args){
		System.out.print(keywordDict.size());
	}*/
}
