package thmp.parse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;

import thmp.search.CollectThm;
import thmp.utils.WordForms;

/**
 * Functionally analogous to TriggerMathObj2.java, but uses hashtable
 * for constant instead of linear time lookup, as is the case in 
 * TriggerMathObj2.java, which uses inner products.
 * 
 * A column contains a set of words that trigger a particular MathObj,
 * such as radius of convergence and root likely in column for function.
 * And ideals for column for ring. 
 * 
 * @author yihed
 *
 */
public class TriggerMathObj3 {
	
	/**
	 * List of keywords, eg radius, convergence, root.
	 * Don't actually need this after building
	 */
	//private static final List<String> keywordList;
	
	/**
	 * List of mathObj's, in order they are inserted into mathObjMx
	 */
	//private static final List<String> mathObjList;
	
	/**
	 * Dictionary of keywords -> their index/row number in mathObjMx.
	 * ImmutableMap.
	 */
	//private static final Map<String, Integer> keywordDict;
	
	/**
	 * Map of math objects, eg function, field, with list of keywords.
	 * don't need this either
	 */
	//private static final Multimap<String, String> mathObjMultimap;
	
	/**
	 * Matrix of keywords.
	 * 
	 */
	//private static final int[][] mathObjMx;
	
	//Multimap of trigger words, and the words they trigger.
	private static final Multimap<String, String> triggerTermsMMap;
	//private static final Set<String> keywordsSet;
	
	static{
		triggerTermsMMap = HashMultimap.create();
		//set to contain the keywords
		//keywordsSet = new HashSet<String>();
		
		//List<String> keywordList = new ArrayList<String>();
		//list of theorems
		//List<String> thmList = new ArrayList<String>();
		
		// which keyword corresponds to which index	in the keywords list
		/*ImmutableMap.Builder<String, Integer> keyDictBuilder = ImmutableMap.builder();
		//map of String (keyword), and integer (index in keywordList) of keyword.
		Map<String, Integer> keywordMap = new HashMap<String, Integer>();
		//ImmutableList.Builder<String> mathObjListBuilder = ImmutableList.builder();
		//to be list that contains the theorems, in the order they are inserted
				ImmutableList.Builder<String> mathObjListBuilder = ImmutableList.builder();
		// math object pre map. keys are theorems, values are keywords.
		Multimap<String, String> mathObjMMap = ArrayListMultimap.create();
		
		//first String is math obj, the rest are properties this object has
		/*
		addKeywordToMathObj("function", new String[]{"analytic", "zero", "surjective", "function", "root", "continuous"}, keywordList, keywordMap, mathObjMMap);
		addKeywordToMathObj("PowerSeries", new String[]{"radius of convergence", "Power Series", "analytic", "holomorphic",
				"zero", "root"}, keywordList, keywordMap, mathObjMMap);
		addKeywordToMathObj("ring", new String[]{"ideal", "ring", "pure", "cohen-macaulay", "catenary"}, keywordList, keywordMap, mathObjMMap);		
		addKeywordToMathObj("field", new String[]{"algebraically closed"}, keywordList, keywordMap, mathObjMMap);
		*/
		
		//add additional 
		addTriggerTermsToMap("function", new String[]{"analytic", "zero", "surjective", "function", "root", "continuous"}, 
				triggerTermsMMap);
		addTriggerTermsToMap("PowerSeries", new String[]{"radius of convergence", "Power Series", "analytic", "holomorphic",
				"zero", "root"}, triggerTermsMMap);
		addTriggerTermsToMap("ring", new String[]{"ring", "pure", "cohen-macaulay", "catenary", "noetherian"}, triggerTermsMMap);		
		addTriggerTermsToMap("ideal", new String[]{"ideal"}, triggerTermsMMap);		

		addTriggerTermsToMap("field", new String[]{"algebraically closed"}, triggerTermsMMap);
		//////
		
		// mathObjMultimap = ImmutableMultimap.copyOf(mathObjMMap);
		
		//keywordDict = ImmutableMap.copyOf(keywordMap);

		//mathObjMx = new int[keywordList.size()][mathObjMMap.keySet().size()];		
		
		//buildMathObjMx(keywordList, mathObjMMap, mathObjListBuilder);

		//mathObjList = mathObjListBuilder.build();
	}
	
