package food.parse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Multimap;
import com.wolfram.jlink.Expr;
import com.wolfram.jlink.ExprFormatException;

import thmp.parse.ParseStruct;
import thmp.parse.ParseStructType;
import thmp.parse.Struct;
import thmp.parse.Struct.ChildRelation;
import thmp.parse.StructH;
import thmp.parse.ParseToWLTree.WLCommandWrapper;
import thmp.parse.WLCommand.PosTerm;
import thmp.parse.WLCommand;
import thmp.parse.WLCommandsList;
import thmp.utils.WordForms;

/**
 * Graph whose vertices are FoodState's, and edges
 * are RecipeEdge's.
 * Holds meta info on the recipe.
 * 
 * @author yihed
 */
public class RecipeGraph {
	
	//ingredients, and whether used or not
	private Map<String, Boolean> ingredientsMap;
	//beginning nodes in 
	//private List<FoodState> ingredientStateList;
	//List of current FoodState's. 
	private List<FoodState> currentStateList;
	
	private RecipeGraph(//List<FoodState> ingredientStateList_, 
			Map<String, Boolean> ingredientsMap_){
		//this.currentStateList = ingredientStateList_;
		this.currentStateList = new ArrayList<FoodState>();
		this.ingredientsMap = ingredientsMap_;
		
		//this.ingredientsSet = new HashSet<String>();
		///ingredientsSet.addAll(ingredientStateList_);
	}
	
	/**
	 * Constructs recipe graph, filling in ingredients states with initial list
	 * of ingredients.
	 * @param ingredientsList
	 * @return
	 */
	public static RecipeGraph initializeRecipeGraph(List<String> ingredientsList){
		//
		//List<FoodState> ingredientsStateList = new ArrayList<FoodState>();
		Map<String, Boolean> ingredientsMap = new HashMap<String, Boolean>();
		
		for(String ingredient : ingredientsList){
			//FoodState foodState = new FoodState(ingredient);
			//ingredientsStateList.add(foodState);
			ingredientsMap.put(ingredient, false);			
		}
		return new RecipeGraph(ingredientsMap);
	}

	/**
	 * Append and update currentStateList with the input instruction
	 * Expr.
	 * 
	 * @param recipeExpr E.g. Action[Math["filter"], Math["Type"["flag"], 
	 * Qualifiers["over", Math["Type"["field"]]]]]
	 */
	public void updateFoodStates(ParseStruct headParseStruct){
		
		//winning wrapper map on the top level
		Multimap<ParseStructType, WLCommandWrapper> wrapperMMap = headParseStruct.getWLCommandWrapperMMap();
		System.out.println("wrapperMMap.values().size "+ wrapperMMap.values().size());
		for(WLCommandWrapper wrapper : wrapperMMap.values()){
			WLCommand wlCommand = wrapper.WLCommand();
			handlePosTermList(wlCommand);
		}		
		//first is head, 2nd and 3rd should be food items
		/*Expr[] exprArgs = recipeExpr.args();
		if(exprArgs.length < 2){
			return;
		}
		//first search under ingredients map, then current food state list.
		//eg Action Head is Expr(Expr.SYMBOL, "Action")
		Expr headExpr = recipeExpr.head();
		Expr[] headExprArgs = headExpr.args();
		String actionStr = "";
		if(headExprArgs.length > 1){
			actionStr = headExprArgs[1].toString();
		}		
		*/	
	}
	
