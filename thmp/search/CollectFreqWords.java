package thmp.search;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Read in the most frequently-used words from file, 
 * along with their POS
 *
 */
public class CollectFreqWords {

	//Map of words and their pos.
	private static final ImmutableMap<String, String> wordPosMap;
	
	private static File wordsFile = new File("src/thmp/data/wordFrequency.txt");
	
	static{
		ImmutableMap.Builder<String, String> wordPosMapBuilder = ImmutableMap.builder();
		
		//pass builder into file
		try{
			readWords(wordPosMapBuilder);
		}catch(FileNotFoundException e){
			e.printStackTrace();
		}
		
		wordPosMap = wordPosMapBuilder.build();
	}
	
	private static void readWords(ImmutableMap.Builder<String, String> wordPosMapBuilder) throws FileNotFoundException{
		Scanner sc = new Scanner(wordsFile);
		//skip first line with header info
		sc.nextLine();
		
		while(sc.hasNextLine()){
			String line = sc.nextLine();
			String[] lineAr = line.split("\\s+");
			//2nd is word, 3rd is pos
			String pos;
			switch(lineAr[2]){
			case "i":
				pos = "pre";
				break;
			case "p":
				pos = "";
				break;
			default:
				pos = lineAr[2];
			}
			
			wordPosMapBuilder.put(lineAr[1], pos);
			
		}
		
		sc.close();
	}
	
	public static ImmutableMap<String, String> get_wordPosMap(){
		return wordPosMap;
	}
	
}