	/**
	 * Add the triggerWord and set of triggered words to triggerTermsMMap.
	 * @param triggeredWord
	 * @param triggerTerms
	 * @param triggerTermsMMap
	 * @return
	 */
	private static void addTriggerTermsToMap(String triggeredWord, String[] triggerWords, Multimap<String, String> triggerTermsMMap){
		
		for(String word : triggerWords){
			triggerTermsMMap.put(word, triggeredWord);			
		}
		
	}

	/**
	 * Gets list of likely terms.
	 * Uses one HashMap to keep track of element counts, and a TreeMap
	 * to keep track of ordering, that uses custom comparator based on the
	 * element counts.
	 * @param triggerTerms
	 * @return list of most-triggered terms. Could be null.
	 */
	public static List<String> getLikelyTerms(List<String> triggerTerms){
		//Map<String, Integer> countsMap = new HashMap<String, Integer>();
		Multiset<String> countsMSet = HashMultiset.create();
		
		Comparator<String> wordComparator = new TriggerWordComparator(countsMSet);
		
		//map of triggered words with their counts
		Map<String, Integer> triggeredWordsTMap = new TreeMap<String, Integer>(wordComparator);
		//list of triggered words with top most frequent number of hits.
		List<String> list = new ArrayList<String>();
		
		for(String triggerTerm : triggerTerms){
			//gets set of triggered words
			Collection<String> triggeredTerms = triggerTermsMMap.get(triggerTerm);
			//add each triggered term into the countsMap, and 
			for(String triggeredTerm : triggeredTerms){
				countsMSet.add(triggeredTerm);
				triggeredWordsTMap.put(triggeredTerm, countsMSet.count(triggeredTerm));
			}			
		}
		//get the triggered words with the highest count
		Iterator<Map.Entry<String, Integer>> triggeredWordsTMapIter 
			= triggeredWordsTMap.entrySet().iterator();
		
		if(!triggeredWordsTMapIter.hasNext()){
			//return null if empty, to be consistent with getTopTerm returning 
			//null if no triggered element.
			return null;
		}
		
		Map.Entry<String, Integer> firstEntry = triggeredWordsTMapIter.next();
		list.add(firstEntry.getKey());
		int topFrequency = firstEntry.getValue();
		
		while(triggeredWordsTMapIter.hasNext()){
			Map.Entry<String, Integer> entry = triggeredWordsTMapIter.next();
			if(entry.getValue() < topFrequency){
				break;
			}
			list.add(entry.getKey());
		}
		
		return list;
	}
	
	private static class TriggerWordComparator implements Comparator<String>{
		//map to keep track of element counts
		Multiset<String> countsMSet;
		TriggerWordComparator(Multiset<String> countsMSet){
			this.countsMSet = countsMSet;
		}
		
		@Override
		public int compare(String s1, String s2){
			int count1 = countsMSet.count(s1);
			int count2 = countsMSet.count(s2);
			//highest count come first
			return count1 > count2 ? -1 : (count1 < count2 ? 1 : 0);
		}
	}
	
	/**
	 * Gets most likely term.
	 * @param triggerTerms
	 * @return
	 */
	public static String getTopTerm(List<String> triggerTerms){
		List<String> highestTriggeredTerms = getLikelyTerms(triggerTerms);
		if(null == highestTriggeredTerms){
			return null;
		}
		return highestTriggeredTerms.get(0);
	}
	
