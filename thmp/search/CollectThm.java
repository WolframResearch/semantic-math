package thmp.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import thmp.ThmInput;

/**
 * Collects thms by reading in thms from Latex files. Gather
 * keywords from each thm. 
 * Keep map of common English words that should not be used
 * in mathObjMx. 
 * Build a structure that TriggerMathThm2 can use to build its
 * mathObjMx.
 * 
 * @author yihed
 *
 */
public class CollectThm {

	//initialized in static block. List of theorems, each of which
	//contains list of keywords in this theorem. 
	private static final ImmutableList<ImmutableList<String>> thmWordsList;
	//Map of frequent words and their pos. Don't need the pos for now.
	private static final ImmutableMap<String, String> freqWordsMap; 
	//file to read from 
	private static File thmFile = new File("src/thmp/data/commAlg4.txt");
	
	static{
		freqWordsMap = CollectFreqWords.get_wordPosMap();
		//pass builder into a reader function. For each thm, builds immutable list of keywords, 
		//put that list into the thm list.
		ImmutableList.Builder<ImmutableList<String>> thmWordsListBuilder = ImmutableList.builder();
		try{
			readThm(thmWordsListBuilder);
		}catch(IOException e){
			e.printStackTrace();
		}
		thmWordsList = thmWordsListBuilder.build();		
	}
	
	private static void readThm(ImmutableList.Builder<ImmutableList<String>> thmWordsListBuilder)
			throws IOException, FileNotFoundException{
		
		//should use method in ThmInput to read in thms. 
		List<String> thmList = ThmInput.readThm(thmFile);
		
		//processes the theorems, select the words
		for(String thm : thmList){
			List<String> thmWords = new ArrayList<String>();
			//trim words, skip latex formulas. 
			String[] thmAr = thm.split("\\s+|\'\\(|\\)|\\{|\\}|\\[|\\]");
			//
			for(String word : thmAr){
				//only keep words with lengths > 2
				if(word.length() < 3 || freqWordsMap.containsKey(word)) continue;
				thmWords.add(word);				
			}
			thmWordsListBuilder.add(ImmutableList.copyOf(thmWords));			
		}
	}
	
	public static ImmutableList<ImmutableList<String>> get_thmWordsList(){
		return thmWordsList;
	}
}
