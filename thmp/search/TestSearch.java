package thmp.search;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class TestSearch {

	public static void main (String[] args) throws IOException{
		
		FileReader freqWordsFileReader = new FileReader("src/thmp/data/wordFrequency.txt");
		FileReader texSourceFileReader = new FileReader("src/thmp/data/CommAlg5.txt");
		
		BufferedReader freqWordsFileBuffer = new BufferedReader(freqWordsFileReader);
		BufferedReader texSourceFileBuffer = new BufferedReader(texSourceFileReader);
		
		SearchCombined.initializeSearchWithResource(freqWordsFileBuffer, texSourceFileBuffer);
		SearchCombined.searchCombined("regular ring");
	}
}