	/*public static void addKeywordToMathObj(String[] keywords, List<String> keywordList,
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
	}*/
	
	
	private static void addKeywordToMathObj(String thm, List<String> keywordsList, List<String> keywordList,
			Map<String, Integer> keywordMap, Multimap<String, String> mathObjMMap) {
		
		int keywordsListLen = keywordsList.size();
		if (keywordsListLen == 0)
			return;
		
		for(int i = 0; i < keywordsListLen; i++){
			String keyword = keywordsList.get(i);
			mathObjMMap.put(thm, keyword);
			//add each keyword in
			if(!keywordMap.containsKey(keyword)){
				keywordMap.put(keyword, keywordList.size());
				keywordList.add(keyword);
			}			
		}
	}
	
	private static void addKeywordToMathObj(String thm, String[] keywords, List<String> keywordList,
			Map<String, Integer> keywordMap, Multimap<String, String> mathObjMMap) {
		
		addKeywordToMathObj(thm, Arrays.asList(keywords), keywordList, keywordMap, mathObjMMap); 	
	}
	
	/**
	 * Adds thms by calling addKeywordToMathObj on CollectThm.thmWordsList.
	 * @param keywords
	 * @param keywordList
	 * @param keywordMap
	 * @param mathObjMMap
	 * @deprecated
	 */	
	private static void addThmsFromList(List<String> keywordList,
			Map<String, Integer> keywordMap, Multimap<String, String> mathObjMMap){
		ImmutableList<ImmutableMap<String, Integer>> thmWordsList = CollectThm.ThmWordsMaps.get_thmWordsFreqListNoAnno();
		//index of thm in thmWordsList, to be used as part of name
		int thmIndex = 0;
		for(ImmutableMap<String, Integer> wordsMap : thmWordsList){
			String thmName = Integer.toString(thmIndex++);
			//List<String> keyWordsList = wordsMap.keySet().asList();
			addKeywordToMathObj(thmName, wordsMap.keySet().asList(), keywordList, keywordMap, mathObjMMap);
		}
	}
	
	/*
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
	
	private static int[] getInnerProducts(List<String> triggerTerms){
		//create vector
		int[] triggerTermsVec = new int[keywordDict.keySet().size()];
		//List<String> triggeredMathObjList = new ArrayList<String>();
		
		for(String term : triggerTerms){
			
			Integer rowIndex = keywordDict.get(term);
			
			if(rowIndex != null){
				triggerTermsVec[rowIndex] = 1;
			}
		}
		//System.out.println("triggerTermsVec" + Arrays.toString(triggerTermsVec));
		return applyMathObjMx(triggerTermsVec);
	}
	
	
	 
	public static List<String> get_MathObj(List<String> triggerTerms){
		//create vector
		//int[] triggerTermsVec = getTriggerTermsVec(triggerTerms);
		List<String> triggeredMathObjList = new ArrayList<String>();
		
		//apply mathObjMx on right
		int[] innerProducts = getInnerProducts(triggerTerms);
		
		for(int i = 0; i < innerProducts.length; i++){
			if(innerProducts[i] > 0){
				triggeredMathObjList.add(mathObjList.get(i));
			}
		}
		return triggeredMathObjList;
	}*/
	
	/**
	 * Get the mathObj with highest inner product
	 * @param triggerTerms
	 * @return
	 */
	/*private static String get_HighestMathObj(List<String> triggerTerms){		
		
		int[] innerProducts = getInnerProducts(triggerTerms);
		String highestMathObj = "";
		int max = 0;
		int maxIndex = -1;
		for(int i = 0; i < innerProducts.length; i++){
			if(innerProducts[i] > max){
				max = innerProducts[i];
				maxIndex = i;				
			}
		}
		System.out.println("inner products: " + Arrays.toString(innerProducts));

		if(maxIndex == -1){
			return "";
		}else{
			highestMathObj = mathObjList.get(maxIndex);
			return highestMathObj;
		}
	}
	*/
	
