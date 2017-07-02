package food.parse;

import java.util.ArrayList;
import java.util.List;

import com.wolfram.jlink.Expr;

import thmp.parse.ParseRun;
import thmp.parse.ParseState;
import thmp.parse.ParseStruct;
import thmp.parse.DetectHypothesis.Stats;
import thmp.parse.ParseState.ParseStateBuilder;

/**
 * Uses parser to parse recipes. 
 * @author yihed
 * Action[Math["filter"], Math["Type"["flag"], Qualifiers["over", Math["Type"["field"]]]]]
 */
public class RecipeParse {

	
	
	public static void main(String[] args){
		
		String[] inputAr = new String[]{"filter the flag over ring", "combine ring"};
		
		ParseStateBuilder pBuilder = new ParseStateBuilder();
		ParseState parseState = pBuilder.build();
		boolean isVerbose = true;
		Stats stats = null;
		List<String> ingredientsList = new ArrayList<String>();
		ingredientsList.add("flag");
		//initialize recipe graph with list of ingredients
		RecipeGraph recipeGraph = RecipeGraph.initializeRecipeGraph(ingredientsList);
		
		for(String input : inputAr){
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
		}
		System.out.println("RecipeParse - recipeGraph " + recipeGraph);
	}
	
	
}
