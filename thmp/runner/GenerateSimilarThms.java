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
		
		Map<Integer, String> similarThmsMap = new HashMap<Integer, String>();
		
		//StringBuilder sb = new StringBuilder(500000);
		//run through thm indices
		int totalThmCount = ThmHypPairGet.totalThmsCount();
		for(int i = 0; i < totalThmCount; i++) {
			
			List<Integer> similarThmList = thmp.search.SimilarThmSearch.preComputeSimilarThm( i );
			similarThmsMap.put(i, SimilarThmUtils.indexListToStr(similarThmList));
			
		}
		//make map instead of write to file, to avoid newlines!!
		//thmp.utils.FileUtils.writeToFile(sb.toString(), DBUtils.similarThmIndexStrPath);
		List<Map<Integer, String>> similarThmsMapList = new ArrayList<Map<Integer, String>>();
		similarThmsMapList.add(similarThmsMap);
		thmp.utils.FileUtils.serializeObjToFile(similarThmsMapList, DBUtils.SimilarThmsTb.similarThmIndexStrPath);
	}
}
