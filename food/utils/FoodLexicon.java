package food.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Multimap;

import thmp.utils.FileUtils;

/**
 * Process raw data, and build lexicons for words and N grams,
 * for food and cooking appliance.
 * @author yihed
 *
 */
public class FoodLexicon {

	private static final Multimap<String, String> FOOD_MAP;
	private static final Multimap<String, String> COOKING_ACTION_MAP;
	//used during food tokenization. 
	//All children originate from one Node.
	private static final FoodMapNode FOOD_TRIE;
	//private static final Multimap<String, FoodMapNode> COOKING_ACTION_TRIE;
	//	
	
	static{
		//deserialize into immutable maps.
		//Paths need to be stored in some meta file. Deserialize from file
		FOOD_TRIE = null;
		FOOD_MAP = null;
		COOKING_ACTION_MAP = null;
	}
	
	/**
	 * Wrapper class for food maps, constitutes nodes in food trie.
	 */
	public static class FoodMapNode implements Serializable{
		
		private static final long serialVersionUID = -7128948069154612069L;
		private Map<String, FoodMapNode> childrenMap;
		//number of tokens leading to current node, including this one
		private int tokenCount;
		
		public FoodMapNode(int tokenCount_) {
			this.tokenCount = tokenCount_;
			this.childrenMap = new HashMap<String, FoodMapNode>();
		}
		
		/**
		 * Adds and retrieves child node with corresponding name.
		 * @param childStr
		 * @return
		 */
		public FoodMapNode addIfAbsent(String childStr){
			FoodMapNode childNode = this.childrenMap.get(childStr);
			if(null == childNode){
				childNode = new FoodMapNode(this.tokenCount+1);
				this.childrenMap.put(childStr, childNode);
			}
			return childNode;
		}
		
		/**
		 * 
		 * @param childStr
		 * @return Can be null
		 */
		public FoodMapNode getChildNode(String childStr){
			FoodMapNode childNode = this.childrenMap.get(childStr);
			return childNode;
		}
		
		/**
		 * Number of tokens to this level, including current one.
		 * @return
		 */
		public int tokenCount(){
			return tokenCount;
		}		
		
		/**
		 * Retrieves token count of given word, ie how far
		 * down the line of 
		 * Throws ArrayIndexOutOfBoundsException if inputArLen <= curIndex
		 * @param inputAr
		 * @param curIndex
		 * @param inputArLen
		 * @return
		 */
		public int getTokenCount(String[] inputAr, int curIndex, int inputArLen){
			
			int tokenCounter = 0;
			FoodMapNode curNode = this;
			for(int i = curIndex; i < inputArLen; i++){
				String curWord = inputAr[i];
				curNode = curNode.getChildNode(curWord) ;
				if(null == curNode){
					return tokenCounter;
				}
				tokenCounter++;				
			}
			return tokenCounter;
		}
	}
	
	/**
	 * Process and Create the food lexicon, classify them as "ent".
	 * Builds trie.
	 * @return
	 * @throws IOException 
	 */
	public static Map<String, String> buildFoodLexicon(FoodMapNode rootNode, final String pos,
			String foodLexiconPath) throws IOException{
		
		Map<String, String> foodLexicon = new HashMap<String, String>();
		//Map<String, FoodMapNode> foodTrie = new HashMap<String, FoodMapNode>();
		
		File lexiconSrcFile = new File(foodLexiconPath);
		BufferedReader bReader = new BufferedReader(new FileReader(lexiconSrcFile));
		String line;
		//build the trie
		while((line=bReader.readLine()) != null){
			String lineAr[] = line.split(" ");
			int lineArLen = lineAr.length;
			if(lineArLen < 1){
				continue;
			}
			String firstWord = lineAr[0];
			FoodMapNode firstWordNode = rootNode.addIfAbsent(firstWord);
			FoodMapNode curNode = firstWordNode;
			for(int i = 1; i < lineArLen; i++){
				String curWord = lineAr[i];
				curNode = curNode.addIfAbsent(curWord);
			}			
			foodLexicon.put(line, pos);
		}
		//close properly!!!
		return foodLexicon;
	}
	
	public static void main(String[] args) throws IOException{
		
		String foodNodeSerialPath = "src/thmp/data/foodTrie.dat";
		List<FoodMapNode> foodTrieList = new ArrayList<FoodMapNode>();
		final int initialNodeTokenCount = 0;
		String foodLexiconPath = "src/thmp/data/foodLexicon/foodNames.txt";
		String pos = "ent";
		FoodMapNode foodRootNode = new FoodMapNode(initialNodeTokenCount);
		buildFoodLexicon(foodRootNode, pos, foodLexiconPath);
		foodTrieList.add(foodRootNode);
		
		String foodActionPath = "src/thmp/data/foodLexicon/cookingAction.txt";
		pos = "verb";
		FoodMapNode actionRootNode = new FoodMapNode(initialNodeTokenCount);
		buildFoodLexicon(actionRootNode, pos, foodActionPath);
		foodTrieList.add(actionRootNode);
		
		FileUtils.serializeObjToFile(foodTrieList, foodNodeSerialPath);
		
		String[] ar = new String[]{"blue", "cheese"};
		System.out.println("FoodLexicon - " + foodRootNode.getTokenCount(ar, 0, 2));		
		System.out.println("FoodLexicon - " + foodRootNode.getTokenCount(new String[]{"creamed", "cottage","cheese"}, 0, 3));
	}
	
}
