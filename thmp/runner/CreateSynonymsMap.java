package thmp.runner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import thmp.search.CollectThm.ThmWordsMaps.IndexPartPair;
import thmp.search.Searcher.SearchMetaData;
import thmp.utils.FileUtils;
import thmp.utils.WordForms;

/**
 * Filter and curate synonyms map for only words in wordThmIndexMMap
 * for all arXiv corpus word frequencies.
 * 
 * Note the raw neural net-generated words are also good for typos,
 * e.g. mclaurin : [maclaurin, maclauren]
 * @author yihed
 */
public class CreateSynonymsMap {

	
	
	public static void main(String[] args) {
		
		//pruneSynonymsMap();
		cleanPrunedMap();
	}

	/**
	 * Clean the synonyms multimap, so words that don't have any theorem associated to
	 * are removed. Should be run after pruneSynonymsMap.
	 */
	private static void cleanPrunedMap() {
		final String synonymsSerPath = "src/thmp/data/synonymsMap.dat";
		
		String wordThmIndexMMapPath = FileUtils.getPathIfOnServlet(SearchMetaData.wordThmIndexMMapSerialFilePath());	
		
		@SuppressWarnings("unchecked")
		Multimap<String, IndexPartPair> wordThmsIndexMultimap = ((List<Multimap<String, IndexPartPair>>)
				FileUtils.deserializeListFromFile(wordThmIndexMMapPath)).get(0);
		
		@SuppressWarnings("unchecked")
		Multimap<String, String> synonymMap 
			= ((List<Multimap<String, String>>)FileUtils.deserializeListFromFile(synonymsSerPath)).get(0);
		
		Multimap<String, String> synonymMap2 = ArrayListMultimap.create();		
		
		for(String w : synonymMap.keySet()) {	
			
			Collection<String> relatedWordsCol = synonymMap.get(w);
			Set<String> addedWords = new HashSet<String>();
			
			for(String word : relatedWordsCol) {
				if(!wordThmsIndexMultimap.containsKey(word)) {
					word = WordForms.getSingularForm(word);
					if(!wordThmsIndexMultimap.containsKey(word)) {
						word = WordForms.normalizeWordForm(word);
						if(!wordThmsIndexMultimap.containsKey(word)) {
							continue;
						}
					}
				}
				if(addedWords.contains(word)) {
					continue;
				}
				addedWords.add(word);
				synonymMap2.put(w, word);
			}			
		}
		
		Gson gson = new Gson();
		String synonymMapJson = gson.toJson(synonymMap2.asMap());
		FileUtils.writeToFile(synonymMapJson, "src/thmp/data/synonymsMapPruned2.json");
		
	}
	
	/**
	 * Prune synonyms map created from model of words that don't have theorems recorded.
	 */
	private static void pruneSynonymsMap() {
		
		String wordThmIndexMMapPath = FileUtils.getPathIfOnServlet(SearchMetaData.wordThmIndexMMapSerialFilePath());	
		
		@SuppressWarnings("unchecked")
		Multimap<String, IndexPartPair> wordThmsIndexMultimap = ((List<Multimap<String, IndexPartPair>>)
				FileUtils.deserializeListFromFile(wordThmIndexMMapPath)).get(0);
		
		final String synonymsJsonPath = "src/thmp/data/synonymsMap.json";					
		String json = FileUtils.readStrFromFile(synonymsJsonPath);	
		Multimap<String, String> synonymMap = ArrayListMultimap.create();
		
		@SuppressWarnings("serial")
		//suppress warning for anonymous class.
		java.lang.reflect.Type typeOfT = new TypeToken<Map<String, List<String>>>(){}.getType();	
		
		Gson gson = new Gson();
		Map<String, List<String>> neuralNetMap = gson.fromJson(json, typeOfT);
		System.out.println("Vocab size before pruning " + neuralNetMap.size());
		
		for(Map.Entry<String, List<String>> entry : neuralNetMap.entrySet()) {
			
			String word = entry.getKey();
			if(!wordThmsIndexMultimap.containsKey(word)) {
				word = WordForms.getSingularForm(word);
				if(!wordThmsIndexMultimap.containsKey(word)) {
					word = WordForms.normalizeWordForm(word);
					if(!wordThmsIndexMultimap.containsKey(word)) {
						continue;
					}
				}
			}			
			synonymMap.putAll(word, entry.getValue());
		}
		System.out.println("Vocab size after pruning " + synonymMap.size());
		
		final String synonymsSerPath = "src/thmp/data/synonymsMap.dat";
		List<Multimap<String, String>> mapList = new ArrayList<Multimap<String, String>>();
		mapList.add(synonymMap);
		FileUtils.serializeObjToFile(mapList, synonymsSerPath);
		
		String synonymMapJson = gson.toJson(synonymMap.asMap());
		FileUtils.writeToFile(synonymMapJson, "src/thmp/data/synonymsMapPruned.json");
	}
	
}
