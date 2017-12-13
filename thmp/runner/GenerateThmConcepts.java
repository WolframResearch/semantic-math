package thmp.runner;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.wolfram.puremath.dbapp.ConceptSearchUtils;
import com.wolfram.puremath.dbapp.DBUtils;
import com.wolfram.puremath.dbapp.SimilarThmUtils;

import thmp.search.ConceptSearch;
import thmp.search.ThmHypPairGet;
import thmp.search.SearchCombined.ThmHypPair;

/**
 * Generates thm concepts. Creates serialized data to populate db.
 * 
 * This will be incorporated into the combining matrix post-processing step.
 * 
 * @author yihed
 */
public class GenerateThmConcepts {
	
	/**
	 * At least one arg required, for path to tar, optional arg to specify whether to 
	 * collect msc data, with "msc" option
	 * @param args
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static void main(String[] args) throws FileNotFoundException, IOException, InterruptedException{
		
		Map<Integer, byte[]> similarThmsMap = new HashMap<Integer, byte[]>();
		int totalThmCount = ThmHypPairGet.totalThmsCount();
		
		for(int i = 0; i < totalThmCount; i++) {				
			ThmHypPair thmHypPair = ThmHypPairGet.retrieveThmHypPairWithThm(i);			
			String thmStr = thmHypPair.thmStr();
			
			similarThmsMap.put(i, ConceptSearch.getThmWordsIndexByteArray(thmStr));				
		}
		
		String path = DBUtils.ThmConceptsTb.thmConceptsByteArrayPath;
		//make map instead of write to file.
		List<Map<Integer, byte[]>> similarThmsMapList = new ArrayList<Map<Integer, byte[]>>();
		similarThmsMapList.add(similarThmsMap);
		thmp.utils.FileUtils.serializeObjToFile(similarThmsMapList, path);
		//System.out.println("Done serializing for bundle " + j);
		
	}
}
