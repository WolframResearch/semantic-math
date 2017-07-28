package food.parse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.wolfram.jlink.Expr;

import food.utils.FoodLexicon;
import food.utils.FoodLexicon.FoodMapNode;
import thmp.parse.ParseRun;
import thmp.parse.ParseState;
import thmp.parse.ParseStruct;
import thmp.parse.Struct;
import thmp.utils.WordForms;
import thmp.parse.DetectHypothesis.Stats;
import thmp.parse.ParseState.ParseStateBuilder;

/**
 * Uses parser to parse recipes. 
 * @author yihed
 * Action[Math["filter"], Math["Type"["flag"], Qualifiers["over", Math["Type"["field"]]]]]
 */
public class RecipeParse {

	//private static final Pattern THEN_PATTERN = Pattern.compile("(?:then)(.+)");
	private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("([^\\.,!:;]*)([\\.,:!;]{1})([^\\.,!:;]*)");
	private static final Pattern PUNCT_PATTERN = Pattern.compile("[.,!:;]");
	private static final Pattern COMMA_PATTERN = Pattern.compile(",");
	private static final FoodMapNode FOOD_TRIE = FoodLexicon.foodTrie();
	
	/**
	 * A punctuation-separated sentence, along with the punctuation.
	 */
	private static class RecipeSentence{
		String sentence;
		PunctuationType punctuationType;
		
		RecipeSentence(String sentence_, PunctuationType type_){
			this.sentence = sentence_;
			this.punctuationType = type_;
		}
		
		void setSentence(String sentenceStr){
			this.sentence = sentenceStr;
		}
	}
	
	/**
	 * Broadly speaking, type of punctuation. Period
	 * and exclamation point can be grouped together.
	 */
	private enum PunctuationType{
		COMMA, PERIOD;		
		static PunctuationType getType(String punct){
			switch(punct){
			case ",":
				return COMMA;
			default:
				return PERIOD;	
			}
		}		
	}
	
