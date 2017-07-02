package food.parse;

import java.util.ArrayList;
import java.util.List;

import com.wolfram.jlink.Expr;

import thmp.parse.Struct;

/**
 * State of food items. Create new state every time
 * a food item(s) is processed in some way. Constitutes
 * the vertices of a recipe graph. 
 * @author yihed
 */
public class FoodState {

	private List<FoodState> parentFoodStateList = new ArrayList<FoodState>();
	//could be multiple, e.g. separating egg white and yolk.
	private List<FoodState> childFoodStateList = new ArrayList<FoodState>();
	//name of food name, could be raw ingredient, or processed food, e.g. "rice mixture"
	private String foodName;
	//Struct produced by parse representing this FoodState.
	private Expr foodExpr;
	private Struct foodStruct;
	private RecipeEdge parentEdge;
	private RecipeEdge childEdge;
	
	public FoodState(String foodName_, List<FoodState> parentFoodStateList_){
		this.foodName = foodName_;
		this.parentFoodStateList = parentFoodStateList_;		
	}
	
	public FoodState(String foodName_, Expr foodExpr_, List<FoodState> parentFoodStateList_){
		//this.foodExpr = foodExpr_;
		this.foodName = foodName_;
		this.parentFoodStateList = parentFoodStateList_;		
	}
	//useful for e.g. ingredient state
	public FoodState(String foodName_, Struct foodStruct_){
		this.foodName = foodName_;
		//this.foodExpr = new Expr(Expr.SYMBOL, foodName_);
		this.foodStruct = foodStruct_;
	}
	public FoodState(Struct foodStruct_, List<FoodState> parentsList, RecipeEdge parentEdge_){
		this.foodName = foodStruct_.nameStr();
		this.parentFoodStateList = parentsList;
		//this.foodExpr = new Expr(Expr.SYMBOL, foodStruct_.nameStr());
		this.foodStruct = foodStruct_;
	}
	public void addChildFoodState(FoodState childState){
		this.childFoodStateList.add(childState);
	}
	
	public List<FoodState> childFoodStateList(){
		return childFoodStateList;
	}
	
	public List<FoodState> parentFoodStateList(){
		return parentFoodStateList;
	}
	
	public String foodName(){
		return foodName;
	}

	public RecipeEdge getParentEdge() {
		return parentEdge;
	}

	public void setParentEdge(RecipeEdge parentEdge) {
		this.parentEdge = parentEdge;
	}

	public RecipeEdge getChildEdge() {
		return childEdge;
	}

	public void setChildEdge(RecipeEdge childEdge) {
		this.childEdge = childEdge;
	}
	
	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder();
		sb.append(foodName);
		return sb.toString();
	}
}
