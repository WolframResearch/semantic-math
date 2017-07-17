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
import java.util.Set;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import food.parse.FoodParseMetadata;
import thmp.utils.FileUtils;
import thmp.utils.WordForms;

/**
 * Process raw data, and build lexicons for words and N grams,
 * for food and cooking appliance.
 * The "buildMapsAndSerialize()" method in this class creates the respective maps
 * from word data gathered over time.
 * @author yihed
 *
 */
public class FoodLexicon {
	
	/***These are from curated lists****/
	private static final Map<String, String> FOOD_MAP;
	//the keys of food type map can be used as starting ingredients list.
	private static final Map<String, String> FOOD_TYPES_MAP;
	private static final Map<String, String> COOKING_ACTION_MAP;
	private static final Map<String, String> EQUIPMENT_MAP;
	
	private static final Set<String> ingredientFoodTypesSet;
	
	//used during food tokenization. 
	//All children originate from one Node.
	private static final FoodMapNode FOOD_TRIE;
	private static final FoodMapNode COOKING_ACTION_TRIE;	
	/*************/
	
	//additional food that don't appear in curated lists.
	private static final Multimap<String, String> EXTRA_FOOD_LEXICON_MMAP;
		
	static{
		//deserialize into immutable maps.
		//Paths need to be stored in some meta file. Deserialize from file.
		//These orders *must* coincide with the order used when serializing data.
		List<FoodMapNode> trieList = deserializeFoodTrieList();
		FOOD_TRIE = trieList.get(0);
		COOKING_ACTION_TRIE = trieList.get(1);
		
		List<Map<String, String>> mapList = deserializeFoodMapList();
		FOOD_MAP = mapList.get(0);
		FOOD_TYPES_MAP = mapList.get(1);
		COOKING_ACTION_MAP = mapList.get(2);	
		EQUIPMENT_MAP = mapList.get(3);
		
		ingredientFoodTypesSet = FOOD_TYPES_MAP.keySet();
		
		String mapInputPath = "src/thmp/data/extraFoodLexicon.txt";
		EXTRA_FOOD_LEXICON_MMAP = deserializeAdditionalFoodLexiconMap(FileUtils.getPathIfOnServlet(mapInputPath));
	}
	
	/**
	 * Wrapper class for food maps, constitutes nodes in food trie.
	 */
	public static class FoodMapNode implements Serializable{
		
		private static final long serialVersionUID = -7128948069154612069L;
		private Map<String, FoodMapNode> childrenMap;
		//number of tokens leading to current node, including this one
		private int tokenCount;
		//whether the combination of tokens up to (including) current node
		//form a valid term. E.g. just "a" in "a blend of peanut oil" is not valid food term.
		private boolean isValid;
		
		public FoodMapNode(int tokenCount_) {
			this.tokenCount = tokenCount_;
			this.childrenMap = new HashMap<String, FoodMapNode>();
		}
		
		public FoodMapNode(int tokenCount_, boolean isValid_) {
			this(tokenCount_);
			this.isValid = isValid_;
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
			}else if(isValid){
				childNode.setValidTokenEnd();
			}
			return childNode;
		}
		
