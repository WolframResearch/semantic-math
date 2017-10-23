package thmp.runner;

import java.util.List;
import java.util.Random;
import java.util.Scanner;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import thmp.search.SearchCombined.ThmHypPair;
import thmp.utils.FileUtils;

/**
 * Selects random theorem given year.
 * @author yihed
 *
 */
public class RandomThmSelector {

	private static final String thmHypPairFileName = "parsedExpressionList";
	//includes trailing slash
	private static final ListMultimap<Integer, String> yearFilePathsMMap;
	private static final Random rand = new Random();
	
	static {
		String tarFileNamesStr = "testAugTo.txt";
		//FileUtils.deserializeListFromFile(tarFileNamesStr);
		List<String> dirNamesList = FileUtils.readLinesFromFile(tarFileNamesStr);
		yearFilePathsMMap = buildMap(dirNamesList);
	
	}
	
	/**
	 * List of directories containing parsedThmFiles, each line of the form
	 * exactly e.g. "0108_001Untarred/0108"
	 * @param dirNamesList
	 */
	private static ListMultimap<Integer, String> buildMap(List<String> dirNamesList) {
		//two-digit year and filePaths
		ListMultimap<Integer, String> yearFilePathsMap = ArrayListMultimap.create();
		
		for(String path : dirNamesList) {
			int twoDigitYear = Integer.parseInt(path.substring(0, 2));
			yearFilePathsMap.put(twoDigitYear, FileUtils.addIfAbsentTrailingSlashToPath(path));
		}
		return yearFilePathsMap;
	}
	
	public static void main(String[] args) {
		
		System.out.println("Enter a year: ");
		//4-digit year
		Scanner sc = new Scanner(System.in);
		while(sc.hasNext()) {
			
			String line = sc.nextLine();
			
			int year = 01;
			if(line.length() > 2) {
				line = line.substring(2, line.length());
			}
			
			try {	
				year = Integer.parseInt(line);
			}catch(NumberFormatException e) {
				System.out.println("Enter a valid year!");
				continue;
			}
			List<String> list = yearFilePathsMMap.get(year);
			
			if(list.isEmpty()) {
				System.out.println("Enter a year between 1992 and 2017.");
				continue;
			}
			
			int listSz = list.size();
			int randomInt = rand.nextInt(listSz);
			String randomDirFilePath = list.get(randomInt) + thmHypPairFileName;
			@SuppressWarnings("unchecked")
			List<ThmHypPair> thmHypPairList = (List<ThmHypPair>)FileUtils.deserializeListFromFile(randomDirFilePath);
			listSz = thmHypPairList.size();
			randomInt = rand.nextInt(listSz);
			
			String thmStr = thmHypPairList.get(randomInt).getEntireThmStr();
			int thmStrLen = thmStr.length();
			
			while(thmStrLen < 135) {
				randomInt = rand.nextInt(listSz);
				thmStr = thmHypPairList.get(randomInt).getEntireThmStr();
				thmStrLen = thmStr.length();
			}
			
			System.out.println(thmStr);
			System.out.println("Enter a year: ");
		}
		sc.close();
		
	}
	
}
