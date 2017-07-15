package food.parse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.wolfram.jlink.Expr;

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

	private static final Pattern THEN_PATTERN = Pattern.compile("(?:then)(.+)");
	
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
		
		boolean isVerbose = true;
		Stats stats = null;
		List<String> ingredientsList = new ArrayList<String>();
		//ingredientsList.add("flag");
		String[] ingredientsAr = new String[]{"flour","soda", "cashew","salt", "egg","banana", "oil","onion", "blue cheese",
				"soy sauce", "lemon juice", "basil", "garlic", "hot pepper sauce", "cilantro","potato","carrot","onion","water"};		
		ingredientsList = Arrays.asList(ingredientsAr);		
		
		RecipeGraph recipeGraph = buildRecipeGraph(inputAr, isVerbose, stats, ingredientsList);
		
		System.out.println("RecipeParse - recipeGraph \n" + recipeGraph);		
	}

	/**
	 * @param inputAr
	 * @param isVerbose
	 * @param stats
	 * @param recipeGraph
	 */
	public static RecipeGraph buildRecipeGraph(String[] inputAr, boolean isVerbose, Stats stats, List<String> ingredientsList) {
		//initialize recipe graph with list of ingredients
		RecipeGraph recipeGraph = RecipeGraph.initializeRecipeGraph(ingredientsList);

		//List<ParseStruct> parseStructList = new ArrayList<ParseStruct>();
		
		int inputArLen = inputAr.length;
		for(int j = 0; j < inputArLen; j++){
			String input = inputAr[j];
			//refine
			//input = WordForms.splitDelimPattern().matcher(input).replaceAll(" and ");
			StringBuilder inputSb = new StringBuilder();
			ParseStateBuilder pBuilder = new ParseStateBuilder();
			ParseState parseState = pBuilder.build();
			
			int inputLen = input.length();
			input = processInput(input, inputSb, inputLen);
			
			//parseInput(String st, ParseState parseState, boolean isVerbose, Stats stats){
			ParseRun.parseInput(input, parseState, isVerbose, stats);
			
			ParseStruct headParseStruct = parseState.getHeadParseStruct();
			List<Expr> exprList = new ArrayList<Expr>();
			StringBuilder sb = new StringBuilder(100);
			Expr recipeExpr = null;
			//System.out.println("headParseStruct "+headParseStruct);
			if(null != headParseStruct && !headParseStruct.getWLCommandWrapperMMap().isEmpty()){
				headParseStruct.createStringAndRetrieveExpr(sb, exprList);
				//System.out.println("@@@" + headParseStructStr);
				if(!exprList.isEmpty()){
					recipeExpr = exprList.get(0);
					System.out.println("~+++~ EXPR: \n" + recipeExpr);
				}
				//parseStructList.add(headParseStruct);
				recipeGraph.updateFoodStates(headParseStruct);	
			}else if(j < inputArLen-1){
				//if first word is a preposition, e.g. "in pan, cook potatoes"
				//Better if also check for punctuation.
				List<Struct> structList = parseState.getTokenList();
				System.out.println("structList "+structList);
				if(structList.size() > 0){
					Struct firstStruct = structList.get(0);
					if("pre".equals(firstStruct.type())){
						String nextInput = inputAr[j+1] + " " + input;
						inputAr[j+1] = nextInput;
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
	 * @param input
	 * @param inputSb
	 * @param inputLen
	 * @return
	 */
	private static String processInput(String input, StringBuilder inputSb, int inputLen) {
		Matcher thenMatcher;
		if((thenMatcher=THEN_PATTERN.matcher(input)).matches()){
			//don't want to affect math parser, so pre-process here
			input = thenMatcher.group(1);
		}		
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