	/**
	 * Should handle different heads, e.g. Action, differently.
	 * @param posList
	 */
	private void handlePosTermList(WLCommand wlCommand){
		//here assume it's Action. Refine
		List<PosTerm> posList = WLCommand.posTermList(wlCommand);
		int triggerTermIndex = WLCommand.triggerWordIndex(wlCommand);
		//List<Struct> knownStructList = new ArrayList<Struct>();
		List<FoodState> knownStateList = new ArrayList<FoodState>();
		//could be utensils, or the name of newly created item
		//Could be multiple, e.g. separate egg into whites and yolk.
		//Or e.g. "in the bowl", or "into a ball"
		List<Struct> unknownStructList = new ArrayList<Struct>();
		Iterator<FoodState> statesListIter = currentStateList.iterator();
		/*if(posList.get(triggerTermIndex).posTermStruct().nameStr().equals("combine")){
			System.out.println("RecipeGraph combine");
		}*/
		int posListSz = posList.size();
		for(int i = 0; i < posListSz; i++){
			PosTerm term = posList.get(i);			
			int posInMap = term.positionInMap();
			if(!term.includeInBuiltString() || triggerTermIndex == i
					|| posInMap == WLCommandsList.AUXINDEX 
					|| posInMap == WLCommandsList.WL_DIRECTIVE_INDEX){
				continue;
			}
			Struct termStruct = term.posTermStruct();
			if(null == termStruct){
				continue;
			}
			if(termStruct.nameStr().equals("baking soda")){
				System.out.println("");
			}
			addStructFoodState(unknownStructList, knownStateList, statesListIter, termStruct);
			
		}
		//need curated utentils & appliance terms!		
		Struct actionStruct;
		//need to check if -1, or some default value!		
		if( triggerTermIndex < 0){
			// improve!
			Map<String, String> map = new HashMap<String, String>();
			map.put("name", "");
			actionStruct = new StructH<Map<String, String>>(map, "unknown");
		}else{
			PosTerm triggerTerm = posList.get(triggerTermIndex);
			actionStruct = triggerTerm.posTermStruct();
		}
		
		//to go along with edge, could be e.g. "until warm"
		List<Struct> applianceStructList = new ArrayList<Struct>();
		List<Struct> productStructList = new ArrayList<Struct>();
		List<Struct> notKnownStructList = new ArrayList<Struct>();
		//construct edge from trigger term and unknown structs
		for(Struct struct : unknownStructList){
			//decide whether utensil, or appliance!
			String structName = struct.nameStr();
			if(isAppliance(structName)){
				applianceStructList.add(struct);
			}else if(isFood(structName)){
				productStructList.add(struct);
			}else{
				notKnownStructList.add(struct);
			}
		}			
		//add not known for now, refine
		//need at least one product, or else should use substitute list, rather than 
		//the actual currentStateList
		//in oven should be appliacen, but form rice mixture should go to product, be smarter
		if(productStructList.isEmpty()){
			productStructList.addAll(notKnownStructList);
		}else{
			applianceStructList.addAll(notKnownStructList);			
		}
		RecipeEdge recipeEdge = new RecipeEdge(actionStruct, applianceStructList);
		for(FoodState parentState : knownStateList){
			parentState.setChildEdge(recipeEdge);
		}
		if(productStructList.isEmpty()){
			//improve! Create some placeholder to represent state
			Map<String, String> map = new HashMap<String, String>();
			map.put("name", "");
			productStructList.add(new StructH<Map<String, String>>(map, ""));
		}
		//need to be careful! Should not allow multiple parents and multiple children at same time!
		
		for(Struct product : productStructList){
			FoodState productState = new FoodState(product, knownStateList, recipeEdge);
			//add parents
			for(FoodState parentState : knownStateList){
				parentState.addChildFoodState(productState);
			}
			currentStateList.add(productState);
		}
		
	}

	private boolean isFood(String structName){
		if("yolk".equals(structName)){
			return true;
		}else if("white".equals(structName)){
			return true;
		}
		return false;
	}
	
	private boolean isAppliance(String structName){
		//////
		if("oven".equals(structName)){
			return true;
		}
		return false;
	}
	/**
	 * @param unknownStructList
	 * @param termStruct
	 * @return
	 */
	private void addStructFoodState(List<Struct> unknownStructList, 
			List<FoodState> knownStateList, Iterator<FoodState> statesListIter, Struct termStruct) {
		
		if(!termStruct.isStructA()){
			addStructFoodState2(unknownStructList, knownStateList, statesListIter, termStruct);
			//consider cases to look into prev1/prev2!? Need to handle e.g. prep!
		}else{
			String structType = termStruct.type();
			//conj
			if(structType.matches("(?:conj|disj)_.+")){
				//if(true)throw new RuntimeException(((Struct)(Struct)termStruct.prev2()).nameStr());
				if(termStruct.prev1NodeType().isTypeStruct()){
					addStructFoodState(unknownStructList, knownStateList, statesListIter, (Struct)termStruct.prev1());
				}
				if(termStruct.prev2NodeType().isTypeStruct()){
					addStructFoodState(unknownStructList, knownStateList, statesListIter, (Struct)termStruct.prev2());
				}
			}
			
		}
		//return structType;
	}

