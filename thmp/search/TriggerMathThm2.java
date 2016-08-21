package thmp.search;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;

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
	 * ImmutableMap.
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

	static {
		// ImmutableList.Builder<String> keywordList = ImmutableList.builder();
		List<String> keywordList = new ArrayList<String>();
		//list of theorems
		//List<String> thmList = new ArrayList<String>();
		
		// which keyword corresponds to which index	in the keywords list
		ImmutableMap.Builder<String, Integer> keyDictBuilder = ImmutableMap.builder();
		//map of String (keyword), and integer (index in keywordList) of keyword.
		Map<String, Integer> keywordMap = new HashMap<String, Integer>();
		//ImmutableList.Builder<String> mathObjListBuilder = ImmutableList.builder();
		//to be list that contains the theorems, in the order they are inserted
				ImmutableList.Builder<String> mathObjListBuilder = ImmutableList.builder();
		// math object pre map. keys are theorems, values are keywords.
		Multimap<String, String> mathObjMMap = ArrayListMultimap.create();

		// first String should be theorem, the rest are key words of this theorem 
		// belongs to
		addKeywordToMathObj(new String[] { "fundamental theorem of algebra", "polynomial", "degree", "root", "complex"}, keywordList, keywordMap, mathObjMMap);
		addKeywordToMathObj(new String[] { "Pythagorean theorem", "triangle", "right", "length", "square"}, keywordList, keywordMap, mathObjMMap);
		addKeywordToMathObj(new String[] { "quadratic extension", "degree", "field", "square", "root"}, keywordList, keywordMap, mathObjMMap);
		
		// should be "thm", "term1", "term2", etc
		addKeywordToMathObj(new String[] { "Godel's incompleteness theorem", "arithmetic", "incomplete" }, keywordList, keywordMap, mathObjMMap);
		
		//adds thms from CollectThm.thmWordsList. The thm name is its index in thmWordsList.
		addThmsFromList(keywordList, keywordMap, mathObjMMap);
		
		keywordDict = ImmutableMap.copyOf(keywordMap);

		mathObjMx = new int[keywordList.size()][mathObjMMap.keySet().size()];		
		
		buildMathObjMx(keywordList, mathObjMMap, mathObjListBuilder);

		mathObjList = mathObjListBuilder.build();
	}

	public static void addKeywordToMathObj(String[] keywords, List<String> keywordList,
			Map<String, Integer> keywordMap, Multimap<String, String> mathObjMMap) {
		if (keywords.length == 0)
			return;
		
		//String keyword = keywords[0];
		//theorem
		String thm = keywords[0];
		
		for(int i = 1; i < keywords.length; i++){
			String keyword = keywords[i];
			mathObjMMap.put(thm, keyword);
			//add each keyword in
			if(!keywordMap.containsKey(keyword)){
				keywordMap.put(keyword, keywordList.size());
				keywordList.add(keyword);
			}			
		}		
		
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
	 * @param keywords
	 * @param keywordList
	 * @param keywordMap
	 * @param mathObjMMap
	 */
	private static void addThmsFromList(List<String> keywordList,
			Map<String, Integer> keywordMap, Multimap<String, String> mathObjMMap){
		ImmutableList<ImmutableMap<String, Integer>> thmWordsList = CollectThm.get_thmWordsList();
		//index of thm in thmWordsList, to be used as part of name
		int thmIndex = 0;
		for(ImmutableMap<String, Integer> wordsMap : thmWordsList){
			String thmName = Integer.toString(thmIndex++);
			List<String> keyWordsList = new ArrayList<String>(wordsMap.keySet()); 
			keyWordsList.add(0, thmName);
			addKeywordToMathObj(keyWordsList.toArray(new String[keyWordsList.size()]), keywordList, keywordMap, mathObjMMap);
		}
	}
	
	/**
	 * Builds the MathObjMx
	 */
	private static void buildMathObjMx(List<String> keywordList, Multimap<String, String> mathObjMMap,
			ImmutableList.Builder<String> mathObjListBuilder) {

		Set<String> mathObjMMapkeys = mathObjMMap.keySet();

		Iterator<String> mathObjMMapkeysIter = mathObjMMapkeys.iterator();
		int mathObjCounter = 0;
		while (mathObjMMapkeysIter.hasNext()) {

			String curMathObj = mathObjMMapkeysIter.next();
			mathObjListBuilder.add(curMathObj);
			Collection<String> curMathObjCol = mathObjMMap.get(curMathObj);
			Iterator<String> curMathObjColIter = curMathObjCol.iterator();

			while (curMathObjColIter.hasNext()) {
				String keyword = curMathObjColIter.next();
				Integer keyWordIndex = keywordDict.get(keyword);
				//should incorporate weights of each word!
				mathObjMx[keyWordIndex][mathObjCounter] = 1;
			}
			mathObjCounter++;
		}
	}

	/**
	 * Create query row vector, 1's and 0's.
	 * @param triggerTerms
	 * @return String representation of query vector, eg {{1,0,1,0}}
	 */
	public static String createQuery(String thm){
		String[] thmAr = thm.split(" |,|;|\\.");
		int dictSz = keywordDict.keySet().size();
		int[] triggerTermsVec = new int[dictSz];
		for (String term : thmAr) {
			//System.out.println("TERM: " + term);
			Integer rowIndex = keywordDict.get(term);
			if (rowIndex != null) {
				triggerTermsVec[rowIndex] = 1;
				//System.out.println("not null!");
			}
		}
		//transform into query list String 
		String s = "{{";
		for(int j = 0; j < dictSz; j++){
			String t = j == dictSz-1 ? triggerTermsVec[j] + "" : triggerTermsVec[j] + ", ";
			s += t;
		}
		s += "}}";
		return s;
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
		return mathObjList.get(index-1);
	}
}
