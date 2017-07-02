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
		//inputAr = new String[]{"combine flour and salt", "combine flour mixture"};
		
		boolean isVerbose = true;
		Stats stats = null;
		List<String> ingredientsList = new ArrayList<String>();
		//ingredientsList.add("flag");
		String[] ingredientsAr = new String[]{"flour","soda", "salt", "egg","banana", "oil","onion"};
		
		ingredientsList = Arrays.asList(ingredientsAr);
		
		//initialize recipe graph with list of ingredients
		RecipeGraph recipeGraph = RecipeGraph.initializeRecipeGraph(ingredientsList);
		
		for(String input : inputAr){
			//refine
			//input = WordForms.splitDelimPattern().matcher(input).replaceAll(" and ");
			StringBuilder inputSb = new StringBuilder();
			for(int i = 0; i < input.length(); i++){
				String cStr = String.valueOf(input.charAt(i));
				char c = input.charAt(i);
				if(',' == c){
					inputSb.append(" and");
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
				String headParseStructStr = headParseStruct.createStringAndRetrieveExpr(sb, exprList);
				//System.out.println("@@@" + headParseStructStr);
				if(!exprList.isEmpty()){
					recipeExpr = exprList.get(0);
					System.out.println("~+++~ EXPR: \n" + recipeExpr);
				}
			}
			recipeGraph.updateFoodStates(headParseStruct);
			System.out.println("RecipeParse - recipeGraph \n" + recipeGraph);
		}
		
	}
	
	
}
