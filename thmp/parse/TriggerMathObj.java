package thmp.parse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;

import thmp.utils.WordForms;

/**
 * Take inner product of trigger words and matrix of keywords.
 * A column contains a set of words that trigger a particular MathObj,
 * such as radius of convergence and root likely in column for function.
 * And ideals for column for ring.  
 * @deprecated Superceded by TriggerMathObj2
 * @author yihed
 */
@Deprecated
public class TriggerMathObj {
	
	/**
	 * List of keywords, eg radius, convergence, root.
	 * Don't actually need this after building
	 */
	//private static final List<String> keywordList;
	
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
	 * Map of math objects, eg function, field, with list of keywords.
	 * don't need this either
	 */
	//private static final Multimap<String, String> mathObjMultimap;
	
	/**
	 * Matrix of keywords.
	 * 
	 */
	private static final int[][] mathObjMx;
	
	static{
		//ImmutableList.Builder<String> keywordList = ImmutableList.builder();
		List<String> keywordList = new ArrayList<String>();
		//which keyword corresponds to which index 
		ImmutableMap.Builder<String, Integer> keyDictBuilder = ImmutableMap.builder();
		ImmutableList.Builder<String> mathObjListBuilder = ImmutableList.builder();
		
		//math object pre map
		Multimap<String, String> mathObjMMap = ArrayListMultimap.create();
		
		//first String is property, the rest are math objects this property belongs to
		addKeywordToMathObj(new String[]{"radius", "function", "PowerSeries"}, keywordList, keyDictBuilder, mathObjMMap);
		addKeywordToMathObj(new String[]{"analytic", "function"}, keywordList, keyDictBuilder, mathObjMMap);
		//addKeywordToMathObj(new String[]{"analytic", "function"}, keywordList, keyDictBuilder, mathObjMMap);
		addKeywordToMathObj(new String[]{"convergence", "PowerSeries"}, keywordList, keyDictBuilder, mathObjMMap);
		addKeywordToMathObj(new String[]{"radius of convergence", "PowerSeries"}, keywordList, keyDictBuilder, mathObjMMap);
		addKeywordToMathObj(new String[]{"ideal", "ring"}, keywordList, keyDictBuilder, mathObjMMap);
		addKeywordToMathObj(new String[]{"zero", "function", "PowerSeries"}, keywordList, keyDictBuilder, mathObjMMap);
		addKeywordToMathObj(new String[]{"root", "function", "PowerSeries"}, keywordList, keyDictBuilder, mathObjMMap);
		addKeywordToMathObj(new String[]{"function", "function"}, keywordList, keyDictBuilder, mathObjMMap);
		addKeywordToMathObj(new String[]{"ring", "ring"}, keywordList, keyDictBuilder, mathObjMMap);
		addKeywordToMathObj(new String[]{"surjective", "function"}, keywordList, keyDictBuilder, mathObjMMap);
		addKeywordToMathObj(new String[]{"holomorphic", "PowerSeries"}, keywordList, keyDictBuilder, mathObjMMap);
		addKeywordToMathObj(new String[]{"continuous", "function"}, keywordList, keyDictBuilder, mathObjMMap);
		addKeywordToMathObj(new String[]{"cohen-macaulay", "ring"}, keywordList, keyDictBuilder, mathObjMMap);
		addKeywordToMathObj(new String[]{"pure", "ring"}, keywordList, keyDictBuilder, mathObjMMap);
		
		//addKeywordToMathObj(new String[]{"finite", "function", "ring", "module"}, keywordList, keyDictBuilder, mathObjMMap);
		
		//mathObjMultimap = ImmutableMultimap.copyOf(mathObjMMap);
		
		keywordDict = keyDictBuilder.build();
		
		mathObjMx = new int[keywordList.size()][mathObjMMap.keySet().size()];
		
		buildMathObjMx(keywordList, mathObjMMap, mathObjListBuilder);
		//System.out.println(Arrays.deepToString(mathObjMx));
		
		mathObjList = mathObjListBuilder.build();
	}
	
	public static void addKeywordToMathObj(String[] keywords, List<String> keywordList,
			ImmutableMap.Builder<String, Integer> keyDictBuilder,
			Multimap<String, String> mathObjMMap){
		if(keywords.length == 0) return;
		
		String keyword = keywords[0];
		
		keyDictBuilder.put(keyword, keywordList.size());
		
		keywordList.add(keywords[0]);
		
		for(int i = 1; i < keywords.length; i++){
			mathObjMMap.put(keywords[i], keyword);					
		}
	}
	
	/**
	 * Builds the MathObjMx 
	 */
	private static void buildMathObjMx(List<String> keywordList, Multimap<String, String> mathObjMMap,
			ImmutableList.Builder<String> mathObjListBuilder){
		
		Set<String> mathObjMMapkeys = mathObjMMap.keySet();
		
		Iterator<String> mathObjMMapkeysIter = mathObjMMapkeys.iterator();
		int mathObjCounter = 0;
		while(mathObjMMapkeysIter.hasNext()){
			
			String curMathObj = mathObjMMapkeysIter.next();
			mathObjListBuilder.add(curMathObj);
			Collection<String> curMathObjCol = mathObjMMap.get(curMathObj);
			Iterator<String> curMathObjColIter = curMathObjCol.iterator();
			
			while(curMathObjColIter.hasNext()){				
				String keyword = curMathObjColIter.next();
				Integer keyWordIndex = keywordDict.get(keyword);
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
	
	/**
	 * Use keywordDict to create a vector. Apply mathObjMx to it on the right.
	 * @param triggerTerms List of trigger terms to fetch an element.
	 * @return List of likely MathObj, anything which matched > 0 keywords.
	 */
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
	}
	
	/**
	 * Get the mathObj with highest inner product
	 * @param triggerTerms
	 * @return
	 */
	private static String get_HighestMathObj(List<String> triggerTerms){		
		
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
	
	/**
	 * 
	 * @param triggerTermsVec
	 * @return the transformed vector
	 */
	private static int[] applyMathObjMx(int[] triggerTermsVec){
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
	
	/**
	 * Get list of triggers from Struct.
	 * One string for now, the one with highest inner product.
	 * @param struct
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
		System.out.println("TRIGGERTERMLIST " + triggerTermList);
		
		String highestMathObj = get_HighestMathObj(triggerTermList);
		
		//String namePpt = ((StructH<?>)struct).append_name_pptStr();
		String namePpt = struct.simpleToString(false, curCommand);
		if(struct.type().equals("ent")){
			namePpt = "{" + namePpt + "}";
		}
		String r = highestMathObj.matches("") ? "MathObj{" + namePpt + "}": highestMathObj + "[" + namePpt + "]";
		
		//return r + "[" + namePpt + "]";
		return r;
	}
	
	/**
	 * Analogous method for StructA as getChildrenNames for StructH. 
	 * @param struct
	 * @param childrenNameList
	 */
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
				System.out.println("\nnamePPT " + child.present(""));
			}			
		}		
	}	
	
}
