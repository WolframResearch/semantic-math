package thmp.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;

import thmp.ThmP1;
import thmp.search.SearchWordPreprocess.WordWrapper;
import thmp.utils.WordForms;

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
	 *
	 */

	/**
	 * List of keywords, eg radius, convergence, root. Don't actually need this
	 * after building
	 */
	// private static final List<String> keywordList;

	/**
	 * List of mathObj's, in order they are inserted into mathObjMx
	 */
	private static final List<String> mathObjList;
	
	/**
	 * Dictionary of keywords -> their index/row number in mathObjMx.
	 * ImmutableMap. Java indexing, starts at 0.
	 */
	private static final Map<String, Integer> keywordDict;
	
	/**
	 * Map of math objects, eg function, field, with list of keywords. don't
	 * need this either
	 */
	// private static final Multimap<String, String> mathObjMultimap;
	
	/**
	 * Matrix of keywords.
	 * 
	 */
	private static final int[][] mathObjMx;
	private static final List<ImmutableMap<String,Integer>> thmWordsList;
	private static final ImmutableMap<String, Integer> docWordsFreqMapNoAnno = CollectThm.ThmWordsMaps.get_docWordsFreqMapNoAnno();
	
	/**
	 * Debug variables
	 */
	private static final boolean showWordScore = false;
	
	static {
		thmWordsList = CollectThm.ThmWordsMaps.get_thmWordsListNoAnno();
		// ImmutableList.Builder<String> keywordList = ImmutableList.builder();
		List<String> keywordList = new ArrayList<String>();
		
		// which keyword corresponds to which index	in the keywords list
		//ImmutableMap.Builder<String, Integer> keyDictBuilder = ImmutableMap.builder();
		//map of String (keyword), and integer (index in keywordList) of keyword.
		Map<String, Integer> keywordMap = new HashMap<String, Integer>();
		//ImmutableList.Builder<String> mathObjListBuilder = ImmutableList.builder();
		//to be list that contains the theorems, in the order they are inserted
		ImmutableList.Builder<String> mathObjListBuilder = ImmutableList.builder();
		// math object pre map. keys are theorems, values are keywords.
		Multimap<String, String> mathObjMMap = ArrayListMultimap.create();

		// first String should be theorem, the rest are key words of this theorem 
		// belongs to
		//addKeywordToMathObj(new String[] { "fundamental theorem of algebra", "polynomial", "degree", "root", "complex"}, keywordList, keywordMap, mathObjMMap);
		//addKeywordToMathObj(new String[] { "Pythagorean theorem", "triangle", "right", "length", "square"}, keywordList, keywordMap, mathObjMMap);
		//addKeywordToMathObj(new String[] { "quadratic extension", "degree", "field", "square", "root"}, keywordList, keywordMap, mathObjMMap);
		
		// should be "thm", "term1", "term2", etc
		//addKeywordToMathObj(new String[] { "Godel's incompleteness theorem", "arithmetic", "incomplete"}, keywordList, keywordMap, mathObjMMap);
		
		//adds thms from CollectThm.thmWordsList. The thm name is its index in thmWordsList.
		addThmsFromList(keywordList, keywordMap, mathObjMMap);
		
		keywordDict = ImmutableMap.copyOf(keywordMap);
		
		//mathObjMx = new int[keywordList.size()][mathObjMMap.keySet().size()];	
		mathObjMx = new int[keywordList.size()][thmWordsList.size()];	
		ImmutableList<String> thmList = CollectThm.get_thmList();
		
		//System.out.println("BEFORE mathObjMMap" +mathObjMMap);
		//pass in thmList to ensure the right order (insertion order) of thms 
		//is preserved or MathObjList and mathObjMMap. Multimaps don't preserve insertion order
		buildMathObjMx(keywordList, mathObjMMap, mathObjListBuilder, thmList);

		mathObjList = mathObjListBuilder.build();
		//System.out.println("===");
		
		/*for(int i = 0; i < thmWordsList.size(); i++){
			System.out.println(mathObjList.get(i));
			System.out.println(thmWordsList.get(i));
		}*/
	}
	
	/**
	 * Add keyword/term to mathObjList, in the process of building keywordList, mathObjMMap, etc.
	 * @param keywords
	 * @param keywordList
	 * @param keywordMap
	 * @param mathObjMMap
	 */
	public static void addKeywordToMathObj(String[] keywords, List<String> keywordList,
			Map<String, Integer> keywordMap, Multimap<String, String> mathObjMMap) {
		if (keywords.length == 0)
			return;
		
		//String keyword = keywords[0];
		//theorem
		String thm = keywords[0];
		//System.out.println("THM " + thm);
		for(int i = 1; i < keywords.length; i++){
			String keyword = keywords[i];
			mathObjMMap.put(thm, keyword); //version with tex, unprocessed
			//add each keyword in. words should already have annotation.
			if(!keywordMap.containsKey(keyword)){
				keywordMap.put(keyword, keywordList.size());
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
	 * @param keywords
	 * @param keywordList
	 * @param keywordMap
	 * @param mathObjMMap
	 */
	private static void addThmsFromList(List<String> keywordList,
			Map<String, Integer> keywordMap, Multimap<String, String> mathObjMMap){
		//thmWordsList has annotations, such as hyp or stm
		//ImmutableList<ImmutableMap<String, Integer>> thmWordsList = CollectThm.get_thmWordsListNoAnno();
		//System.out.println("---thmWordsList " + thmWordsList);
		ImmutableList<String> thmList = CollectThm.get_thmList();
		//System.out.println("---thmList " + thmList);
		
		//index of thm in thmWordsList, to be used as part of name
		int thmIndex = 0;
		for(ImmutableMap<String, Integer> wordsMap : thmWordsList){
			//make whole thm text as name
			String thmName = thmList.get(thmIndex++);
			//System.out.println("!thmName " +thmName);
			List<String> keyWordsList = new ArrayList<String>();
			keyWordsList.add(thmName);
			keyWordsList.addAll(wordsMap.keySet());
			//System.out.println("wordsMap " +wordsMap);
			//thms added to mathObjMMap are *not* in the same order as thms are iterated here!! Cause Multimaps don't 
			//need to preserve insertion order!
			addKeywordToMathObj(keyWordsList.toArray(new String[keyWordsList.size()]), keywordList, keywordMap, mathObjMMap);
		}
		//System.out.println("!!mathObjMMap " + mathObjMMap);
	}
	
	/**
	 * Builds the MathObjMx. Weigh inversely based on word frequencies extracted from 
	 * CollectThm.java.
	 */
	private static void buildMathObjMx(List<String> keywordList, Multimap<String, String> mathObjMMap,
			ImmutableList.Builder<String> mathObjListBuilder, ImmutableList<String> thmList) {
		
		//map of annotated words and their scores
		Map<String, Integer> wordsScoreMap = CollectThm.ThmWordsMaps.get_wordsScoreMapNoAnno();
		
		//Iterator<String> mathObjMMapkeysIter = mathObjMMapkeys.iterator();
		Iterator<String> thmListIter = thmList.iterator();
		
		int mathObjCounter = 0;
		
		while (thmListIter.hasNext()) {
			String thm = thmListIter.next();
			//String curMathObj = mathObjMMapkeysIter.next();
			//System.out.println("BUILDING mathObjList " +curMathObj);
			mathObjListBuilder.add(thm);
			Collection<String> curMathObjCol = mathObjMMap.get(thm);
			//Iterator<String> curMathObjColIter = curMathObjCol.iterator();
			int norm = 0;
			for (String keyword : curMathObjCol) {
				//Integer keyWordIndex = keywordDict.get(keyword);
				Integer wordScore = wordsScoreMap.get(keyword);
				norm += Math.pow(wordScore, 2);
				//should not be null, as wordsScoreMap was created using same list of thms.
				//if(wordScore == null) continue;
				//weigh each word based on *local* frequency, ie word freq in sentence, not whole doc.
				//mathObjMx[keyWordIndex][mathObjCounter] = wordScore;
			}
			//Sqrt is usually too large, so take log instead of sqrt.
			norm = norm < 3 ? 1 : (int)Math.log(norm);
			
			//divide by log of norm
			for (String keyword : curMathObjCol) {
				Integer keyWordIndex = keywordDict.get(keyword);
				Integer wordScore = wordsScoreMap.get(keyword);
				//divide by log of norm
				//could be very small! ie 0 after rounding.
				int newScore = wordScore/norm;
				if(newScore == 0 && wordScore != 0){
					mathObjMx[keyWordIndex][mathObjCounter] = 1;
				}else{
					mathObjMx[keyWordIndex][mathObjCounter] = newScore;
				}
			}
			mathObjCounter++;
		}
		//System.out.println("~~keywordDict "+keywordDict);
	}

	/**
	 * Create query row vector, 1's and 0's.
	 * @deprecated SVD queries should be created without word annotations.
	 * @param triggerTerms
	 * @return String representation of query vector, eg {{1,0,1,0}}
	 */
	public static String createQuery(String thm){
		
		//String[] thmAr = thm.split("\\s+|,|;|\\.");
		//map of annotated words and their scores
		Map<String, Integer> wordsScoreMap = CollectThm.ThmWordsMaps.get_wordsScoreMapNoAnno();		
		//should eliminate unnecessary words first, then send to get wrapped.
		//<--can only do that if leave the hyp words in, eg if.
		List<WordWrapper> wordWrapperList = SearchWordPreprocess.sortWordsType(thm);
		//System.out.println(wordWrapperList);
		//keywordDict is annotated with "hyp"/"stm"
		int dictSz = keywordDict.keySet().size();
		int[] triggerTermsVec = new int[dictSz];
		
		for (WordWrapper wordWrapper : wordWrapperList) {
			//annotated form
			String termAnno = wordWrapper.hashToString();
			
			Integer rowIndex = keywordDict.get(termAnno);
			//System.out.println("first rowIndex " + rowIndex);
			if(rowIndex == null){
				termAnno = wordWrapper.otherHashForm();
				rowIndex = keywordDict.get(termAnno);				
			}
			//should normalize!!
			
			//System.out.println("second rowIndex " + rowIndex);
			if (rowIndex != null) {
				int termScore = wordsScoreMap.get(termAnno);
				//System.out.println("termAnno " + termAnno);
				//System.out.print("termScore " + termScore + "\t");
				//triggerTermsVec[rowIndex] = termScore;
				//keywordDict starts indexing from 0!
				triggerTermsVec[rowIndex] = termScore;
			}
		}
		//transform into query list String 
		StringBuilder sb = new StringBuilder();
		sb.append("{{");
		//String s = "{{";
		for(int j = 0; j < dictSz; j++){
			String t = j == dictSz-1 ? triggerTermsVec[j] + "" : triggerTermsVec[j] + ", ";
			sb.append(t);
		}
		sb.append("}}");
		System.out.println("query vector " + sb);
		return sb.toString();
	}

	/**Same as creareQuery, no annotation.
	 * Create query row vector, weighed using word scores,
	 * and according to norm.
	 * @param thm
	 * @return
	 */
	public static String createQueryNoAnno(String thm){
		
		//String[] thmAr = thm.split("\\s+|,|;|\\.");
		String[] thmAr = thm.split(WordForms.splitDelim());
		//map of non-annotated words and their scores
		Map<String, Integer> wordsScoreMap = CollectThm.ThmWordsMaps.get_wordsScoreMapNoAnno();		
		//should eliminate unnecessary words first, then send to get wrapped.
		//<--can only do that if leave the hyp words in, eg if.
		
		//keywordDict is annotated with "hyp"/"stm"
		int dictSz = keywordDict.keySet().size();
		int[] triggerTermsVec = new int[dictSz];
		int norm = 0;
		//highest weight amongst the single words
		int highestWeight = 0;
		//int numWords = 0;
		List<Integer> priorityWordsIndexList = new ArrayList<Integer>();
		
		//get norm first, form unnormalized vector, then divide terms by log of norm
		for (int i = 0; i < thmAr.length; i++) {
			String term = thmAr[i];
			int newNorm = norm;
			newNorm = addToNorm(thmAr, wordsScoreMap, triggerTermsVec, norm, i, term);
			if(newNorm - norm > highestWeight){
				highestWeight = newNorm - norm;
			}
			if(i < thmAr.length-1){
				String nextTermCombined = term + " " + thmAr[i+1];
				newNorm = addToNorm(thmAr, wordsScoreMap, triggerTermsVec, newNorm, i, nextTermCombined);				
			}
			if(term.matches(ConstantsInSearch.get_priorityWords())){
				priorityWordsIndexList.add(i);
			}						
			norm = newNorm;
		}
		//short-circuit if no relevant term was detected in input thm
		//rather than return a list of results that are close to the 0-vector
		//but doesn't make sense.
		if(norm == 0) return "";
		//fill in the priority words with highestWeight
		for(int index : priorityWordsIndexList){
			Integer rowIndex = keywordDict.get(thmAr[index]);
			if(rowIndex != null){
				int prevWeight = triggerTermsVec[rowIndex];
				int weightDiff = highestWeight - prevWeight;
				triggerTermsVec[rowIndex] = highestWeight;
				norm += weightDiff;
			}
		}		
		//avoid division by 0 apocalypse.
		norm = norm < 3 ? 1 : (int)Math.log(norm);
		//divide entries of triggerTermsVec by norm
		for(int i = 0; i < triggerTermsVec.length; i++){
			int prevScore = triggerTermsVec[i]; 
			triggerTermsVec[i] = triggerTermsVec[i]/norm;
			//avoid completely obliterating a word 
			if(prevScore != 0 && triggerTermsVec[i] == 0){
				triggerTermsVec[i] = 1;
			}
		}
		/*for (String term : thmAr) {			
			//need to singularize!
			Integer rowIndex = keywordDict.get(term);
			
			if (rowIndex != null) {
				int termScore = wordsScoreMap.get(term);
				//triggerTermsVec[rowIndex] = termScore;
				//keywordDict starts indexing from 0!
				triggerTermsVec[rowIndex] = termScore/norm;
			}
		}*/
		//transform into query list String 
		StringBuilder sb = new StringBuilder();
		sb.append("{{");
		//String s = "{{";
		for(int j = 0; j < dictSz; j++){
			String t = j == dictSz-1 ? triggerTermsVec[j] + "" : triggerTermsVec[j] + ", ";
			sb.append(t);
		}
		sb.append("}}");
		System.out.println("query vector " + sb);
		return sb.toString();
	}

	/**
	 * Auxiliary method to createQueryNoAnno. Adds word weight
	 * to norm, when applicable.
	 * @param thmAr
	 * @param wordsScoreMap
	 * @param norm
	 * @param i
	 * @param term
	 * @return
	 */
	private static int addToNorm(String[] thmAr, Map<String, Integer> wordsScoreMap, int[] triggerTermsVec,
			int norm, int i, String term) {
		Integer termScore = wordsScoreMap.get(term);
		//get singular forms
		
		if(termScore == null){
			term = WordForms.getSingularForm(term);
			termScore = wordsScoreMap.get(term);
		}
		//triggerTermsVec[rowIndex] = termScore;
		//keywordDict starts indexing from 0!
		if(termScore != null){
			//just adding the termscore, without squaring, works better
			//norm += Math.pow(termScore, 2);
			norm += termScore;
			thmAr[i] = term;
			//shouldn't be null, since termScore!=null
			int rowIndex = keywordDict.get(term);
			//triggerTermsVec[rowIndex] = termScore;
			//keywordDict starts indexing from 0!
			triggerTermsVec[rowIndex] = termScore;
			System.out.println("term just added: " + term + " " + rowIndex + " " + termScore);
			System.out.println(Arrays.toString(triggerTermsVec));
		}
		return norm;
	}

	/**
	 * Obtains mathThmMx
	 * @return
	 */
	public static int[][] mathThmMx(){
		return mathObjMx;
	}

	/**
	 * Get theorem given its index (column number) in mathThmMx.
	 * @param index
	 * @return
	 */
	public static String getThm(int index){
		//System.out.println("docWrodsFreqMap " + docWordsFreqMap);
		System.out.print("index of thm: " + index + "\t");
		//index is 1-based indexing, not 0-based.
		//System.out.println(CollectThm.get_thmWordsListNoAnno().get(index-1));
		
		if(showWordScore){
			Map<String, Integer> wordsScoreMap = CollectThm.ThmWordsMaps.get_wordsScoreMapNoAnno();		
			for(String word : thmWordsList.get(index-1).keySet()){
				System.out.print(word + " " + wordsScoreMap.get(word) + " " + docWordsFreqMapNoAnno.get(word));
			}
		}
		//System.out.println(thmWordsList.get(index-1));
		return mathObjList.get(index-1);
	}
	
	/*public static void main(String[] args){
		System.out.print(keywordDict.size());
	}*/
}
