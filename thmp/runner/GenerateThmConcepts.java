package thmp.runner;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.wolfram.puremath.dbapp.DBUtils;
import com.wolfram.puremath.dbapp.SimilarThmUtils;

import thmp.search.CollectThm;
import thmp.search.ConceptSearch;
import thmp.search.ThmHypPairGet;
import thmp.search.SearchCombined.ThmHypPair;
import thmp.utils.FileUtils;

/**
 * Generates thm concepts. Creates serialized data to populate db.
 * 
 * This will be incorporated into the combining matrix post-processing step.
 * 
 * @author yihed
 */
public class GenerateThmConcepts {
	
	private static final int bitsPerWordIndex = thmp.search.ConceptSearch.NUM_BITS_PER_WORD_INDEX();
	private static final List<String> contextWordIndexDict 
		= CollectThm.ThmWordsMaps.get_CONTEXT_VEC_WORDS_LIST();
	
	/**
	 * At least one arg required, for path to tar, optional arg to specify whether to 
	 * collect msc data, with "msc" option
	 * @param args
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static void main(String[] args) throws FileNotFoundException, IOException, InterruptedException{
		
		testConceptsCreation();
		
		
		boolean b = false;
		if(b) {
			generateThmConcepts();
		}
	}

	private static void testConceptsCreation() {
		@SuppressWarnings("unchecked")
		List<Map<Integer, byte[]>> thmConceptsMapList 
			= (List<Map<Integer, byte[]>>)FileUtils.deserializeListFromFile(DBUtils.ThmConceptsTb.thmConceptsByteArrayPath);
		
		//Map<Integer, byte[]> thmConceptsMap = thmConceptsMapList.get(0);
		Map<Integer, byte[]> thmConceptsMap = thmConceptsMapList.get(0);
		
		int counter = 0;
		
		while(counter++ < 10) {
			byte[] bytes = thmConceptsMap.get(counter);
			ThmHypPair thmHypPair = ThmHypPairGet.retrieveThmHypPairWithThm(counter);
			
			System.out.println(thmHypPair.thmStr());
			
			System.out.println("FRESH GENERATED: ");
			ConceptSearch.getThmWordsIndexByteArray( thmHypPair.thmStr());
			
			System.out.println("PRECOMPUTED: ");	
			List<Integer> wordsList = SimilarThmUtils.byteArrayToIndexList(bytes, bitsPerWordIndex);
			for(int i = 0; i < wordsList.size(); i++) {
				System.out.print(contextWordIndexDict.get(wordsList.get(i)) +  "\t");
			}
			
			System.out.println("~~~~~~~~");			
		}
		
	}

	private static void generateThmConcepts() {
		Map<Integer, byte[]> thmConceptsMap = new HashMap<Integer, byte[]>();
		int totalThmCount = ThmHypPairGet.totalThmsCount();
		
		for(int i = 0; i < totalThmCount; i++) {				
			ThmHypPair thmHypPair = ThmHypPairGet.retrieveThmHypPairWithThm(i);			
			String thmStr = thmHypPair.thmStr();
			
			thmConceptsMap.put(i, ConceptSearch.getThmWordsIndexByteArray(thmStr));				
		}
		
		String path = DBUtils.ThmConceptsTb.thmConceptsByteArrayPath;
		//make map instead of write to file.
		List<Map<Integer, byte[]>> thmConceptsMapList = new ArrayList<Map<Integer, byte[]>>();
		thmConceptsMapList.add(thmConceptsMap);
		thmp.utils.FileUtils.serializeObjToFile(thmConceptsMapList, path);
		//System.out.println("Done serializing for bundle " + j);
	}
}
