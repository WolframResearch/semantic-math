package thmp.search;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;

public class TriggerMathThm {

	/**
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
		List<String> thmList = new ArrayList<String>();
		
		// which keyword corresponds to which index	in the keywords list
		ImmutableMap.Builder<String, Integer> keyDictBuilder = ImmutableMap.builder();
		ImmutableList.Builder<String> mathObjListBuilder = ImmutableList.builder();
		
		//which theorem corresponds to which index in the thmList
		ImmutableMap.Builder<String, Integer> thmDictBuilder = ImmutableMap.builder();
		//ImmutableList.Builder<String> mathObjListBuilder = ImmutableList.builder();
		
		// math object pre map
		Multimap<String, String> mathObjMMap = ArrayListMultimap.create();

		// first String should be theorem, the rest are key words of this theorem 
		// belongs to
		addKeywordToMathObj(new String[] { "polynomial", "fundamental theorem of algebra" }, keywordList, keyDictBuilder, mathObjMMap);
		addKeywordToMathObj(new String[] { "root", "fundamental theorem of algebra" }, keywordList, keyDictBuilder, mathObjMMap);
		addKeywordToMathObj(new String[] { "degree", "fundamental theorem of algebra" }, keywordList, keyDictBuilder, mathObjMMap);
		addKeywordToMathObj(new String[] { "complex", "fundamental theorem of algebra" }, keywordList, keyDictBuilder, mathObjMMap);
		addKeywordToMathObj(new String[] { "triangle", "Pythagorean theorem" }, keywordList, keyDictBuilder, mathObjMMap);
		addKeywordToMathObj(new String[] { "length", "Pythagorean theorem" }, keywordList, keyDictBuilder, mathObjMMap);
		addKeywordToMathObj(new String[] { "right", "Pythagorean theorem" }, keywordList, keyDictBuilder, mathObjMMap);
		addKeywordToMathObj(new String[] { "hypotenuse", "Pythagorean theorem" }, keywordList, keyDictBuilder, mathObjMMap);
		addKeywordToMathObj(new String[] { "square", "Pythagorean theorem" }, keywordList, keyDictBuilder, mathObjMMap);
		addKeywordToMathObj(new String[] { "incomplete", "Godel's incompleteness theorem" }, keywordList, keyDictBuilder, mathObjMMap);
		addKeywordToMathObj(new String[] { "arithmetic", "Godel's incompleteness theorem" }, keywordList, keyDictBuilder, mathObjMMap);
		addKeywordToMathObj(new String[] { "natural", "Godel's incompleteness theorem" }, keywordList, keyDictBuilder, mathObjMMap);
		// addKeywordToMathObj(new String[] { " ", "Godel's incompleteness theorem" }, keywordList, keyDictBuilder, mathObjMMap);
		// addKeywordToMathObj(new String[]{"finite", "function", "ring",
		// "module"}, keywordList, keyDictBuilder, mathObjMMap);

		// mathObjMultimap = ImmutableMultimap.copyOf(mathObjMMap);

		keywordDict = keyDictBuilder.build();

		mathObjMx = new int[keywordList.size()][mathObjMMap.keySet().size()];
		
		buildMathObjMx(keywordList, mathObjMMap, mathObjListBuilder);

		mathObjList = mathObjListBuilder.build();
	}

	public static void addKeywordToMathObj(String[] keywords, List<String> keywordList,
			ImmutableMap.Builder<String, Integer> keyDictBuilder, Multimap<String, String> mathObjMMap) {
		if (keywords.length == 0)
			return;
		
		String keyword = keywords[0];
		//theorem
		String thm = keywords[0];
		
		keyDictBuilder.put(keyword, keywordList.size());
		//*keyDictBuilder.put(keyword, keywordList.size());
		//System.out.println("Building: " + keyword);
		
		keywordList.add(keywords[0]);
		
		
		for (int i = 1; i < keywords.length; i++) {
			mathObjMMap.put(keywords[i], keyword);
			//keys are theorems, values are keywords
			//mathObjMMap.put(thm, keywords[i]);
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
	
	private static int[] getInnerProducts(List<String> triggerTerms) {
		// create vector
		int[] triggerTermsVec = new int[keywordDict.keySet().size()];
		// List<String> triggeredMathObjList = new ArrayList<String>();

		for (String term : triggerTerms) {
			Integer rowIndex = keywordDict.get(term);
			if (rowIndex != null) {
				triggerTermsVec[rowIndex] = 1;
			}
		}
		return applyMathObjMx(triggerTermsVec);
	}

	/**
	 * Use keywordDict to create a vector. Apply mathObjMx to it on the right.
	 * 
	 * @param triggerTerms
	 *            List of trigger terms to fetch an element.
	 * @return List of likely MathObj, anything which matched > 0 keywords.
	 */
	public static List<String> get_MathObj(List<String> triggerTerms) {
		// create vector
		// int[] triggerTermsVec = getTriggerTermsVec(triggerTerms);
		List<String> triggeredMathObjList = new ArrayList<String>();

		// apply mathObjMx on right
		int[] innerProducts = getInnerProducts(triggerTerms);

		for (int i = 0; i < innerProducts.length; i++) {
			if (innerProducts[i] > 0) {
				triggeredMathObjList.add(mathObjList.get(i));
			}
		}
		return triggeredMathObjList;
	}

	
	/**
	 * Get the mathObj with highest inner product
	 * 
	 * @param triggerTerms
	 * @return
	 */
	public static String get_HighestMathObj(List<String> triggerTerms) {
		
		int[] innerProducts = getInnerProducts(triggerTerms);
		String highestMathObj = "";
		int max = 0;

		for (int i = 0; i < innerProducts.length; i++) {
			if (innerProducts[i] > max) {
				max = innerProducts[i];
				highestMathObj = mathObjList.get(i);
			}
		}
		return highestMathObj;
	}

	/**
	 * 
	 * @param triggerTermsVec
	 * @return the transformed vector
	 */
	private static int[] applyMathObjMx(int[] triggerTermsVec) {
		// mathObjMx is a square matrix
		int mathObjNum = mathObjList.size();
		int keywordNum = keywordDict.keySet().size();

		int[] innerProducts = new int[mathObjNum];

		for (int i = 0; i < mathObjNum; i++) {
			for (int j = 0; j < keywordNum; j++) {
				innerProducts[i] += mathObjMx[j][i] * triggerTermsVec[j];
			}
		}
		return innerProducts;
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
