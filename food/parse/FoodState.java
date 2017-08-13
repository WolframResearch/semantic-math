package food.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.wolfram.jlink.Expr;

import thmp.parse.Struct;
import thmp.utils.ExprUtils;
import thmp.utils.WordForms;

/**
 * State of food items. Create new state every time
 * a food item(s) is processed in some way. Constitutes
 * the vertices of a recipe graph. 
 * @author yihed
 */
public class FoodState {

	//private static final FoodState FOODSTATE_SINGLETON = new FoodState("");
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
	
	public FoodState(String foodName_//, List<FoodState> parentFoodStateList_
			){
		this.foodName = foodName_;
		//this.parentFoodStateList = parentFoodStateList_;		
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
		this.parentEdge = parentEdge_;
	}
	
	/**
	 * Create new blank food state, with empty string as name and no struct.
	 * @return
	 */
	public static FoodState createBlankFoodState(){		
		return new FoodState("");
	}
	
	public void setFoodStruct(Struct struct){
		//if(this == FOODSTATE_SINGLETON) return;
		this.foodStruct = struct;
		String newName = "".equals(this.foodName) ? struct.nameStr() : " " + struct.nameStr();
		this.foodName = this.foodName + newName;
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
	
	public void setFoodName(String foodName){
		this.foodName = foodName;
	}
	
	/**
	 * Description of food state at the first level.
	 * @return Either a List Expr, or a singleton Name Expr.
	 * E.g. "{\"until\", Name[\"blended banana mixture\"] }"
	 */
	private static Expr describeAsExpr(FoodState foodState){
		List<Expr> nameExprList = new ArrayList<Expr>();
		//StringBuilder nameSb = new StringBuilder();
		
		Struct foodStruct = foodState.foodStruct;
		//"{\"until\", Name[\"blended banana mixture\"] }"
		String qualifier = "";
		if(null != foodStruct && foodStruct.isFoodStruct()){
			qualifier = ((FoodStruct)foodStruct).qualifier();			
		}
		//nameSb.append("Name[\""+foodState.foodName).append("\"] ");
		String foodName = foodState.foodName;
		/*if(WordForms.getWhiteEmptySpacePattern().matcher(foodName).matches()){
			Random r = new Random();
			foodName = String.valueOf(r.nextInt(300));
		}*/
		Expr[] argExprAr;
		Set<String> pptSet;
		
		//if(foodName.equals("knife")) throw new IllegalStateException(foodStruct.getPropertySet().toString());
		//System.out.println("FoodState - foodStruct " +foodStruct);
		if(null != foodStruct && !(pptSet = foodStruct.getPropertySet()).isEmpty()){
			List<Expr> pptExprList = new ArrayList<Expr>();
			for(String pptStr : pptSet){
				pptExprList.add(new Expr(pptStr));				
			}
			argExprAr = new Expr[]{new Expr(foodName), ExprUtils.listExpr(pptExprList)};
		}else{
			argExprAr = new Expr[]{new Expr(foodName)};
		}
		Expr foodNameExpr = new Expr(new Expr(Expr.SYMBOL, "Name"), argExprAr);
		
		if(null != foodStruct){
			//if("tablespoon".equals(foodStruct.nameStr())) throw new RuntimeException(foodStruct.children().toString());
			for(Struct childStruct : foodStruct.children()){
				//figure out adequate string!!!
				String childStr = childStruct                  .toString();
				nameExprList.add(new Expr(childStr));	
				/*List<String> contentList = childStruct.toString();
				if(null != contentList){
					for(String s : contentList){
						nameExprList.add(new Expr(s));				
					}				
				}*/
			}			
		}
		if(!"".equals(qualifier)){
			//nameSb.insert(0, "\""+qualifier+"\", ").insert(0,"{").append("}");
			nameExprList.add(new Expr(qualifier));
			nameExprList.add(foodNameExpr);
			return ExprUtils.listExpr(nameExprList);
		}		
		return foodNameExpr;
	}
	
	/**
	 * Forms Expr, returns set of rules (or Labeled) used
	 * to describe edges of the graph.  
	 * @return Just set of rules, without an enclosing List.
	 */
	public Expr toExpr(){
		List<Expr> curLevelRules = new ArrayList<Expr>();		
		this.getExprList(curLevelRules);
		//e.g. Graph[{"apple" -> "pudding", Labeled["banana" -> "pudding", "blend"]}, VertexLabels -> "Name"]
		Expr graphHead = new Expr(Expr.SYMBOL, "Graph");
		Expr edgeListExpr = ExprUtils.listExpr(curLevelRules);
		Expr vertexOptionExpr = ExprUtils.ruleExpr(new Expr(Expr.SYMBOL, "VertexLabels"), new Expr("Name"));
		
		return new Expr(graphHead, new Expr[]{edgeListExpr, vertexOptionExpr});
	}
	
	private void getExprList(List<Expr> curLevelRules){
		//List<Expr> curLevelRules = new ArrayList<Expr>();		
		Expr productExpr = describeAsExpr(this);		
		if(!parentFoodStateList.isEmpty()){
			//sb.append("P{");
			for(FoodState parentState : parentFoodStateList){				
				Expr ruleExpr = ExprUtils.ruleExpr(describeAsExpr(parentState), productExpr);				
				if(null != parentEdge){		
					//e.g. Labeled["banana" -> "pudding", "blend"]
					Expr edgeNameExpr = parentEdge.toExpr();
					Expr labelHead = new Expr(Expr.SYMBOL, "Labeled");
					ruleExpr = new Expr(labelHead, new Expr[]{ruleExpr, edgeNameExpr});					
				}
				curLevelRules.add(ruleExpr);
				parentState.getExprList(curLevelRules);
			}			
		}
	}
	
	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder();
		if(null != foodStruct && foodStruct.isFoodStruct()){
			sb.append(" (").append(((FoodStruct)foodStruct).qualifier()).append(") ");
		}
		sb.append(" name["+foodName).append("] ");
		if(null != parentEdge){
			sb.append("<-").append(parentEdge).append("-|");
		}
		if(!parentFoodStateList.isEmpty()){
			sb.append("P{");
			for(FoodState parentState : parentFoodStateList){
				sb.append("[").append(parentState).append("]");
			}
			sb.append("} ");
		}
		
		return sb.toString();
	}
}