	public static void main(String[] args){
		
		String[] inputAr = new String[]{"filter the flag over ring", "combine flag"};
		//String[] inputAr = new String[]{"filter the flag over ring"};
		inputAr = new String[]{"combine flour, soda and salt."};
		inputAr = new String[]{"combine flour salt", "Stir in eggs and mashed bananas until blended"};
		inputAr = new String[]{"combine flour and salt", "Stir in eggs and mashed bananas until blended","Combine banana mixture and flour mixture"};
		inputAr = new String[]{"combine flour and salt", "Stir in eggs and mashed bananas until blended","Combine banana mixture and flour mixture"};
		inputAr = new String[]{"Heat oil in a skillet over medium heat", "cook and stir onion in the hot oil until softened and transparent"};
		inputAr = new String[]{"cook and stir onion in the hot oil until soft"};
		inputAr = new String[]{"age the blue cheese"};
		inputAr = new String[]{"place soy sauce, lemon juice, and basil in blender"};
		inputAr = new String[]{"place soy sauce, lemon juice, and basil in blender", "stir in hot pepper sauce and garlic", 
				"blend on high speed for 30 seconds"};//"Pour marinade over desired type of meat"
		inputAr = new String[]{"combine flour and salt", "pour batter in bowl"}; //batter should be made to refer to previous mixture		
		inputAr = new String[]{"combine flour and salt", "add banana", "pour batter in bowl","Combine banana mixture and egg"}; 
		inputAr = new String[]{"Tear off a small piece of dough, flatten into a disc"};
		inputAr = new String[]{"Sprinkle yeast over warm water in a large bowl"};
		inputAr = new String[]{"take banana","top on cilantro"};
		inputAr = new String[]{"soak cashew overnight","place potato and carrot in steamer for 20 minutes", 
				"combine soaked cashew and steamed vegetable"};
		//inputAr = new String[]{"soak cashew overnight","place potato and carrot in steamer for 20 minutes","combine cashew and vegetable"};
		//inputAr = new String[]{"place potato and carrot in steamer"};
		inputAr = new String[]{"combine flour and salt", "add banana", "pour batter in bowl"}; 
		inputAr = new String[]{"combine flour and salt", "wait 10 minutes"};
		inputAr = new String[]{"combine flour and salt", "bake in oven"};
		inputAr = new String[]{"take potato", "combine cashew and steamed vegetable"};
		inputAr = new String[]{"warm tortillas on a pan"};
		inputAr = new String[]{"knead on a floured surface until smooth"}; //wrong qualifiers?!
		inputAr = new String[]{"in a large pan over medium heat", "combine water, onion, and garlic",
				"cook until translucent and fragrant"};
		//"cook until translucent and fragrant" "add more liquid if needed"
		inputAr = new String[]{"warm tortillas on pan or directly over fire", "add garlic and mix until smooth"};
		String inputStr = "add tomato, and onion";
		inputStr = "make pizza, fill the middle of the pizza with guacomole";
		inputStr = "combine flour and salt, add banana, pour batter in bowl, Combine banana mixture and egg";
		inputStr = "combine flour and salt, wait 10 minutes";
		inputStr = "warm tortillas on pan or directly over fire, add garlic and mix until smooth";
		inputStr = "cook until translucent and fragrant";
		inputStr = "wash potato, drain and set aside";
		inputStr = "in a large pan over medium heat, add 1/4 cup of the water, onions, and garlic";
		inputStr = "place into the oven and bake for 12 minutes";
		inputStr = "bring a large pot of water to boil, add corn, cook until tender. Drain. Cook corn on preheated grill.";
		//inputStr = "bring a large pot of water to boil, add corn. cook until tender. drain. Cook corn on preheated grill.";
		inputStr = "cut kernels off the cob with a sharp knife.";
		inputStr = "warm tortillas on pan or directly over fire, add garlic and mix until smooth"; //mix!
		inputStr = "Beat together cream cheese and confectioners sugar";
		inputStr = "stir together the garbanzo beans, kidney beans, lemon juice and salt";
		inputStr = "Cover, and refrigerate for about 2 hours";
		inputStr = "lightly dust the dough with flour";
		inputStr = "make crust, Place thin slices of mozzarella over the crust"; //multiple edges for crust?!
		
		
		boolean isVerbose = true;
		Stats stats = null;
		List<String> ingredientsList = new ArrayList<String>();
		String[] ingredientsAr = new String[]{"flour","soda", "cashew","salt", "egg","banana", "oil","onion", "blue cheese",
				"soy sauce", "lemon juice", "basil", "kidney bean","lemon juice","garlic", "hot pepper sauce", 
				"cilantro","potato","carrot","water","crust","corn"//,"garbanzo bean"
				};		
		ingredientsList = Arrays.asList(ingredientsAr);	
		Set<String> ingredientsSet = new HashSet<String>();
		ingredientsSet.addAll(FoodLexicon.ingredientFoodTypesSet());
		ingredientsSet.addAll(ingredientsList);
		RecipeGraph recipeGraph = buildRecipeGraph(inputStr, isVerbose, stats, //ingredientsList
				ingredientsSet);
		
		System.out.println("RecipeParse - recipeGraph \n" + recipeGraph);		
	}

	/**
	 * Creates Graph based on input list of ingredients and recipe instructions.
	 * @param inputAr
	 * @param isVerbose
	 * @param stats
	 * @param recipeGraph
	 */
	public static RecipeGraph buildRecipeGraph(String recipeStr, boolean isVerbose, Stats stats, Collection<String> ingredientsList) {
		//initialize recipe graph with list of ingredients
		RecipeGraph recipeGraph = RecipeGraph.initializeRecipeGraph(ingredientsList);
		//List<ParseStruct> parseStructList = new ArrayList<ParseStruct>();
		List<RecipeSentence> inputList = recipePreprocess(recipeStr);		
		boolean doPreprocess = false;
		
		int inputArLen = inputList.size();
		for(int j = 0; j < inputArLen; j++){
			RecipeSentence sentence = inputList.get(j);
			String curSentenceStr = sentence.sentence; 
			//refine
			//input = WordForms.splitDelimPattern().matcher(input).replaceAll(" and ");
			//StringBuilder inputSb = new StringBuilder();
			ParseStateBuilder pBuilder = new ParseStateBuilder();
			ParseState parseState = pBuilder.build();
			
			//int inputLen = input.length();
			//input = processInput(input, inputSb, inputLen); //combine wih preprocess earlier!
			
			//parseInput(String st, ParseState parseState, boolean isVerbose, Stats stats){
			ParseRun.parseInput(curSentenceStr, parseState, isVerbose, stats, doPreprocess);
			
			ParseStruct headParseStruct = parseState.getHeadParseStruct();
			
			//System.out.println("headParseStruct "+headParseStruct);
			if(null != headParseStruct && !headParseStruct.getWLCommandWrapperMMap().isEmpty()){				
				//parseStructList.add(headParseStruct);
				recipeGraph.updateFoodStates(headParseStruct);	
			}else if(j < inputArLen-1){
				//if first word is a preposition, e.g. "in pan, cook potatoes"
				//Better if also check for punctuation.
				List<Struct> structList = parseState.getTokenList();
				//System.out.println("structList "+structList);
				if(structList.size() > 0){
					Struct firstStruct = structList.get(0);
					if("pre".equals(firstStruct.type()) && sentence.punctuationType == PunctuationType.COMMA){
						RecipeSentence nextSentence = inputList.get(j+1);
						nextSentence.setSentence(nextSentence.sentence + " " + curSentenceStr);
						//String nextInput = inputAr[j+1] + " " + input;
						//inputAr[j+1] = nextInput;
						/*if(!parseStructList.isEmpty()){
							parseStructList.remove(parseStructList.size()-1);
						}*/
					}
				}
			}
		}
		/*for(ParseStruct parseStruct : parseStructList){
			recipeGraph.updateFoodStates(parseStruct);			
		}*/
		return recipeGraph;
	}

