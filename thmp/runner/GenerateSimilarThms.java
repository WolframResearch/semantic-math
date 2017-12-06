package thmp.runner;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.wolfram.puremath.dbapp.DBUtils;
import com.wolfram.puremath.dbapp.SimilarThmUtils;

import thmp.search.ThmHypPairGet;

/**
 * Generate indices of similar thms. Encodes indices to string,
 * Write results to csv file for database.
 * 
 * @author yihed
 *
 */
public class GenerateSimilarThms {

	//number of thms to serialize at a time
	private static final int numThmPerBundle = 5000;
	
	/**
	 * At least one arg required, for path to tar, optional arg to specify whether to 
	 * collect msc data, with "msc" option
	 * @param args
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static void main(String[] args) throws FileNotFoundException, IOException, InterruptedException{
		
		/*int argsLen = args.length;
		//read in location of source file
		if(0 == argsLen){
			System.out.println("Please supply a file to read data sources from!");
			return;
		}*/
		
		//get all files in thm dir
		//System.out.println("GenerateSimilarThms-fileNamesList: " + fileNamesList);
		
		//run through thm indices
		int totalThmCount = ThmHypPairGet.totalThmsCount();
		int totalBundles = (int)Math.ceil(((double)totalThmCount) / numThmPerBundle);
		//3 on Dec 5
		for(int j = 3; j < totalBundles; j++) {
			
			Map<Integer, byte[]> similarThmsMap = new HashMap<Integer, byte[]>();
			int endingIndex = Math.min(totalThmCount, (j+1)*numThmPerBundle);
			int startingIndex = j * numThmPerBundle;
			for(int i = startingIndex; i < endingIndex; i++) {				
				List<Integer> similarThmList = thmp.search.SimilarThmSearch.preComputeSimilarThm( i );
				similarThmsMap.put(i, SimilarThmUtils.indexListToByteArray(similarThmList));				
			}
			String path = DBUtils.SimilarThmsTb.similarThmIndexByteArrayPathNoDat + j + ".dat" ;
			//make map instead of write to file.
			List<Map<Integer, byte[]>> similarThmsMapList = new ArrayList<Map<Integer, byte[]>>();
			similarThmsMapList.add(similarThmsMap);
			thmp.utils.FileUtils.serializeObjToFile(similarThmsMapList, path);
			System.out.println("Done serializing for bundle " + j);
		}		
	}
}
