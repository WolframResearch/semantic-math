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

import thmp.qa.QuestionAcquire.Formula;

/**
 * General methods used to get term document matrix, and associated lists.
 * Rows represent terms, columns represent documents.
 * @author yihed
 *
 */
public class TermDocMatrix {
	
	/**
	 * List of mathObj's, in order they are inserted into mathObjMx
	 */
	private List<String> docList;

	/**
	 * Dictionary of keywords -> their index/row number in mathObjMx.
	 * ImmutableMap.
	 */
	private Map<String, Integer> keywordDict;

	/**
	 * Matrix of keywords.
	 */
	private int[][] termDocMx;

	//private constructor. Using builder pattern.
	private TermDocMatrix(){		
	}
	
	/**
	 * Get theorem given its index (column number) in mathThmMx.
	 * @param index
	 * @return
	 */
	public String getDoc(int index){
		return docList.get(index-1);
	}
	
	/**
	 * Obtains mathThmMx
	 * @return
	 */
	public int[][] termDocMx(){
		return termDocMx;
	}
	
	public List<String> docList(){
		return docList;
	}
	
	public static class TermDocMatrixBuilder{
		
		private List<String> keywordList;
		// which keyword corresponds to which index	in the keywords list
		private ImmutableMap.Builder<String, Integer> keyDictBuilder;
		//map of String (keyword), and integer (index in keywordList) of keyword.
		private Map<String, Integer> keywordMap;
		//to be list that contains the theorems, in the order they are inserted
		private ImmutableList.Builder<String> mathObjListBuilder;
		// math object pre map. keys are theorems, values are keywords.
		private Multimap<String, String> mathObjMMap;
		
		
		public TermDocMatrixBuilder(){
			keywordList = new ArrayList<String>();
			keyDictBuilder = ImmutableMap.builder();
			keywordMap = new HashMap<String, Integer>();
			mathObjListBuilder = ImmutableList.builder();
			mathObjMMap = ArrayListMultimap.create();
		}

		public TermDocMatrix build(){
			
			TermDocMatrix s = new TermDocMatrix();
			
			s.keywordDict = ImmutableMap.copyOf(keywordMap);
			
			s.termDocMx = new int[keywordList.size()][mathObjMMap.keySet().size()];
			
			//need to be called after s.mathObjMx and s.keywordDict are initialized
			buildMathObjMx(keywordList, mathObjMMap, mathObjListBuilder, s);

			s.docList = mathObjListBuilder.build();
			
			return s;
		}
	
	
	public void addKeywordToMathObj(String[] keywords) {
		if (keywords.length == 0)
			return;		
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
	}
	/**
	 * For QuestionAcquire mx.
	 * @param keywords.
	 * @param varFormulaMMap which var names map to which Formulas.
	 * @param formula map.
	 */
	public void addQAKeywordToMathObj(String[] keywords, Map<String, Formula> formulaDict,
			Multimap<String, Formula> varFormulaMMap) {
		if (keywords.length < 4)
			return;	
		//theorem
		String varName = keywords[1];
		String formulaName = keywords[0];
		String varQuestion = keywords[2];
		//need formula entry, to put in map.
		
		Formula formula = formulaDict.get(formulaName);
		if(formula == null){
			formula = new Formula(formulaName);
			formulaDict.put(formulaName, formula);
		}
		formula.addVariable(varName, varQuestion);
		//multimap re-add exact same val? --yes
		//but one formula should not have two var's of same name.
		varFormulaMMap.put(varName, formula);
		
		//should take weights into account!
		for(int i = 3; i < keywords.length; i += 2){
			String keyword = keywords[i];
			mathObjMMap.put(varName, keyword);
			//add each keyword in
			if(!keywordMap.containsKey(keyword)){
				keywordMap.put(keyword, keywordList.size());
				keywordList.add(keyword);
			}			
		}				
	}
	
	/**
	 * Builds the MathObjMx
	 */
	private void buildMathObjMx(List<String> keywordList, Multimap<String, String> mathObjMMap,
			ImmutableList.Builder<String> mathObjListBuilder, TermDocMatrix instance) {
		
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
				Integer keyWordIndex = instance.keywordDict.get(keyword);
				//should incorporate weights of each word!
				instance.termDocMx[keyWordIndex][mathObjCounter] = 1;
			}
			mathObjCounter++;
		}
	}
	}
}