	/**
	 * 
	 * @param unknownStructList
	 * @param knownStateList
	 * @param statesIter  Delete from iter, if previous FoodState corresponding to struct is found.
	 * @param termStruct
	 */
	private void addStructFoodState2(List<Struct> unknownStructList, List<FoodState> knownStateList,
			Iterator<FoodState> statesIter, Struct termStruct) {
		//seek out the foodState this Struct is ascribed to
		String structName = termStruct.nameStr();
		//look amongst ingredients first
		Boolean ingredientUsed = ingredientsMap.get(structName);
		//System.out.println("ingredientUsed "+structName);
		//if(structName.equals("baking soda")) throw new RuntimeException("ingredientUsed "+ingredientUsed);
		if(null != ingredientUsed && !ingredientUsed){
			//**maybe should check if used or not, but shouldn't be used if match on nose
			FoodState foodState = new FoodState(structName, termStruct);
			//add entry to currentStateList
			//currentStateList.add(foodState);
			boolean used = true;
			ingredientsMap.put(structName, used);
			knownStateList.add(foodState);
		}else{
			//look for other food states termStruct could be referring to
			//e.g. rice mixture could refer to something formed earlier.
			boolean knownStateFound = findPreviousFoodState(knownStateList, statesIter, termStruct); 			
			if(!knownStateFound){
				unknownStructList.add(termStruct);
			}
			//unknownStructList.add(termStruct);
		}
		//add children
		addStructChildren(unknownStructList, knownStateList, statesIter, termStruct);
	}
	
	/**
	 * Look for other food states termStruct could be referring to
	 * e.g. rice mixture could refer to something formed earlier
	 * @return
	 */
	private boolean findPreviousFoodState(List<FoodState> knownStateList, Iterator<FoodState> stateIter,
			Struct termStruct){
		String name = termStruct.nameStr();
		String[] foodNameAr = WordForms.splitThmIntoSearchWords(name);
		Set<String> nameSet = new HashSet<String>(Arrays.asList(foodNameAr));
		Iterator<FoodState> currentStateListIter = currentStateList.iterator();
		while(currentStateListIter.hasNext()){
			FoodState foodState = currentStateListIter.next();
			boolean ancestorMatched = stateAncestorMatchStruct(foodState, nameSet);
			if(ancestorMatched){
				//update name if none already, e.g. "flour mixture"
				String newFoodName = foodState.foodName() + " " + name;
				foodState.setFoodName(newFoodName);
				/*if("".equals(foodState.foodName())){
					foodState.setFoodName(name);
				}*/
				knownStateList.add(foodState);
				currentStateListIter.remove();
				return true;
			}			
		}	
		return false;
	}
	
	/**
	 * Looks through ancestors, to see if any their names possibly matches
	 * struct's name.
	 * @param foodState
	 * @param struct
	 * @return
	 */
	private boolean stateAncestorMatchStruct(FoodState foodState, Set<String> structNameSet){

		String foodStateName = foodState.foodName();
		String[] foodNameAr = WordForms.splitThmIntoSearchWords(foodStateName) ;
		System.out.println("foodNameAr - " +Arrays.toString(foodNameAr) + " structNameSet "+structNameSet);
		//Set<String> foodNameSet = new HashSet<String>(Arrays.asList(foodNameAr));
		for(String foodName : foodNameAr){
			//for now, only need one term to agree, e.g. rice and "rice mixture"
			if(structNameSet.contains(foodName)){
				return true;
			}
		}
		List<FoodState> parentsList = foodState.parentFoodStateList();
		for(FoodState parentState : parentsList){
			if(stateAncestorMatchStruct(parentState, structNameSet)){
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Process struct's children, either with more ingredients,
	 * or appliances, e.g. "bake in oven"
	 * @param termStruct
	 */
	private void addStructChildren(List<Struct> unknownStructList, List<FoodState> knownStateList,
			Iterator<FoodState> stateIter, Struct struct){
		List<Struct> children = struct.children();
		List<ChildRelation> childRelationList = struct.childRelationList();
		for(int i = 0; i < children.size(); i++){
			Struct childStruct = children.get(i);
			String childRelationStr = childRelationList.get(i).childRelationStr();
			Struct childFoodStruct = new FoodStruct(childStruct, childRelationStr);
			addStructFoodState2(unknownStructList, knownStateList, stateIter, childFoodStruct);
		}
	}
	
	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder();
		sb.append("currentStateList: \n");
		int counter = 0;
		for(FoodState state : currentStateList){
			sb.append(counter++).append(": ").append(state).append("\n");
			sb.append("EXPR:\n").append(state.toExpr());
			//sb.append(" ParentEdge " + state.getParentEdge());
			//sb.append(" ChildEdge " + state.getChildEdge()).append(" ** ");
		}
		return sb.toString();
	}
}
