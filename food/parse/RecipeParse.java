package food.parse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.wolfram.jlink.Expr;

import thmp.parse.ParseRun;
import thmp.parse.ParseState;
import thmp.parse.ParseStruct;
import thmp.utils.WordForms;
import thmp.parse.DetectHypothesis.Stats;
import thmp.parse.ParseState.ParseStateBuilder;

/**
 * Uses parser to parse recipes. 
 * @author yihed
 * Action[Math["filter"], Math["Type"["flag"], Qualifiers["over", Math["Type"["field"]]]]]
 */
public class RecipeParse {

	
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
		inputAr = new String[]{"combine flour and salt", "add banana", "pour batter in bowl"}; 
		inputAr = new String[]{"combine flour and salt", "add banana", "pour batter in bowl","Combine banana mixture and egg"}; 
		inputAr = new String[]{"Tear off a small piece of dough, flatten into a disc"};
		inputAr = new String[]{"Sprinkle yeast over warm water in a large bowl"};
		inputAr = new String[]{"take banana","top on cilantro"};
		inputAr = new String[]{"soak cashew overnight","place potato and carrot in steamer for 20 minutes", 
				"combine soaked cashew and steamed vegetable"};
		
		boolean isVerbose = true;
		Stats stats = null;
		List<String> ingredientsList = new ArrayList<String>();
		//ingredientsList.add("flag");
		String[] ingredientsAr = new String[]{"flour","soda", "cashew","salt", "egg","banana", "oil","onion", "blue cheese",
				"soy sauce", "lemon juice", "basil", "garlic", "hot pepper sauce", "cilantro","potato","carrot"};
		
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
		for(String input : inputAr){
			//refine
			//input = WordForms.splitDelimPattern().matcher(input).replaceAll(" and ");
			StringBuilder inputSb = new StringBuilder();
			int inputLen = input.length();
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
			ParseStateBuilder pBuilder = new ParseStateBuilder();
			ParseState parseState = pBuilder.build();
			//parseInput(String st, ParseState parseState, boolean isVerbose, Stats stats){
			ParseRun.parseInput(input, parseState, isVerbose, stats);
			
			ParseStruct headParseStruct = parseState.getHeadParseStruct();
			List<Expr> exprList = new ArrayList<Expr>();
			StringBuilder sb = new StringBuilder(100);
			Expr recipeExpr = null;
			if(null != headParseStruct){
				headParseStruct.createStringAndRetrieveExpr(sb, exprList);
				//System.out.println("@@@" + headParseStructStr);
				if(!exprList.isEmpty()){
					recipeExpr = exprList.get(0);
					System.out.println("~+++~ EXPR: \n" + recipeExpr);
				}
			}
			recipeGraph.updateFoodStates(headParseStruct);			
		}
		return recipeGraph;
	}
	
	
}
