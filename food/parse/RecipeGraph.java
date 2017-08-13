package food.parse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.Multimap;
import com.wolfram.jlink.Expr;
import com.wolfram.jlink.ExprFormatException;

import food.parse.FoodStruct.FoodStructType;
import food.utils.FoodLexicon;
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
	
	//set of verbs that can represent action to prior FoodState, e.g. "add".
	private static final Set<String> VERB_PRIOR_SET;
	//prepositions denoting source of actions
	private static final Set<String> ACTION_SOURCE_SET;
	//prepositions denoting target of actions
	private static final Set<String> ACTION_TARGET_SET;		
	private static final Pattern TIME_PATTERN = Pattern.compile(".*(?:second|minute|hour|day|overnight)s*.*");
	private static final Pattern CONJ_DISJ_TYPE_PATTERN = Pattern.compile("(?:conj|disj)_.+");
	private static final Random randomNumGenerator = new Random();
	private static final Set<String> QUANTITY_MAP;
	
	//ingredients, and whether used or not
	private Map<String, Boolean> ingredientsMap;
	//List of current FoodState's. 
	private List<FoodState> currentStateList;
	//immediate prior FoodState, to be used in next parse, 
	//if next parse makes implicit reference to lastFoodState.
	private FoodState lastFoodState = FoodState.createBlankFoodState();
	//list of recipe parsed expressions created used to create this RecipeGraph.
	private List<String> parsedExpressionList;
	
	static{
		String[] verbPriorAr = new String[]{"add", "drain", "stir in"};
		VERB_PRIOR_SET = new HashSet<String>(Arrays.asList(verbPriorAr));
		String[] actionSourceAr = new String[]{"from"};
		String[] actionTargetAr = new String[]{"to", "into", "onto"};
		ACTION_SOURCE_SET = new HashSet<String>(Arrays.asList(actionSourceAr));
		ACTION_TARGET_SET = new HashSet<String>(Arrays.asList(actionTargetAr));
		String[] quantitySet = new String[]{"tablespoon", "teaspoon"};
		QUANTITY_MAP = new HashSet<String>(Arrays.asList(quantitySet));
	}
	
	/**
	 * Static factory. Private constructor. 
	 * @param ingredientsMap_
	 */
	private RecipeGraph(//List<FoodState> ingredientStateList_, 
			Map<String, Boolean> ingredientsMap_){
		//this.currentStateList = ingredientStateList_;
		this.currentStateList = new ArrayList<FoodState>();
		this.ingredientsMap = ingredientsMap_;		
		this.parsedExpressionList = new ArrayList<String>();
	}
	
	/**
	 * Constructs recipe graph, filling in ingredients states with initial list
	 * of ingredients.
	 * @param ingredientsList
	 * @return
	 */
	public static RecipeGraph initializeRecipeGraph(Collection<String> ingredientsList){
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
		
		List<Expr> exprList = new ArrayList<Expr>();
		StringBuilder sb = new StringBuilder(100);
		Expr recipeExpr = null;
		headParseStruct.createStringAndRetrieveExpr(sb, exprList);
		//System.out.println("@@@" + headParseStructStr);
		if(!exprList.isEmpty()){
			parsedExpressionList.add(exprList.get(0).toString());
			recipeExpr = exprList.get(0);
			System.out.println("~+++~ EXPR: \n" + recipeExpr);
		}
		//winning wrapper map on the top level
		Multimap<ParseStructType, WLCommandWrapper> wrapperMMap = headParseStruct.getWLCommandWrapperMMap();
		//System.out.println("wrapperMMap.values().size "+ wrapperMMap.values().size());
		for(Map.Entry<ParseStructType, WLCommandWrapper> entry : wrapperMMap.entries()){
			if(entry.getKey() == ParseStructType.NONE){
				continue;
			}
			WLCommandWrapper wrapper = entry.getValue();
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
		//System.out.println("RG posList "+ posList);
		int triggerTermIndex = WLCommand.triggerWordIndex(wlCommand);
		//List<Struct> knownStructList = new ArrayList<Struct>();
		List<FoodState> knownStateList = new ArrayList<FoodState>();
		//could be utensils, or the name of newly created item
		//Could be multiple, e.g. separate egg into whites and yolk.
		//Or e.g. "in the bowl", or "into a ball"
		List<Struct> unknownStructList = new ArrayList<Struct>();
		List<FoodStruct> actionSourceList = new ArrayList<FoodStruct>(); 
		List<FoodStruct> actionTargetList = new ArrayList<FoodStruct>();
		/*if(posList.get(triggerTermIndex).posTermStruct().nameStr().equals("combine")){
			System.out.println("RecipeGraph combine");
		}*/

		//to go along with edge, could be e.g. "until warm", "in oven"
		List<Struct> edgeQualifierStructList = new ArrayList<Struct>();	
		//triggerSubject, subject of the trigger term, e.g. "potatoes" in "smash potatoes"
		boolean prevTermIsTrigger = false;
		//whether lastFoodState has been added to knownStateList.
		boolean lastStateUsed = false;
		int posListSz = posList.size();
		for(int i = 0; i < posListSz; i++){
			PosTerm term = posList.get(i);			
			int posInMap = term.positionInMap();
			if(!term.includeInBuiltString() || triggerTermIndex == i
					|| posInMap == WLCommandsList.AUXINDEX 
					|| posInMap == WLCommandsList.WL_DIRECTIVE_INDEX){
				if(triggerTermIndex == i){
					prevTermIsTrigger = true;
				}
				continue;
			}
			Struct termStruct = term.posTermStruct();
			if(null == termStruct){
				continue;
			}			
			lastStateUsed = addStructFoodState(unknownStructList, knownStateList, actionSourceList, actionTargetList,
					edgeQualifierStructList, prevTermIsTrigger, termStruct);	
			prevTermIsTrigger = false;
		}				
		Struct actionStruct;
		//need to check if -1, or some default value!		
		if(triggerTermIndex < 0){
			// improve!
			Map<String, String> map = new HashMap<String, String>();
			map.put("name", "");
			actionStruct = new StructH<Map<String, String>>(map, "unknown");
		}else{
			PosTerm triggerTerm = posList.get(triggerTermIndex);
			actionStruct = triggerTerm.posTermStruct();
			String actionStructName = actionStruct.nameStr();
			//if actionStruct has child, e.g. "stir in", "combine with",
			//means subject refers to immediately prior product.
			//Or verb alone represents action to prior FoodState, e.g. "add".
			//but only if not explicit "add .. to .." <--check more explicit than 
			//notKnownStructList empty?
			if(actionStruct.has_child() || VERB_PRIOR_SET.contains(actionStructName) && actionTargetList.isEmpty()){
				//purge previous known states? Since they probably don't refer to prior food states?
				knownStateList.add(lastFoodState);
				removeLastFoodStateFromCurrentList();
				lastStateUsed = true;
				for(Struct childStruct : actionStruct.children()){
					edgeQualifierStructList.add(childStruct);
					//System.out.println("RecipeGraph childStruct "+childStruct );
				}
			}
		}		
		List<Struct> productStructList = new ArrayList<Struct>();
		List<Struct> notKnownStructList = new ArrayList<Struct>();
		//construct edge from trigger term and unknown structs
		//System.out.println("RecipeGraph - unknownStructList "+unknownStructList);
		for(Struct struct : unknownStructList){
			//decide whether utensil, or appliance!
			String structName = struct.nameStr();
			/*if(isAppliance(structName)){	
				edgeQualifierStructList.add(struct);
			}*/
			boolean structAdded = addIfApplianceStruct(struct, edgeQualifierStructList);
			if(!structAdded){
				if(isFood(structName)){
					lastStateUsed = addStructToList(knownStateList, productStructList, edgeQualifierStructList, struct, lastStateUsed) || lastStateUsed;
				}else{
					lastStateUsed = addStructToList(knownStateList, notKnownStructList, edgeQualifierStructList, struct, lastStateUsed) || lastStateUsed;
				}
			}			
		}		
		//System.out.println("RecipeGraph - notKnownStructList "+notKnownStructList);
		//add not known for now, refine
		//need at least one product, or else should use substitute list, rather than 
		//the actual currentStateList
		//in oven should be appliacen, but form rice mixture should go to product, be smarter
		
		/*notKnownStructList elements should be added to edgeQualifierStructList, if food struct*/
		/*for(Struct notKnownStruct : notKnownStructList){
			if(isEdgeQualifier(notKnownStruct)){
				//e.g. bake for 20 minutes
				edgeQualifierStructList.add(notKnownStruct);
			}else{
				//e.g. "form rice mixture into balls"
				productStructList.add(notKnownStruct);
			}
		}*/
		if(productStructList.isEmpty()){
			productStructList.addAll(notKnownStructList);
		}else{
			edgeQualifierStructList.addAll(notKnownStructList);			
		}
		
		if(knownStateList.isEmpty()){
			knownStateList.add(lastFoodState);
			removeLastFoodStateFromCurrentList();
		}
		RecipeEdge recipeEdge = new RecipeEdge(actionStruct, edgeQualifierStructList);

		for(FoodState parentState : knownStateList){
			parentState.setChildEdge(recipeEdge);
		}
		//target of edge.
		if(productStructList.isEmpty()){
			//improve! Create some placeholder to represent state
			Map<String, String> map = new HashMap<String, String>();					
			map.put("name", String.valueOf(randomNumGenerator.nextInt(5000))); //graph thinks same vertex, if same as previous!! need unique identifier!
			//map.put("name", ""); //graph thinks same vertex, if same as previous!! need unique identifier!
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
			//try to pick out the most likely state?
			lastFoodState = productState;
		}		
	}

	/**
	 * 
	 */
	private void removeLastFoodStateFromCurrentList() {
		int currentStateListSz = currentStateList.size();
		if(currentStateListSz > 0){
			//this assumes the lastFoodState is the last added to list
			//need to update if that's no longer true.
			currentStateList.remove(currentStateListSz-1);
		}
	}

	/**
	 * Whether the struct qualifies edge.
	 * e.g. "bake in oven", "wait 20 minutes"
	 * @param notKnownStruct
	 * @return
	 */
	private boolean isEdgeQualifier(Struct struct) {
		if(struct.isFoodStruct() && !"".equals(((FoodStruct)struct).qualifier())){
			//maybe check the struct's qualifier string, such as "in", "for" etc
			// && !"".equals(((FoodStruct)notKnownStruct).qualifier())
			return true;
		}
		String structName = struct.nameStr();
		if(TIME_PATTERN.matcher(structName).matches()){			
			return true;
		}
		return false;
	}

	/**
	 * Determines which list, whether known state, product list, etc, to add to for each struct.
	 * @param knownStateList
	 * @param addToStructList
	 * @param struct
	 * @param lastStateUsed Whether lastFoodState has been added in building this edge step.
	 */
	private boolean addStructToList(List<FoodState> knownStateList, List<Struct> addToStructList, 
			List<Struct> edgeQualifierStructList, Struct struct, boolean lastStateUsed) {
		boolean structAdded = false;
		//if(true) throw new RuntimeException();
		if(struct.isFoodStruct()){
			FoodStruct foodStruct = (FoodStruct)struct;			
			if(isEdgeQualifier(struct)){
				////e.g. bake for 20 minutes
				edgeQualifierStructList.add(struct);
				structAdded = true;
			}else if(!lastStateUsed && (foodStruct.foodStructType() == FoodStructType.SUBJECT
					//e.g. subject e.g. "wash veggies", "bake batter'
					|| knownStateList.isEmpty())){
				//System.out.println("RecipeGraph - foodStruct "+foodStruct);
				//can't set struct, as will affect edge formation.<--not if haven't formed Expr's.
				//need to avoid name clashes, in case struct has same name as some previous one.
				lastFoodState.setFoodStruct(foodStruct);//HERE
				removeLastFoodStateFromCurrentList();
				knownStateList.add(lastFoodState);			
				structAdded = true;
				lastStateUsed = true;
			}
		}
		if(!structAdded){
			addToStructList.add(struct);					
		}
		return lastStateUsed;
	}

	private boolean isFood(String structName){
		return FoodLexicon.foodMap().containsKey(structName);
	}
	
	/**
	 * Add struct to edge qualifier list if e.g. appliance.
	 * @param struct
	 * @param edgeQualifierStructList
	 * @return Whether struct was added to the edge qualifier list.
	 */
	private boolean addIfApplianceStruct(Struct struct, List<Struct> edgeQualifierStructList){	
		//System.out.println("adding struct "+struct);
		//if(true) throw new IllegalStateException();
		String structName = struct.nameStr();
		if(FoodLexicon.equipmentMap().containsKey(structName)){
			edgeQualifierStructList.add(struct);
			return true;
		}
		boolean structAdded = false;
		String structType = struct.type();
		if(WordForms.CONJ_DISJ_PATTERN.matcher(structType).matches()){
			structAdded = addIfApplianceStruct((Struct)struct.prev1(), edgeQualifierStructList) || structAdded;
			structAdded = addIfApplianceStruct((Struct)struct.prev2(), edgeQualifierStructList) || structAdded;
		}else if("prep".equals(structType)){
			//discussed with others, "prep" should usually indicate edge qualifier
			edgeQualifierStructList.add(struct);
			System.out.println("recipeGraph adding prep "+struct);
			structAdded = true;
		}
		//return FoodLexicon.equipmentMap().containsKey(structName);
		return structAdded;
	}
	
	/**
	 * Whether certain food states have been added.
	 */
	private class FoodStateAdded{
		
		boolean lastStateUsed;
		boolean structAdded;
		
		FoodStateAdded(boolean structAdded_, boolean lastStateUsed_){
			this.structAdded = structAdded_;
			this.lastStateUsed = lastStateUsed_;
		}
	}
	
	/**
	 * FoodStateAdded foodStateAdded = addStructFoodState2(unknownStructList, knownStateList, actionSourceList, actionTargetList, 
							prevTermIsTrigger, (Struct)termStruct.prev2());
	 * @param unknownStructList
	 * @param termStruct
	 * @return
	 */
	private boolean addStructFoodState(List<Struct> unknownStructList, 
			List<FoodState> knownStateList, List<FoodStruct> actionSourceList, List<FoodStruct> actionTargetList,
			List<Struct> edgeQualifierStructList,
			boolean prevTermIsTrigger, Struct termStruct, String... qualifierString) {
		
		boolean lastStateUsed = false;
		
		if(!termStruct.isStructA()){
			FoodStateAdded foodStateAdded = addStructFoodState2(unknownStructList, knownStateList, actionSourceList, actionTargetList, 
					edgeQualifierStructList, prevTermIsTrigger, termStruct, qualifierString);
			lastStateUsed = foodStateAdded.lastStateUsed || lastStateUsed;
			if(!foodStateAdded.structAdded){
				unknownStructList.add(termStruct);
			}
		}else{
			String structType = termStruct.type();
			
			if(CONJ_DISJ_TYPE_PATTERN.matcher(structType).matches()){
				//if(true)throw new RuntimeException(((Struct)(Struct)termStruct.prev2()).nameStr());
				if(termStruct.prev1NodeType().isTypeStruct()){
					lastStateUsed = addStructFoodState(unknownStructList, knownStateList, actionSourceList, actionTargetList,
							edgeQualifierStructList, prevTermIsTrigger, (Struct)termStruct.prev1(), qualifierString) || lastStateUsed;
				}
				if(termStruct.prev2NodeType().isTypeStruct()){
					lastStateUsed = addStructFoodState(unknownStructList, knownStateList, actionSourceList, actionTargetList,
							edgeQualifierStructList, prevTermIsTrigger, (Struct)termStruct.prev2(), qualifierString) || lastStateUsed;
				}
				//conj can have children nodes, e.g. "bake A and B in oven"
				lastStateUsed = addStructChildren(unknownStructList, knownStateList, actionSourceList, actionTargetList, 
						edgeQualifierStructList, termStruct) || lastStateUsed;
			}else{
				if("adj".equals(structType)){
					//e.g. "translucent" in "cook until translucent"
					edgeQualifierStructList.add(termStruct);					
				}
				if("prep".equals(structType)){
					////////////////
					/*addStructFoodState(unknownStructList, knownStateList, actionSourceList, actionTargetList,
							edgeQualifierStructList, prevTermIsTrigger, (Struct)termStruct.prev2());*/
					FoodStateAdded foodStateAdded = addStructFoodState2(unknownStructList, knownStateList, actionSourceList, actionTargetList, 
							edgeQualifierStructList, prevTermIsTrigger, (Struct)termStruct.prev2(), qualifierString);
					lastStateUsed = lastStateUsed || foodStateAdded.lastStateUsed;
					if(!foodStateAdded.structAdded){
						edgeQualifierStructList.add(termStruct);
					}
				}else{
					if(termStruct.prev1NodeType().isTypeStruct()){
						lastStateUsed = addStructFoodState(unknownStructList, knownStateList, actionSourceList, actionTargetList, 
								edgeQualifierStructList, prevTermIsTrigger, (Struct)termStruct.prev1(), qualifierString) 
								|| lastStateUsed;
									
					}
					if(termStruct.prev2NodeType().isTypeStruct()){
						lastStateUsed = addStructFoodState(unknownStructList, knownStateList, actionSourceList, actionTargetList, 
								edgeQualifierStructList, prevTermIsTrigger, (Struct)termStruct.prev2(), qualifierString) 
								|| lastStateUsed;
						
					}
				}
			}
		}		
		return lastStateUsed;
		//return structType;
	}

	/**
	 * 
	 * @param unknownStructList
	 * @param knownStateList
	 * @param actionSourceList
	 * @param actionTargetList
	 * @param prevTermIsTrigger
	 * @param termStruct
	 * @param structQualifier qualifier to struct, e.g. "over" in "over pan"
	 * @return
	 */
	private FoodStateAdded addStructFoodState2(List<Struct> unknownStructList, List<FoodState> knownStateList,
			List<FoodStruct> actionSourceList, List<FoodStruct> actionTargetList, 
			List<Struct> edgeQualifierStructList, //List<FoodToken> foodTokenList, 
			boolean prevTermIsTrigger, Struct termStruct, String...structQualifier) {
		
		if(termStruct.isStructA()){
			return new FoodStateAdded(false, false);
		}
		//seek out the foodState this Struct is ascribed to
		String structName = termStruct.nameStr();
		//look amongst ingredients first
		Boolean ingredientUsed = ingredientsMap.get(structName);
		Struct structToAdd;
		boolean lastStateUsed = false;
		boolean structAdded = false;
		//Struct structToAdd = termStruct;
		//boolean addChildren = true;
		//System.out.println("RecipeGraph termStruct " + termStruct + " " +prevTermIsTrigger);
		//remember relation to parent, if applicable.
		if(prevTermIsTrigger){
			structToAdd = new FoodStruct(termStruct, FoodStructType.SUBJECT);
			//if(true) throw new RuntimeException("structToAdd "+structToAdd);
		}else if(structQualifier.length > 0){
			structToAdd = new FoodStruct(termStruct, structQualifier[0]);
		}else{
			structToAdd = termStruct;
		}
		//System.out.println("ingredientUsed "+structName);
		//if(structName.equals("baking soda")) throw new RuntimeException("ingredientUsed "+ingredientUsed);
		if(null != ingredientUsed && !ingredientUsed){			
			FoodState foodState = new FoodState(structName, structToAdd);
			//add entry to currentStateList
			//currentStateList.add(foodState);
			boolean used = true;
			ingredientsMap.put(structName, used);
			knownStateList.add(foodState);
			structAdded = true;
		}else{
			//look for other food states termStruct could be referring to
			//e.g. rice mixture could refer to something formed earlier.
			FoodStateAdded foodStateAdded = findPreviousFoodState(knownStateList, termStruct, unknownStructList,
					structToAdd); 		
			lastStateUsed = foodStateAdded.lastStateUsed || lastStateUsed;
			structAdded = foodStateAdded.structAdded;
			if(!structAdded && QUANTITY_MAP.contains(structName)){
				//check for quantities, e.g. "tablespoon"
				FoodState foodState = new FoodState(structName, structToAdd);
				structToAdd = findQuantityChildren(structToAdd);
				
				//if(true) throw new RuntimeException(structToAdd.toString());
				knownStateList.add(foodState);
				structAdded = true;
			}
		}
		lastStateUsed = addStructChildren(unknownStructList, knownStateList, actionSourceList, actionTargetList, 
				edgeQualifierStructList, structToAdd) || lastStateUsed;
		return new FoodStateAdded(structAdded, lastStateUsed);
	}
	
	/**
	 * Adjusts the struct to select the relevant children of a quantity, e.g.
	 * "teaspoon". Returns updated Struct.
	 */
	private static Struct findQuantityChildren(Struct quantityStruct){
		
		Struct newStruct = quantityStruct.copy();
		List<Struct> children = newStruct.children();
		List<ChildRelation> childRelationList = newStruct.childRelationList();
		int childrenSz = children.size();
		int indexRemoved = 0;
		for(int i = 0; i < childrenSz; i++){			
			if(FoodLexicon.isFoodStruct(children.get(i))){
				indexRemoved = i;
				quantityStruct.children().clear();
				quantityStruct.children().add(children.get(indexRemoved));		
				quantityStruct.childRelationList().clear();
				quantityStruct.childRelationList().add(childRelationList.get(indexRemoved));
				
				children.remove(indexRemoved);
				childRelationList.remove(indexRemoved);				
				break;
			}
		}
		return newStruct;
	}
	
	/**
	 * Look for other food states termStruct could be referring to
	 * e.g. rice mixture could refer to something formed earlier
	 * @param structToAdd is the struct to add to unknownStructList, if applicable
	 * @return lastStateUsed whether the lastFoodState had been used
	 * for a node in this instruction.
	 */
	private FoodStateAdded findPreviousFoodState(List<FoodState> knownStateList,// Iterator<FoodState> stateIter,
			Struct termStruct, List<Struct> unknownStructList, Struct structToAdd){
		String name = termStruct.nameStr();
		//String[] foodNameAr = WordForms.splitThmIntoSearchWords(name);
		//Set<String> nameSet = new HashSet<String>(Arrays.asList(foodNameAr));
		Iterator<FoodState> currentStateListIter = currentStateList.iterator();
		boolean lastStateUsed = false;
		boolean structAdded = false;
		while(currentStateListIter.hasNext()){
			FoodState foodState = currentStateListIter.next();
			boolean ancestorMatched = stateAncestorMatchStruct(foodState, name);
			if(ancestorMatched){
				//update name if none already, e.g. "flour mixture"
				String newFoodName = foodState.foodName() + " " + name;
				foodState.setFoodName(newFoodName);
				/*if("".equals(foodState.foodName())){
					foodState.setFoodName(name);
				}*/
				if(foodState == lastFoodState){
					lastStateUsed = true;
					//System.out.println("RecipeGraph - using lastFoodState ");
					//throw new RuntimeException();
				}
				knownStateList.add(foodState);
				currentStateListIter.remove();
				//return true;
				structAdded = true;
				break;
			}			
		}
		/*if(!structAdded){
			//unless is action subject, e.g. "pour batter ..." in which case make 
			//it refer to the last state added to currentStateList.
			unknownStructList.add(structToAdd);			
		}*/
		
		return new FoodStateAdded(structAdded, lastStateUsed);
	}
	
	/**
	 * Looks through ancestors, to see if any their names possibly matches
	 * struct's name.
	 * @param foodState Current state under consideration.
	 * @param sourceFoodName name of the food we are trying to find match for
	 * @return
	 */
	private boolean stateAncestorMatchStruct(FoodState foodState, String sourceFoodName //, Set<String> structNameSet
			){
		String foodStateName = foodState.foodName();
		//String[] foodNameAr = WordForms.splitThmIntoSearchWords(foodStateName) ;
		//System.out.println("foodNameAr - " +Arrays.toString(foodNameAr) + " structNameSet "+structNameSet);
		//Set<String> foodNameSet = new HashSet<String>(Arrays.asList(foodNameAr));
		/*for(String foodName : foodNameAr){
			//for now, only need one term to agree, e.g. rice and "rice mixture"
			if(structNameSet.contains(foodName)){
				return true;
			}
		}*/
		//System.out.println("foodStateName "+foodStateName + " sourceFoodName "+sourceFoodName);
		if(!WordForms.getWhiteEmptySpacePattern().matcher(foodStateName).matches() 
				&& (sourceFoodName.contains(foodStateName) || foodStateName.contains(sourceFoodName))){
			//"rice mixture" contains "rice", but hot sauce shouldn't match soy sauce
			return true;
		}
		List<FoodState> parentsList = foodState.parentFoodStateList();
		for(FoodState parentState : parentsList){
			if(stateAncestorMatchStruct(parentState, sourceFoodName)){
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Process struct's children, either with more ingredients,
	 * or appliances, e.g. "bake in oven"
	 * @param termStruct
	 * @param List<FoodState> sourceFoodStateList, source of action 
	 * @param actionTargetList, target of action. E.g. "to soup" in "add basil to soup"
	 */
	private boolean addStructChildren(List<Struct> unknownStructList, List<FoodState> knownStateList,
			List<FoodStruct> actionSourceList, List<FoodStruct> actionTargetList, 
			List<Struct> edgeQualifierList, //List<FoodToken> foodTokenList,
			Struct struct){
		List<Struct> children = struct.children();
		List<ChildRelation> childRelationList = struct.childRelationList();
		boolean isPrevTermTrigger = false;
		boolean lastStateUsed = false;
		//System.out.println(" struct  childFoodStruct children "+children);
		for(int i = 0; i < children.size(); i++){
			Struct childStruct = children.get(i);
			String childRelationStr = childRelationList.get(i).childRelationStr();
			FoodStruct childFoodStruct = new FoodStruct(childStruct, childRelationStr);
			//e.g. "to" in "... to apple pie"
			if(ACTION_SOURCE_SET.contains(childRelationStr)){
				actionSourceList.add(childFoodStruct);
				//foodTokenList.add(new FoodToken(childFoodStruct, FoodTokenType.ACTION_SOURCE));
			}else if(ACTION_TARGET_SET.contains(childRelationStr)){
				actionTargetList.add(childFoodStruct);
			}
			
			lastStateUsed = addStructFoodState(unknownStructList, knownStateList, actionSourceList, actionTargetList, 
					edgeQualifierList, isPrevTermTrigger, childFoodStruct, childRelationStr) || lastStateUsed;
			 /*lastStateUsed = foodStateAdded.lastStateUsed || lastStateUsed;
			 if(!foodStateAdded.structAdded){
				 unknownStructList.add(childFoodStruct);
			 }*/
		}
		return lastStateUsed;
	}
	
	/**
	 * List of current FoodState's in this RecipeGraph.
	 * @return
	 */
	public List<FoodState> currentStateList(){
		return this.currentStateList;
	}
	
	/**
	 * create Graph Expr's based on the current list of 
	 * FoodState's. Currently used by Gson in web servlet.
	 * @return
	 */
	public List<String> createGraphExprs(){
		List<String> graphExprList = new ArrayList<String>();
		for(FoodState state : currentStateList){
			graphExprList.add(state.toExpr().toString());
		}
		return graphExprList;
	}
	
	/**
	 * Currently used by Gson in web servlet.
	 * @return List of recipe parsed expressions created used to create 
	 * this RecipeGraph.
	 */
	public List<String> parsedExpressionList(){
		return this.parsedExpressionList;
	}
	
	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder();
		sb.append("currentStateList: \n");
		int counter = 0;
		for(FoodState state : currentStateList){
			sb.append(counter++).append(": ").append(state).append("\n");
			sb.append("EXPR:\n").append(state.toExpr());
		}
		return sb.toString();
	}
}