	/**
	 * Tokenize recipe.
	 * @param string
	 * @return
	 */
	private static List<RecipeSentence> recipePreprocess(String recipeStr) {
		if(WordForms.getWhiteEmptySpacePattern().matcher(recipeStr).matches()){
			return new ArrayList<RecipeSentence>();
		}
		List<RecipeSentence> sentenceList = new ArrayList<RecipeSentence>();
		recipeStr = recipeStr.toLowerCase();
		//distancing the words
		String[] wordsArray = WordForms.getWhiteNonEmptySpaceNotAllPattern().split(PUNCTUATION_PATTERN.matcher(recipeStr).replaceAll("$1 $2 $3"));
		int wordsArrayLen = wordsArray.length;
		StringBuilder sentenceSB = new StringBuilder(30);
		
		for(int i = 0; i < wordsArrayLen; i++){
			String word = wordsArray[i];
			if(PUNCT_PATTERN.matcher(word).matches()){
				if(sentenceSB.length() < 2){
					continue;
				}
				//boolean sentenceAdded = false;
				if(COMMA_PATTERN.matcher(word).matches()){
					//check for and in next term
					//check for "and"
					if(i < wordsArrayLen-1){
						//if(true) throw new IllegalStateException(Arrays.toString(wordsArray));
						//next word is food, remove comma
						if(FOOD_TRIE.getTokenCount(wordsArray, i+1, wordsArrayLen) > 0
								){
							sentenceSB.append("and ");							
							continue;
						}else if("and".equals(wordsArray[i+1])){
							continue;
						}
					}					
				}
				//remove trailing space
				String sentenceStr = sentenceSB.substring(0, sentenceSB.length()-1);				
				sentenceList.add(new RecipeSentence(sentenceStr, PunctuationType.getType(word)));
				sentenceSB = new StringBuilder(30);				
				if(i < wordsArrayLen-1 && "then".equals(wordsArray[i+1])){
					//don't want to affect math parser, so remove "then" here instead of in main parser.
					i++;
				}
				continue;
			}
			sentenceSB.append(word).append(" ");			
		}
		if(sentenceSB.length() > 0){
			sentenceList.add(new RecipeSentence(sentenceSB.toString(), PunctuationType.PERIOD));
		}
		return sentenceList;
	}

	/**
	 * @param input
	 * @param inputSb
	 * @param inputLen
	 * @deprecated July 2017
	 * @return
	 */
	private static String processInput(String input, StringBuilder inputSb, int inputLen) {
		/*Matcher thenMatcher;
		if((thenMatcher=THEN_PATTERN.matcher(input)).matches()){
			//don't want to affect math parser, so pre-process here
			input = thenMatcher.group(1);
		}*/
		for(int i = 0; i < inputLen; i++){
			//String cStr = String.valueOf(input.charAt(i));
			char c = input.charAt(i);
			if(',' == c){
				if(i+5 < inputLen){
					String nextChars = input.substring(i+1, i+5);
					if(!nextChars.contains("and")){
						inputSb.append(" and");							
					}
				}else{
					inputSb.append(" and");
				}
			}else{
				inputSb.append(c);
			}
		}
		input = inputSb.toString();
		return input;
	}
	
}