	//
	 // @param triggerTermsVec
	 //@return the transformed vector
	 //
	/*private static int[] applyMathObjMx(int[] triggerTermsVec){
		//mathObjMx is a square matrix
		int mathObjNum = mathObjList.size();
		int keywordNum = keywordDict.keySet().size();
		
		int[] innerProducts = new int[mathObjNum];
		
		for(int i = 0; i < mathObjNum; i++){
			for(int j = 0; j < keywordNum; j++){
				innerProducts[i] += mathObjMx[j][i] * triggerTermsVec[j];
			}
		}
		return innerProducts;
	}	
	*/
	/**
	 * Get list of triggers from Struct.
	 * One string for now, the one with highest inner product.
	 * @param struct The triggering struct.
	 * @param curCommand WLCommand in which this triggering struct is part of.
	 * @return
	 */
	public static String get_mathObjFromStruct(Struct struct, WLCommand curCommand){
		//recursively find all relevant strings
		//if structH, use name of element and name of children
		//if(struct instanceof StructA) return "";
		
		List<String> triggerTermList = new ArrayList<String>();
		Map<String, String> map = struct.struct();		
		
		if(map != null && map.containsKey("name")){
			triggerTermList.add(map.get("name"));
		}
		
		getSubContent(struct, triggerTermList);
		getChildrenNames(struct, triggerTermList);
		//System.out.println("TRIGGER_MATH_OBJ_LIST " + triggerTermList);
		
		String highestMathObj = getTopTerm(triggerTermList);
		
		//String namePpt = ((StructH<?>)struct).append_name_pptStr();
		boolean includeType = false;
		String namePpt = struct.simpleToString(includeType, curCommand);
		/*if(struct.type().equals("ent")){
			namePpt = "[" + namePpt + "]";
		}*/
		//String mathStr = (null == highestMathObj) ? "Math[\"" + namePpt + "\"]": highestMathObj + "[\"" + namePpt + "\"]";
		String mathStr = (null == highestMathObj) ? "Math[" + namePpt + "]": highestMathObj + "[" + namePpt + "]";
		
		//return r + "[" + namePpt + "]";
		return mathStr;
	}
	
	// Analogous method for StructA as getChildrenNames for StructH. 
	// @param struct
	 // @param childrenNameList
	 //
	private static void getSubContent(Struct struct, List<String> childrenNameList){
		if(struct instanceof StructH ) return;
		
		if(struct.prev1() instanceof String){
			childrenNameList.add(struct.prev1().toString());
		}else if(struct.prev1() instanceof Struct){
			Struct subStruct = (Struct)struct.prev1();
			getSubContent(subStruct, childrenNameList);
			getChildrenNames(subStruct, childrenNameList);
		}
		
		if(struct.prev2() instanceof String){
			childrenNameList.add(struct.prev2().toString());
		}else if(struct.prev2() instanceof Struct){
			Struct subStruct = (Struct)struct.prev2();
			getSubContent(subStruct, childrenNameList);
			getChildrenNames(subStruct, childrenNameList);
		}
	}
	
	/**
	 * Retrieves the Sting representation of names of the children.
	 * @param struct
	 * @param childrenNameList
	 */
	private static void getChildrenNames(Struct struct, List<String> childrenNameList){
		//note for StructA, has_child == false
		if(struct instanceof StructA) return; 
		
		if(struct.struct().containsKey("name")){
			String structName = struct.struct().get("name");
			childrenNameList.add(structName);
			String[] singularForms = WordForms.getSingularForms(structName);
			for(String singularForm : singularForms){
				if(singularForm != null && !singularForm.equals("")){
					childrenNameList.add(singularForm);
				}
			}
		}
		List<Struct> children = struct.children();
		for(Struct child : children){
			//don't cast, make abstract method in Struct
			if(child instanceof StructH){
				String namePpt = ((StructH<?>)child).append_name_pptStr();	
				//should not add the whole namePpt string to childrenNameList as one string,
				//should add as separate strings
				String[] namePptAr = namePpt.split(",\\s*");
				childrenNameList.addAll(Arrays.asList(namePptAr));
				getChildrenNames(child, childrenNameList);
			}else{
				System.out.println("\n namePPT " + child.present(""));
			}			
		}		
	}	
	
}
