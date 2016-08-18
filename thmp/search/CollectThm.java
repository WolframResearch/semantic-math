package thmp.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import thmp.ProcessInput;
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
	//contains map of keywords and their frequencies in this theorem. 
	//the more frequent words in a thm should be weighed up, but the
	//ones that are frequent in the whole doc weighed down.
	private static final ImmutableList<ImmutableMap<String, Integer>> thmWordsList;
	//Map of frequent words and their pos. Don't need the pos for now.
	private static final ImmutableMap<String, String> freqWordsMap; 
	//document-wide word frequency
	private static final ImmutableMap<String, Integer> docWordsFreqMap;
	//raw original file
	private static final File rawFile = new File("src/thmp/data/commAlg4.txt");
	//file to read from. Thms already extracted, ready to be processed.
	private static File thmFile = new File("src/thmp/data/thmFile4.txt");
	//list of theorems, in order their keywords are added to thmWordsList
	private static final ImmutableList<String> thmList;
	
	static{
		//should only get the top 100 words! *******
		freqWordsMap = CollectFreqWords.get_wordPosMap();
		//pass builder into a reader function. For each thm, builds immutable list of keywords, 
		//put that list into the thm list.
		ImmutableList.Builder<ImmutableMap<String, Integer>> thmWordsListBuilder = ImmutableList.builder();
		ImmutableList.Builder<String> thmListBuilder = ImmutableList.builder();
		Map<String, Integer> docWordsFreqPreMap = new HashMap<String, Integer>();
		
		try{
			readThm(thmWordsListBuilder, thmListBuilder, docWordsFreqPreMap);
		}catch(IOException e){
			e.printStackTrace();
		}
		
		thmWordsList = thmWordsListBuilder.build();	
		thmList = thmListBuilder.build();
		docWordsFreqMap = ImmutableMap.copyOf(docWordsFreqPreMap); 
	}
	
	private static void readThm(ImmutableList.Builder<ImmutableMap<String, Integer>> thmWordsListBuilder,
			ImmutableList.Builder<String> thmListBuilder, Map<String, Integer> docWordsFreqPreMap)
			throws IOException, FileNotFoundException{
		
		//use method in ProcessInput to process in thms. 
		List<String> thmList = ProcessInput.processInput(thmFile, true);
		//adds original thms without latex replaced
		thmListBuilder.addAll(ThmInput.readThm(rawFile));
		
		//processes the theorems, select the words
		for(String thm : thmList){
			//System.out.println("++THM: " + thm);
			Map<String, Integer> thmWordsMap = new HashMap<String, Integer>();
			//trim chars such as , : { etc.  			
			String[] thmAr = thm.toLowerCase().split("\\s+|\'|\\(|\\)|\\{|\\}|\\[|\\]|\\.|\\;|\\,|:");
			
			for(String word : thmAr){
				//only keep words with lengths > 2
				if(word.length() < 3 || freqWordsMap.containsKey(word)) continue;
				int wordFreq = thmWordsMap.containsKey(word) ? thmWordsMap.get(word) : 0;
				thmWordsMap.put(word, wordFreq + 1);
				int docWordFreq = docWordsFreqPreMap.containsKey(word) ? thmWordsMap.get(word) : 0;
				docWordsFreqPreMap.put(word, docWordFreq + 1);
			}
			thmWordsListBuilder.add(ImmutableMap.copyOf(thmWordsMap));
			//System.out.println("++THM: " + thmWordsMap);
		}
	}
	
	public static ImmutableList<ImmutableMap<String, Integer>> get_thmWordsList(){
		return thmWordsList;
	}
	
	/**
	 * Tests the methods here.
	 * 
	 * @param args
	 */
	public static void main(String[] args){
		
	}
}
