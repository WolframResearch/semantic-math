package thmp.runner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import thmp.parse.ParseStruct;
import thmp.parse.ParsedExpression;
import thmp.parse.DetectHypothesis.DefinitionListWithThm;
import thmp.search.Searcher.SearchMetaData;
import thmp.utils.FileUtils;

/**
 * Creates placeholder lists, such as placeholder ParsedExpression lists.
 * 
 * @author yihed
 */
public class CreatePlaceholderLists {

	private static final String parsedExpressionSerialFileStr = "src/thmp/data/parsedExpressionList.dat";
	
	public static void main(String[] args){
		
		//f();
		createWordThmIndexMMap();
		//serializeParsedExpressionsList();
		
	}
	
	private static void createWordThmIndexMMap(){
		@SuppressWarnings("unchecked")
		Map<String, Integer> wordMap = ((List<Map<String, Integer>>)
				FileUtils.deserializeListFromFile("src/thmp/data/allThmWordsMap.dat")).get(0);
		Multimap<String, Integer> mmap = ArrayListMultimap.create();
		for(String word : wordMap.keySet()){
			mmap.put(word, 2);
		}
		String path = SearchMetaData.wordThmIndexMMapSerialFilePath();
		//wordThmIndexMMap.dat;
		List<Multimap<String, Integer>> tList = new ArrayList<Multimap<String, Integer>>(); 
		tList.add(mmap);
		FileUtils.serializeObjToFile(tList, path);
	}
	
	
	private static void f(){
		String twoGramsMapFile = "src/thmp/data/twoGramsMap.dat";
		Map<String, Integer> twoMap = new HashMap<String, Integer>();
		twoMap.put("group algebra",1);
		List<Map<String, Integer>> tList = new ArrayList<Map<String, Integer>>(); 
		tList.add(twoMap);
		FileUtils.serializeObjToFile(tList, twoGramsMapFile);
		
		String threeGramsMapFile = "src/thmp/data/threeGramsMap.dat";
		Map<String, Integer> threeMap = new HashMap<String, Integer>();
		threeMap.put("group algebra representation",1);
		tList = new ArrayList<Map<String, Integer>>(); 
		tList.add(threeMap);
		FileUtils.serializeObjToFile(tList, threeGramsMapFile);
	
		String fluffSetPath = SearchMetaData.trueFluffWordsSetPath();
		Set<String> set = new HashSet<String>();
		set.add("is");
		List<Set<String>> list = new ArrayList<Set<String>>(); 
		list.add(set);
		FileUtils.serializeObjToFile(list, fluffSetPath);
		
	}
	
	private static void serializeParsedExpressionsList(){
		List<ParsedExpression> parsedExpressionList = new ArrayList<ParsedExpression>();
		parsedExpressionList.add(new ParsedExpression("this is a thm", new ParseStruct(), 
				new DefinitionListWithThm("this is a thm", Collections.emptyList(), "str", "file")));
		
		FileUtils.serializeObjToFile(parsedExpressionList, parsedExpressionSerialFileStr);
	}
	
}
