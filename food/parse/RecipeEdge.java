package food.parse;

import java.util.List;

import com.wolfram.jlink.Expr;

import thmp.parse.Struct;

/**
 * Describes food processing step .
 * Used to connect FoodState's.
 * Constitutes the edges of a recipe graph. 
 * One edge object could represent *multiple* edges, in connecting
 * multiple products.
 * @author yihed
 */
public class RecipeEdge {

	//Epxr representing this processing step, 
	//e.g. Math["bake"]
	private Expr actionExpr;
	private Struct actionStruct;
	//e.g. bake ... in the oven
	private List<Struct> qualifierStructList;
	
	public RecipeEdge( Struct actionStruct_, List<Struct> qualifiers_){
		//this.actionExpr = action;
		this.actionStruct = actionStruct_;
		this.qualifierStructList = qualifiers_;
	}
	
	public Struct actionStruct(){
		return actionStruct;
	}
	
	public List<Struct> qualifierStructList(){
		return qualifierStructList;
	}
	
	public Expr actionExpr(){
		return actionExpr;
	}
	
	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder();
		sb.append(actionStruct).append(" qualifiers: ").append(qualifierStructList);
		return sb.toString();
	}
}
