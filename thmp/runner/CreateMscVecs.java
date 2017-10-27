package thmp.runner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;
import thmp.parse.TheoremContainer;
import thmp.search.CollectThm;
import thmp.search.TriggerMathThm2;
import thmp.utils.FileUtils;

/**
 * Creates vectors for classifying MSC classes.
 * @author yihed
 */
public class CreateMscVecs {

	public static class Paper implements TheoremContainer{
	
		private String paper;
		
		public Paper(String paper_) {
			this.paper = paper_;
		}
		
		@Override
		public String getEntireThmStr() {
			return this.paper;
		}
	}
	
	/**
	 * Create TD vectors for entire documents, then project down to 
	 * 35 dimensions, serialize in mx. 
	 */
	public static void main(String[] args) {
		
		
		
	}
	
	private static void f() {
		
		List<int[]> coordinatesList = new ArrayList<int[]>();
		List<Double> weightsList = new ArrayList<Double>();
		
		Map<String, Integer> wordsScoreMap = CollectThm.ThmWordsMaps.get_wordsScoreMap();
		//list of papers on one tar.
		List<Paper> paperList = new ArrayList<Paper>();
		List<String> paperIdList = new ArrayList<String>();
		
		// gatherTermDocumentMxEntries(List<? extends TheoremContainer> defThmList,
			//List<int[]> coordinatesList, List<Double> weightsList, Map<String, Integer> wordsScoreMap);
		//map of terms and their indices.
		//Map<String, Integer> termIndexMap = CollectThm.ThmWordsMaps.get_CONTEXT_VEC_WORDS_INDEX_MAP();
		
		//gather set of papers in one tar.
		
		
		//the coordinates list contains arrays of size 2, [keyWordIndex, thmCounter], need to increment
		//thmCounter by current running counter for all tars. Need
		List<List<String>> allTermsList = TriggerMathThm2.gatherTermDocumentMxEntries(paperList, coordinatesList, weightsList);
		
		StringBuilder termSb = new StringBuilder(50000);
		
		int paperListSz = paperList.size();
		
		assert paperListSz == paperIdList.size();
		
		for(int i = 0; i < paperListSz; i++) {
			String paperId = paperIdList.get(i);
			
			termSb.append(paperId +  );
			
		}
		
		//Need to index by paper, keep counter, need final data with paper id, the actual words that occur.
		//Create both, map for paper and words, and map for paper and indices.
		//Also needs words-score map, to json format.
		
		//Can check against map e.g. 0704.0005,42B30,42B35 at runtime to form map.
		
		
		//create JSON from wordsScoreMap,
		StringBuilder wordsScoreSb = new StringBuilder(30000);
		wordsScoreSb.append("{");
		for(Map.Entry<String, Integer> wordsScorePair : wordsScoreMap.entrySet()) {
			
			wordsScoreSb.append("\"" + wordsScorePair.getKey() + "\" : " + wordsScorePair.getValue() + ",");
		}
		wordsScoreSb.append("}");
		
		String wordsScoreMapPath = "src/thmp/data/wordsScoreMap.json";
		FileUtils.writeToFile(wordsScoreSb, wordsScoreMapPath);
		
	}
	
	
}
