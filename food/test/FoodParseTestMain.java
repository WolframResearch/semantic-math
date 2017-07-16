package food.test;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.wolfram.jlink.Expr;

import food.parse.FoodState;
import food.parse.RecipeGraph;
import food.parse.RecipeParse;
import thmp.parse.DetectHypothesis.Stats;

/**
 * Tests food parse. 
 * 
 * @author yihed
 */
public class FoodParseTestMain {
	
	/**
	 * Relying on the Expr string form, check that it contains
	 * certain hosnippets. Not ideal, but can't check exact Expr form, because random
	 * numbers used in names (as of July 2017).
	 * @param inputAr Array of strings with recipes to parse. 
	 * @param desiredSnippets Desired string snippets to check for presence.
	 * @return
	 */
	public static boolean testFoodParse(String input, String[] ingredientsAr, String[] desiredSnippets, int desiredEdgeNum){
		List<String> ingredientsList = Arrays.asList(ingredientsAr);
		
		boolean isVerbose = true;
		Stats stats = null;		
		RecipeGraph recipeGraph = RecipeParse.buildRecipeGraph(input, isVerbose, stats, ingredientsList);
		
		List<FoodState> currentStateList = recipeGraph.currentStateList();		
		if(currentStateList.size() != 1){
			System.out.println("ERROR: currentStateList.size() " + currentStateList.size() + " != 1 for " + input);
			return false;
		}
		FoodState productState = currentStateList.get(0);		
		Expr graphExpr = productState.toExpr();
		System.out.println("graphExpr: " + graphExpr);
		/*check argument size (number of edges in graph)
		Graph[{Labeled[Rule[Name["1708 banana mixture"]}, Rule[VertexLabels, "Name"]];*/
		
		//args don't include Head Symbol. graphExpr.args()[0] is the List of edges supplied to Graph.
		int numEdges = graphExpr.args()[0].args().length;
		if(numEdges != desiredEdgeNum){
			System.out.println("ERROR: numEdges != desiredEdgeNum! numEdges: "+ numEdges + " desiredEdgeNum: "+ desiredEdgeNum);
			return false;
		}
		String graphStr = graphExpr.toString();
		for(String snippet : desiredSnippets){
			if(!graphStr.contains(snippet)){
				System.out.println("ERROR: graphStr does not contain " + snippet);
				return false;
			}
		}		
		return true;
	}
	
	@Test
	public void test1(){
		//String[] inputAr = new String[]{"combine flour and salt", "add banana", "pour batter in bowl","Combine banana mixture and egg"}; 
		String input = "combine flour and salt, add banana, pour batter in bowl, Combine banana mixture and egg";
		String[] ingredientsAr = new String[]{"flour","soda", "salt", "egg","banana", "oil","onion", "blue cheese",
				"soy sauce", "lemon juice", "basil", "garlic", "hot pepper sauce"};
		/*
		 * Account for frequency of words as well?
		 * Graph[{Labeled[Rule[Name["1708 banana mixture"], Name["4599"]], Action["combine"]], Labeled[Rule[Name["1242"], Name["1708 banana mixture"]], 
		 * Action["pour{ in bowl}"]], Labeled[Rule[Name["banana"], Name["1242"]], Action["add"]], Labeled[Rule[Name["751"], Name["1242"]], Action["add"]], 
		 * Labeled[Rule[Name["flour"], Name["751"]], Action["combine"]], Labeled[Rule[Name["salt"], Name["751"]], Action["combine"]], 
		 * Labeled[Rule[Name["egg"], Name["4599"]], Action["combine"]]}, Rule[VertexLabels, "Name"]]
		 */
		String[] desiredSnippets = new String[]{"banana mixture", "combine", "pour", "bowl", "add", "flour", "salt", "egg"};
		int desiredEdgeNum = 7;
		assertTrue(testFoodParse(input, ingredientsAr, desiredSnippets, desiredEdgeNum));		
	}
	
	@Test
	public void test2(){
		/*
		 * Graph[{Labeled[Rule[Name["356"], Name["3664"]], Action["wait{  10 minute}"]], Labeled[Rule[Name["flour"], Name["356"]], Action["combine"]], 
		 * Labeled[Rule[Name["salt"], Name["356"]], Action["combine"]]}, Rule[VertexLabels, "Name"]]
		 */
		//String[] inputAr = new String[]{"combine flour and salt", "wait 10 minutes"};
		String input = "combine flour and salt, wait 10 minutes";
		String[] ingredientsAr = new String[]{"flour","soda", "salt"};
		String[] desiredSnippets = new String[]{"wait", "10 minute", "combine", "flour", "salt"};
		int desiredEdgeNum = 3;
		assertTrue(testFoodParse(input, ingredientsAr, desiredSnippets, desiredEdgeNum));
	}
	
	@Test
	public void test3(){
		/*
		 * Graph[{Labeled[Rule[Name["356"], Name["3664"]], Action["wait{  10 minute}"]], Labeled[Rule[Name["flour"], Name["356"]], Action["combine"]], 
		 * Labeled[Rule[Name["salt"], Name["356"]], Action["combine"]]}, Rule[VertexLabels, "Name"]]
		 */
		//String[] inputAr = new String[]{"soak cashew overnight","place potato and carrot in steamer for 20 minutes", 
		//"combine soaked cashew and steamed vegetable"};
		String input = "soak cashew overnight, place potato and carrot in steamer for 20 minutes, combine soaked cashew and steamed vegetable";
		String[] ingredientsAr = new String[]{"potato","cashew", "carrot"};
		String[] desiredSnippets = new String[]{"soak", "cashew", "place", "potato", "carrot", "steamer", "20 minute", "vegetable"};
		int desiredEdgeNum = 5;
		assertTrue(testFoodParse(input, ingredientsAr, desiredSnippets, desiredEdgeNum));
	}
	
	@Test
	public void test4(){
		String inputStr = "warm tortillas on pan or directly over fire, add garlic and mix until smooth";
		String[] ingredientsAr = new String[]{"tortillas","garlic"};
		String[] desiredSnippets = new String[]{"until", "smooth", "garlic", "mix", "add", "warm", "tortillas"};
		int desiredEdgeNum = 3;
		assertTrue(testFoodParse(inputStr, ingredientsAr, desiredSnippets, desiredEdgeNum));
	}

}