		private void setValidTokenEnd(){
			this.isValid = true;
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
		 * Whether the combination of tokens up to (including) current node
		 * form a valid term. E.g. just "a" in "a blend of peanut oil" is not valid food term.
		 * @return
		 */
		public boolean isValid(){
			return isValid;
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
		 * down the line of .
		 * Returns 0 if term is not in lexicon, even if the token matches part
		 * of some valid lexicon word.
		 * Throws ArrayIndexOutOfBoundsException if inputArLen <= curIndex.
		 * @param inputAr
		 * @param curIndex
		 * @param inputArLen
		 * @return
		 */
		public int getTokenCount(String[] inputAr, int curIndex, int inputArLen){
			if(curIndex > inputArLen - 1){
				return 0;
			}
			int tokenCounter = 0;
			FoodMapNode curNode = this;
			boolean isValidEnding = curNode.isValid;
			for(int i = curIndex; i < inputArLen; i++){
				String curWord = inputAr[i];				
				FoodMapNode tempNode = curNode.getChildNode(curWord);				
				if(null == tempNode){
					curNode = curNode.getChildNode(WordForms.getSingularForm(curWord));	
				}else{
					curNode = tempNode;
				}
				if(null == curNode){
					if(!isValidEnding){
						return 0;
					}
					return tokenCounter;
				}
				isValidEnding = curNode.isValid;
				tokenCounter++;				
			}
			if(!isValidEnding){
				return 0;
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
			String foodLexiconPath)// throws IOException
	{
		
		Map<String, String> foodLexicon = new HashMap<String, String>();
		
		File lexiconSrcFile = new File(foodLexiconPath);
		try{
			BufferedReader bReader = null;
			try{
				bReader = new BufferedReader(new FileReader(lexiconSrcFile));
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
					curNode.setValidTokenEnd();
					foodLexicon.put(line, pos);
				}
			}finally{
				FileUtils.silentClose(bReader);
			}	
		}catch(FileNotFoundException e){
			throw new IllegalStateException("FileNotFoundException when building food lexicon!");
		}catch(IOException e){
			throw new IllegalStateException("IOException when building food lexicon!");
		}
		return foodLexicon;
	}
	
	private static List<FoodMapNode> deserializeFoodTrieList(){
		String foodTrieSerialPath = FoodParseMetadata.foodTrieSerialPath;
		@SuppressWarnings("unchecked")
		List<FoodMapNode> trieList = (List<FoodMapNode>)FileUtils.deserializeListFromFile(foodTrieSerialPath);
		return trieList;		
	}
	
	private static List<Map<String, String>> deserializeFoodMapList(){
		String foodTrieSerialPath = FoodParseMetadata.foodMapSerialPath;
		@SuppressWarnings("unchecked")
		List<Map<String, String>> mapList = (List<Map<String, String>>)FileUtils.deserializeListFromFile(foodTrieSerialPath);
		return mapList;		
	}
	
	/**
	 * Read additional food lexicon map. 
	 * @return
	 */
	private static Multimap<String, String> deserializeAdditionalFoodLexiconMap(String mapInputPath){
		Multimap<String, String> additionalFoodMMap = ArrayListMultimap.create();
		try{
			FileReader fileReader = new FileReader(new File(mapInputPath));
			BufferedReader bReader = new BufferedReader(fileReader);
			try{
				String line;
				while((line=bReader.readLine()) != null){
					String[] lineAr = WordForms.getWhiteNonEmptySpaceNotAllPattern().split(line);
					if(lineAr.length < 2){
						continue;
					}			
					additionalFoodMMap.put(lineAr[0], lineAr[1]);			
				}
			}finally{
				bReader.close();
				fileReader.close();
			}
		}catch(IOException e){
			throw new IllegalStateException(e);
		}
		return additionalFoodMMap;
	}
	
	public static FoodMapNode foodTrie(){
		return FOOD_TRIE;
	}
	
	public static FoodMapNode cookingActionTrie(){
		return COOKING_ACTION_TRIE;
	}
	
	public static Map<String, String> foodMap(){
		return FOOD_MAP;
	}
	
	public static Set<String> ingredientFoodTypesSet(){
		return ingredientFoodTypesSet;
	}
	
	public static Map<String, String> cookingActionMap(){
		return COOKING_ACTION_MAP;
	}
	/**
	 * E.g. oven, skillet.
	 * @return
	 */
	public static Map<String, String> equipmentMap(){
		return EQUIPMENT_MAP;
	}

	/**
	 * Additional food lexicon beyond the curated lists.
	 * @return
	 */
	public static Multimap<String, String> additionalFoodLexiconMMap(){
		return EXTRA_FOOD_LEXICON_MMAP;
	}
	
	public static void main(String[] args) throws IOException{
		
		boolean build = false;
		if(build){
			FoodMapNode foodRootNode = buildMapsAndSerialize();
			String[] ar = new String[]{"blue", "cheese"};
			System.out.println("FoodLexicon - " + foodRootNode.getTokenCount(ar, 0, 2));		
			System.out.println("FoodLexicon - " + foodRootNode.getTokenCount(new String[]{"creamed", "cottage","cheese"}, 0, 3));
		}		
	}

	/**
	 * @return
	 */
	private static FoodMapNode buildMapsAndSerialize() {
		String foodTrieSerialPath = FoodParseMetadata.foodTrieSerialPath;//  "src/thmp/data/foodTrie.dat";
		String foodMapSerialPath = FoodParseMetadata.foodMapSerialPath;
		List<FoodMapNode> foodTrieList = new ArrayList<FoodMapNode>();
		List<Map<String, String>> foodMapList = new ArrayList<Map<String, String>>();
		
		final int initialNodeTokenCount = 0;
		String pos = "ent";
		String foodLexiconPath = "src/thmp/data/foodLexicon/foodNames.txt";		
		FoodMapNode foodRootNode = new FoodMapNode(initialNodeTokenCount);
		Map<String, String> foodPosMap = buildFoodLexicon(foodRootNode, pos, foodLexiconPath);
		
		String foodTypesPath = "src/thmp/data/foodLexicon/foodTypes.txt";
		Map<String, String> foodTypesMap = buildFoodLexicon(foodRootNode, pos, foodTypesPath);
		foodPosMap.putAll(foodTypesMap);
		
		String foodIngredientsPath = "src/thmp/data/foodLexicon/foodIngredients.txt";
		foodPosMap.putAll(buildFoodLexicon(foodRootNode, pos, foodIngredientsPath));
		
		String equipmentPath = "src/thmp/data/foodLexicon/cookingEquipment.txt";
		Map<String, String> equipmentMap = buildFoodLexicon(foodRootNode, pos, equipmentPath);		
		String equipmentTypePath = "src/thmp/data/foodLexicon/cookingEquipmentType.txt";
		equipmentMap.putAll(buildFoodLexicon(foodRootNode, pos, equipmentTypePath));
		foodPosMap.putAll(equipmentMap);
		
		foodMapList.add(foodPosMap);
		foodMapList.add(foodTypesMap);
		foodTrieList.add(foodRootNode);
		
		String foodActionPath = "src/thmp/data/foodLexicon/cookingAction.txt";
		pos = "verb";
		FoodMapNode actionRootNode = new FoodMapNode(initialNodeTokenCount);
		Map<String, String> cookingActionPosMap = buildFoodLexicon(actionRootNode, pos, foodActionPath);
		
		foodMapList.add(cookingActionPosMap);
		foodTrieList.add(actionRootNode);
		
		foodMapList.add(equipmentMap);
		
		FileUtils.serializeObjToFile(foodMapList, foodMapSerialPath);
		FileUtils.serializeObjToFile(foodTrieList, foodTrieSerialPath);
		
		return foodRootNode;
	}
	
}
