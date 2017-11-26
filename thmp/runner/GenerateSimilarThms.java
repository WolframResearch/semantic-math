package thmp.runner;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

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
		int argsLen = args.length;
		//read in location of source file
		if(0 == argsLen){
			System.out.println("Please supply a file to read data sources from!");
			return;
		}
		//filenames should be absolute paths
		List<String> fileNamesList = extractNamesFromFile(args[0]);
		System.out.println("GenerateSimilarThms-fileNamesList: " + fileNamesList);
		
		//run through thm indices
		
		thmp.search.SimilarThmSearch.findSimilarThm( thmIndex );		
		
	}
}
