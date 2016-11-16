package thmp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

import thmp.WLCommand.ImmutableWLCommand;
import thmp.WLCommand.ImmutableWLCommand.Builder;
import thmp.WLCommand.OptionalPosTerm;
import thmp.WLCommand.PosTerm;
import thmp.WLCommand.PosTerm.PBuilder;
import thmp.WLCommand.WLCommandComponent;

/**
 * list of WLCommand's. Creates WLCommands using data here and stores them in an
 * immutable Multimap, keys being trigger words.
 *
 */
public class WLCommandsList {

	/**
	 * Map, keys are trigger words, values are pointers to WLCommand's
	 */
	private static final ImmutableMultimap<String, ImmutableWLCommand> WLCommandMap; 
	
	/**
	 * ImmutableMultimap of which strings lead/direct to which trigger words. It's a
	 * many-to-many mapping: one string can lead to many, and many can lead to
	 * one, trigger word. Strings that don't have entries should subsequently
	 * look up in WLCommandMap directly.
	 */
	private static final ImmutableMultimap<String, String> triggerWordLookupMap;

	/**
	 * Final constants used to indicate WLCommand/Aux String as posIndex in PosList.
	 * Use the getter below instead of public!
	 */
	public static final int WLCOMMANDINDEX = -1;

	public static final int AUXINDEX = -2;
	private static final int DEFAULT_POSITION_IN_MAP = Integer.MIN_VALUE;
	
	/**
	 * @return the defaultPositionInMap
	 */
	public static int getDefaultPositionInMap() {
		return DEFAULT_POSITION_IN_MAP;
	}

	private static final String TRIGGERMATHOBJSTR = "TriggerMathObj";
	private static final String OPTIONAL_TOKEN_STR = "OPT";
	//should put these in WLCommand
	private static final Pattern OPTIONAL_TOKEN_PATTERN = Pattern.compile("OPT(\\d*)");
	private static final String DEFAULT_AUX_NAME_STR = "AUX";
	private static final String VERB_PARAM = "verb|vbs|be";
	private static final Pattern VERB_PARAM_PATTERN = Pattern.compile(VERB_PARAM);
	
	/**
	 * 
	 */
	static {
		ImmutableListMultimap.Builder<String, String> triggerWordLookupMapBuilder = ImmutableListMultimap.builder();
		//need to put all vbs in here programmatically
		triggerWordLookupMapBuilder.put("be", "is");
		triggerWordLookupMapBuilder.put("are", "is");
		triggerWordLookupMapBuilder.put("has", "have");
		//triggerWordLookupMapBuilder.put("have", "is");
		triggerWordLookupMapBuilder.put("belong", "is");
		triggerWordLookupMapBuilder.put("lie", "is");
		triggerWordLookupMapBuilder.put("let", "if");
		triggerWordLookupMapBuilder.put("for any", "for every");
		triggerWordLookupMapBuilder.put("for all", "for every");
		triggerWordLookupMapBuilder.put("if", "suppose");
		triggerWordLookupMapBuilder.put("if and only if", "suppose");
		triggerWordLookupMapBuilder.put("assume", "if");
		triggerWordLookupMapBuilder.put("is included", "is contained");
		triggerWordLookupMapBuilder.put("are included", "is contained");
		triggerWordLookupMapBuilder.put("are contained", "is contained");
		// triggerWordLookupMapBuilder.put("radius", "is");

		triggerWordLookupMap = triggerWordLookupMapBuilder.build();

		// builder for WLComamndMap,
		ImmutableMultimap.Builder<String, ImmutableWLCommand> WLCommandMapBuilder = ImmutableMultimap.builder();

		// create(Map<WLCommandComponent, Integer> commandsCountMap)
		// The words go in order they are expected to appear in sentence.
		// In int array, 1 means to use in posList in final command, 0 means
		// don't use.
		// eg "of" in "element of" is not used, but should be there to determine
		// if a command is satisfied.
		// all Strings go into commandsMap and PosList, but only those with true
		// goes to final command String built.
		// "symb|ent" "pre, of" "symb|ent"; All regexes to be matched
		// type and name are always specified, if name left empty, will become
		// wildcard.
		// type and name uniquely specify a WLCommand, for the same command, use
		// custom position if it's specified
		// (by an int, 4 comma-separated strings total), use default order
		// otherwise (3 comma-separated strings).
		// name being -1 indicates WL command.
		// -1 indicates WL command.
		// indicate trigger word in sentence, if not WL command. Ensure position
		// of trigger word is correct.
		// Just a String represents an auxilliary String, eg just a bracket.
		// 3rd element could be true/false (includes/not includes in command),
		// or trigger (trigger word, but not
		// included in final command, so indicates false).
		// 5th element used for defining custom order for now, 4th term defines
		// whether to trigger TriggerMathObj term
		// If Commands involving \[Element] deviate from item1 \[Element] item2, 
		// should re-examine ParseTreeTwoVec.java.
		// OPTi, where i is the group number for this optional term, represents an optional 
		// term. An optional group is only included if all optional terms in this group
		// are satisfied.
		//String posStr, String nameStr, boolean includeInBuiltString, boolean isTrigger, boolean isTriggerMathObj, int positionInMap
		//WLCommandMapBuilder.put("element", addCommand(
			//	new String[] { "symb|ent, , true", "\\[Element]", ", element, trigger", "pre, of, false", "symb|ent, , true" }));	
		
		WLCommandMapBuilder.put("element", addCommand(new PBuilder("symb|ent", null, true), new PBuilder("\\[Element]"),
				new PBuilder(null, "element", false, true, false), new PBuilder("pre", "of", false), new PBuilder("symb|ent", null, true) ));		
		
		//e.g. "is given by an element of the ring"
		/*WLCommandMapBuilder.put("element", addCommand(
				new String[] { "parti, , false", ", element, trigger", "x", "\\[Element]", "pre, of, false", "symb|ent, , true" }));*/
		WLCommandMapBuilder.put("element", addCommand(new PBuilder("parti", null, false), new PBuilder(null, "element", false, true, false),
				new PBuilder("\\[Element]"), new PBuilder("pre", "of", false), new PBuilder("symb|ent|noun", null, true) ));		
		
		/*WLCommandMapBuilder.put("given", addCommand(
				new String[] { "parti, given, trigger", "symb|ent, , true" }));*/
		WLCommandMapBuilder.put("given", addCommand(new PBuilder("parti", "given", false, true, false), 
				new PBuilder("symb|ent", null, true) ));	
		
		/*WLCommandMapBuilder.put("exist", addCommand(
				new String[] { ", there, false", "Exists[", ", exists*, trigger",  "ent|symb|phrase|noun, , true", "]"}));*/
		WLCommandMapBuilder.put("exist", addCommand(new PBuilder(null, "there", false), new PBuilder("Exists["),
				new PBuilder(null, "exists*", false, true, false), new PBuilder("ent|symb|phrase|noun", null, true),
				 new PBuilder("]") ));	
		//WLCommandMapBuilder.put("is", addCommand(new String[] { "symb|ent|pro, , true", "verb|vbs|be, is|are|be, trigger",
			//	"\\[Element]", "symb|ent|phrase, , true, TriggerMathObj" }));
		/*WLCommandMapBuilder.put("is", addCommand(new PBuilder("symb|ent|pro", null, true), 
				new PBuilder("verb|vbs|be", "is|are|be", false, true, false), new PBuilder("\\[Element]"),
				new PBuilder("symb|ent|phrase", null, true, false, true) ));*/
		
		//WLCommandMapBuilder.put("derivative",
			//	addCommand(new String[] { ", derivative, trigger", "Derivative[", "pre, of, false", "symb|ent, , true", "]" }));
		WLCommandMapBuilder.put("derivative", addCommand(new PBuilder(null, "derivative", false, true, false), 
				new PBuilder("Derivative["), new PBuilder("pre", "of", false), 
				new PBuilder("symb|ent", null, true), new PBuilder("]") ));
		//WLCommandMapBuilder.put("log",
			//addCommand(new String[] { ", log, trigger", "Log[", "pre, of, false", "symb|ent, , true", "]" }));
		WLCommandMapBuilder.put("log", addCommand(new PBuilder(null, "log", false, true, false), 
				new PBuilder("Log["), new PBuilder("pre", "of", false), 
				new PBuilder("symb|ent", null, true), new PBuilder("]") ));
		//WLCommandMapBuilder.put("union",
			//addCommand(new String[] { ", union, trigger", "Union[", "pre, of, false", "symb|ent, , true", "]" }));
		WLCommandMapBuilder.put("union", addCommand(new PBuilder(null, "union", false, true, false), 
				new PBuilder("Union["), new PBuilder("pre", "of", false), 
				new PBuilder("symb|ent", null, true), new PBuilder("]") ));
		
		// label a term to use to trigger a mathObj, communicate to posList,
		// posList dynamically builds command
		// using TriggerMathObj.
		//WLCommandMapBuilder.put("is", addCommand(new String[] { "symb|ent, , true", "verb|vbs, is|are|be, trigger",
			//	"\\[Element]", "symb|ent, , true" }));
		
		//WLCommandMapBuilder.put("subset",
			//	addCommand(new String[] { ", subset, trigger", "Subset[", "pre, of, false", "symb|ent, , true", "]" }));
		WLCommandMapBuilder.put("subset", addCommand(new PBuilder(null, "subset", false, true, false), 
				new PBuilder("Subset["), new PBuilder("pre", "of", false), 
				new PBuilder("symb|ent", null, true), new PBuilder("]") ));
		
		// trigger TriggerMathObj
		//***action*** commands
		//WLCommandMapBuilder.put("is", addCommand(new String[] { "symb|ent|pro, , true", "verb|vbs|be, is|are|be, trigger",
			//	"\\[Element]", "symb|ent|phrase, , true, TriggerMathObj" }));
		WLCommandMapBuilder.put("is", addCommand(new PBuilder("symb|ent|pro", null, true), 
				new PBuilder("verb|vbs|be", "is|are|be", false, true, false), new PBuilder("\\[Element]"),
				//negative term, to stop command if encountered
				new PBuilder("adj", null, WLCommand.PosTermType.NEGATIVE),				
				new PBuilder("symb|ent|phrase", null, true, false, true) ));
		//e.g. "$X$ is connected"
		//WLCommandMapBuilder.put("is", addCommand(new String[] { "symb|ent|pro, , true", "verb|vbs|be, is|are|be, trigger",
			//"\\[HasProperty]", "adj, , true, TriggerMathObj" }));
		WLCommandMapBuilder.put("is", addCommand(new PBuilder("symb|ent|pro", null, true), 
				new PBuilder("verb|vbs|be", "is|are|be", false, true, false), 
				new PBuilder("\\[HasProperty]"), new PBuilder("adj", null, true, false, true) ));
		//e.g. "R is of finite type"
		WLCommandMapBuilder.put("is", addCommand(new PBuilder("symb|ent|pro", null, true), 
				new PBuilder("verb|vbs|be", "is|are|be", false, true, false), 
				new PBuilder("\\[HasProperty]"), new PBuilder("pre", "of", false),
				new PBuilder("noun|ent", null, true, false, true) ));
		
		//negative of above
		//WLCommandMapBuilder.put("is not", addCommand(new String[] { "symb|ent|pro, , true", "verb|vbs|be, is not|are not|be not, trigger",
				//"Not[\\[Element]]", "symb|ent|adj|phrase, , true, TriggerMathObj" }));
		WLCommandMapBuilder.put("is not", addCommand(new PBuilder("symb|ent|pro", null, true), 
				new PBuilder("verb|vbs|be", "is not|are not|be not", false, true, false), 
				new PBuilder("Not[\\[Element]]"), new PBuilder("symb|ent|adj|phrase", null, true, false, true) ));
		
		//WLCommandMapBuilder.put("act", addCommand(new String[] { "Action[", "symb|ent|pro, , true", ", act|acts, trigger",
				//";", "symb|ent|pro, , true, TriggerMathObj", ";", ", by, false, OPT", "ent, , true, OPT", "]" }));		
		WLCommandMapBuilder.put("act", addCommand(new PBuilder("Action["), new PBuilder("symb|ent|pro", null, true), 
				new PBuilder(null, "act|acts", false, true, false), new PBuilder(","),
				new PBuilder("symb|ent|pro", null, true, false, true), new PBuilder(",", "OPT"), 
				new PBuilder(null, "by", false, false, "OPT"), new PBuilder(null, null, true, false, "OPT"), new PBuilder("]") ));	
		//$f$ maps $x$ to $y$
		WLCommandMapBuilder.put("map", addCommand(new PBuilder("Map["), new PBuilder("symb|ent|pro", null, true), 
				new PBuilder("verb|vbs", "map|maps", false, true, false), new PBuilder(","),
				new PBuilder("symb|ent|pro", null, true, false, true), new PBuilder(","), new PBuilder(null, "to", false), 
				new PBuilder("symb|ent|pro", null, true, false, true), new PBuilder("]") ));
		
		//if_assert. As well as "let", etc
		//WLCommandMapBuilder.put("if", addCommand(new String[] { "if|If|let, , trigger", "assert, , true" }));
		WLCommandMapBuilder.put("if", addCommand(new PBuilder("if|If|let", null, false, true, false), 
				new PBuilder("assert", null, true) ));
		
		//WLCommandMapBuilder.put("equal to", addCommand(new String[] { "symb|ent, , true",
		//"==", "equal to, , trigger", "symb|ent|phrase, , true, TriggerMathObj" }));
		WLCommandMapBuilder.put("equal to", addCommand(new PBuilder("symb|ent", null, true), 
				new PBuilder("=="), new PBuilder("equal to", null, false, true, false), 
				new PBuilder("symb|ent|phrase", null, true, false, true) ));
		
		//assert_hypo: 
				//WLCommandMapBuilder.put("hyp", addCommand(new String[] { "assert, , true", "hyp, , trigger", "ent|symb, , true"
				  //}));
		WLCommandMapBuilder.put("hyp", addCommand(new PBuilder("assert", null, true), new PBuilder("hyp", null, false, true, false), 
				new PBuilder("ent|symb", null, true) ));
		
		//WLCommandMapBuilder.put("auxpass", addCommand(new String[] { "ent, , true",
			//	"auxpass, , trigger_true", "ent|csubj, , true" }));
		WLCommandMapBuilder.put("auxpass", addCommand(new PBuilder("ent", null, true), new PBuilder("auxpass", null, true, true, false), 
				new PBuilder("ent|csubj", null, true) ));
		
		//auxpass, eg "is called"
		//WLCommandMapBuilder.put("is called", addCommand(new String[] { "symb|ent|pro, , true", "auxpass, is called, trigger",
				//"\\[Element]", "symb|ent|adj|phrase, , true, TriggerMathObj" }));
		WLCommandMapBuilder.put("is called", addCommand(new PBuilder("symb|ent|pro", null, true), 
				new PBuilder("auxpass", "is called", false, true, false), new PBuilder("\\[Element]"),
				new PBuilder("symb|ent|adj|phrase", null, true, false, true) ));
		
		// ***Hypothesis commands***
		//for every $x$
		//WLCommandMapBuilder.put("for every", addCommand(new String[] { ", for every|for any, trigger", "\\[ForAll][",
		//	 "ent|symb, , true", "]" }));
		WLCommandMapBuilder.put("for every", addCommand(new PBuilder(null, "for every|for any", false, true, false), 
				new PBuilder("\\[ForAll]["), new PBuilder("ent|symb", null, true), new PBuilder("]") ));
		
		//WLCommandMapBuilder.put("suppose", addCommand(new String[] { "hyp, , trigger",
			//"assert, , true" }));
		WLCommandMapBuilder.put("suppose", addCommand(new PBuilder("hyp", null, false, true, false), 
				new PBuilder("assert", null, true) ));
		
		//WLCommandMapBuilder.put("consider", addCommand(new String[] { "verb, , trigger", "ent|phrase, , true" }));
		WLCommandMapBuilder.put("consider", addCommand(new PBuilder("verb", null, false, true, false), 
				new PBuilder("ent|phrase", null, true) ));
		
		//we have ...
		//WLCommandMapBuilder.put("have", addCommand(new String[] { "pro, we, false", "verb, have, trigger", ", , true"}));
		WLCommandMapBuilder.put("have", addCommand(new PBuilder("pro", "we", false), new PBuilder("verb", "have", false, true, false), 
				new PBuilder(null, null, true) ));
		// "A has property B", eg "chains of ideals have same length"
		//WLCommandMapBuilder.put("have", addCommand(new String[] { "ent|symb|pro, , true", "verb, have|has, trigger", "\\HasProperty[", ", , true", "]"}));
		WLCommandMapBuilder.put("have", addCommand(new PBuilder("ent|symb|pro", null, true), new PBuilder("verb", "have|has", false, true, false), 
				new PBuilder("\\HasProperty["), new PBuilder(null, null, true), new PBuilder("]") ));
		
		/*
		WLCommandMapBuilder.put("is contained", addCommand(new String[] { "symb|ent|pro, , true", "auxpass, , trigger",
				"\\[Element]", "symb|ent|adj|phrase, , true, TriggerMathObj" }));		
		
		WLCommandMapBuilder.put("at most", addCommand(new String[] { "symb|ent|pro, , true", "verb|vbs|be, is|are|be, false",
				"<=", "pre, at most, trigger", "symb|ent, , true, TriggerMathObj" }));
				
		*/
		
		// label string if to be used as trigger ent/symb, then use these words
		// as trigger system
		// function with radius of convergence
		
		// logical operators
		
		WLCommandMap = WLCommandMapBuilder.build();

	}

	/**
	 * @return the auxindex
	 */
	public static int getAUXINDEX() {
		return AUXINDEX;
	}
	
	/**
	 * @return the wlcommandindex
	 */
	public static int getWLCOMMANDINDEX() {
		return WLCOMMANDINDEX;
	}	
	
	private static ImmutableWLCommand addCommand(PBuilder ... pBuilderAr) {
		
		final ImmutableMap<WLCommandComponent, Integer> commandsCountMap;
		ImmutableMap<Integer, Integer> optionalTermsGroupCountMap = null;
		final ImmutableList<PosTerm> posTermList;
		// used to build commandsCountMap
		Map<WLCommandComponent, Integer> commandsCountPreMap = new HashMap<WLCommandComponent, Integer>();

		Map<Integer, Integer> optionalTermsGroupCountPreMap = null;
		
		final List<PosTerm> posList = new ArrayList<PosTerm>();
		// total number of components, it's sum of all entries in
		// ComponentCountMap,
		// minus the number of WL commands.
		int componentCount = 0;
		// assert(commandStringAr.length == useAr.length);
		int triggerWordIndex = -1;
		int optionalTermsCount = 0;
		
		for (int i = 0; i < pBuilderAr.length; i++) {

			PBuilder curBuilder = pBuilderAr[i];			
			WLCommandComponent curCommandComponent = curBuilder.getCommandComponent();

			if(curCommandComponent.nameStr().equals(DEFAULT_AUX_NAME_STR)){
				//if(!curBuilder.isOptionalTerm()){
				PosTerm curPosTerm = curBuilder.build();
				posList.add(curPosTerm);
				continue;
				//}
			}
			
			Integer temp;
			int curComponentCount = (temp = commandsCountPreMap.get(curCommandComponent)) == null ? 0 : temp;
			
			if(curBuilder.getPositionInMap() == DEFAULT_POSITION_IN_MAP){
				int positionInMap = curComponentCount;
				curBuilder.setPositionInMap(positionInMap);
			}
			
			PosTerm curPosTerm = curBuilder.build();
			posList.add(curPosTerm);
			
			//term used to eliminate commands.
			if(curPosTerm.isNegativeTerm()){				
				continue;
			}
			
			if(curPosTerm.isOptionalTerm()){
				
				optionalTermsCount++;
				int optionalGroupNum = curPosTerm.optionalGroupNum();
				
				if(null == optionalTermsGroupCountPreMap){
					optionalTermsGroupCountPreMap = new HashMap<Integer, Integer>();
				}
				
				Integer num = optionalTermsGroupCountPreMap.get(optionalGroupNum);
				if(null == num){
					optionalTermsGroupCountPreMap.put(optionalGroupNum, 1);
				}else{
					optionalTermsGroupCountPreMap.put(optionalGroupNum, num + 1);
				}
				
			}else{
				componentCount++;
				//optional terms can't be trigger terms
				if(curPosTerm.isTrigger()){
					triggerWordIndex = i;
				}
			}
			commandsCountPreMap.put(curCommandComponent, curComponentCount + 1);

		}
		
		if(triggerWordIndex < 0){
			throw new IllegalArgumentException("Trigger word index in a command cannot be negative!");
		}
		
		commandsCountMap = ImmutableMap.copyOf(commandsCountPreMap);
		posTermList = ImmutableList.copyOf(posList);

		if(null != optionalTermsGroupCountPreMap){
			optionalTermsGroupCountMap = ImmutableMap.copyOf(optionalTermsGroupCountPreMap);
		}
		/*(Map<WLCommandComponent, Integer> commandsCountMap, 
		ImmutableList<PosTerm> posList, int componentCount, int triggerWordIndex,
		int optionalTermsCount, ImmutableMap<Integer, Integer> optionalTermsMap){*/
		
		ImmutableWLCommand.Builder immutableWLCommandBuilder = new ImmutableWLCommand.Builder(commandsCountMap, posTermList, 
				componentCount, triggerWordIndex, optionalTermsCount, optionalTermsGroupCountMap);

		return immutableWLCommandBuilder.build();
	}
	
	/**
	 * Create WLCommands using input data and add them to an immutable list.
	 * Specifically, create commandsCountMap with WLCommandComponent and
	 * supplied integer.
	 * 
	 * @param commandStringAr
	 *            Array of command Strings
	 */
	///// name should be there already!
	/*public static WLCommand addCommand(String[] commandStringAr) {
		
		ImmutableMap<WLCommandComponent, Integer> commandsCountMap;
		ImmutableMap<Integer, Integer> optionalTermsGroupCountMap = null;
		// used to build commandsCountMap
		Map<WLCommandComponent, Integer> commandsCountPreMap = new HashMap<WLCommandComponent, Integer>();

		Map<Integer, Integer> optionalTermsGroupCountPreMap = null;
		
		List<PosTerm> posList = new ArrayList<PosTerm>();
		// total number of components, it's sum of all entries in
		// ComponentCountMap,
		// minus the number of WL commands.
		int componentCounter = 0;
		// assert(commandStringAr.length == useAr.length);
		int triggerWordIndex = -1;
		int optionalTermsCount = 0;
		
		for (int i = 0; i < commandStringAr.length; i++) {
			
			int optionalGroupNum = 0;
			// those are regexes to be matched
			String commandStr = commandStringAr[i];

			String[] commandStrParts = commandStr.split(",");
			int commandStrPartsLen = commandStrParts.length;
			// auxilliary String like brackets. Just put in posTermList, don't
			// put in commandsMap
			if (commandStrPartsLen == 1) {
				String posStr = commandStrParts[0];
				String nameStr = "AUX"; // auxilliary string
				WLCommandComponent commandComponent = new WLCommandComponent(posStr, nameStr);
				//
				PosTerm curTerm = new PosTerm(commandComponent, AUXINDEX, true, false);
				posList.add(curTerm);
			} else {

				String posStr = commandStrParts[0].matches("\\s*") ? ".*" : commandStrParts[0].trim();
				// String nameStr = commandStrParts.length > 2 ?
				// commandStrParts[1] : "*";
				String nameStr = commandStrParts[1].matches("\\s*") ? ".*" : commandStrParts[1].trim();
				
				String[] cmdPart2Ar = commandStrParts[2].trim().split("_");
				//could be trigger_true, to indicate inclusion in posString
				boolean useInPosList = cmdPart2Ar.length > 1 ? Boolean.valueOf(cmdPart2Ar[1])
						: Boolean.valueOf(cmdPart2Ar[0]);	
				
				boolean isTriggerMathObj = false;
				boolean isOptionalTerm = false;
				// process command and create WLCommandComponent and PosList
				WLCommandComponent commandComponent = new WLCommandComponent(posStr, nameStr);
				
				// how many of this component have we added so far
				Integer temp;
				int curComponentCount = (temp = commandsCountPreMap.get(commandComponent)) == null ? 0 : temp;
				
				int positionInMap = curComponentCount;
				// check length of commandStrParts to see if custom order is
				// required
				if (commandStrPartsLen > 3) {
					String commandStrParts3 = commandStrParts[3].trim();
					//if trigger triggerMathObj
					isTriggerMathObj = commandStrParts3.equals(TRIGGERMATHOBJSTR);
					if(!isTriggerMathObj){
						Matcher optionalTermMatcher = OPTIONAL_TOKEN_PATTERN.matcher(commandStrParts3);
						
						if(optionalTermMatcher.find()){
							String groupNum = optionalTermMatcher.group(1);
							//should only need to check if null, so why "" is no match?
							if(groupNum != null && !groupNum.equals("")){
								//System.out.println(groupNum + " groupNum");
								optionalGroupNum = Integer.valueOf(groupNum);
							}
							
							if(null == optionalTermsGroupCountPreMap){
								optionalTermsGroupCountPreMap = new HashMap<Integer, Integer>();
							}
							
							Integer num = optionalTermsGroupCountPreMap.get(optionalGroupNum);
							if(null == num){
								optionalTermsGroupCountPreMap.put(optionalGroupNum, 1);
							}else{
								optionalTermsGroupCountPreMap.put(optionalGroupNum, num + 1);
							}
							
							optionalTermsCount++;
							isOptionalTerm = true;
						}
						
					}
					if (commandStrParts.length > 4) {
						positionInMap = Integer.valueOf(commandStrParts[4]);
					}
				}
			

				if(commandStrPartsLen > 2){
					if (commandStrParts[2].trim().matches("trigger.*")) {
						//System.out.println("*^^^^^^^^^^%% trigger index " + i);
						triggerWordIndex = i;
						//componentCounter--;
					}
				}
				
				// curOcc is the position inside the list in commandsMap.
				// but sometimes want to switch order of occurence in final
				// command and order in original sentence
				PosTerm curTerm;
				if(!isOptionalTerm){
					curTerm = new PosTerm(commandComponent, positionInMap, useInPosList, isTriggerMathObj);
				}else{
					curTerm = new OptionalPosTerm(commandComponent, positionInMap, useInPosList, isTriggerMathObj, optionalGroupNum);
				}
				posList.add(curTerm);
				// }
				// PosTerm(WLCommandComponent commandComponent, int position,
				// boolean includeInBuiltString){

				//optional CommandComponents are stored in commandsCountMap, but don't count
				//towards componentCounter, which determines satisfiability.
				commandsCountPreMap.put(commandComponent, curComponentCount + 1);
				
				if(!isOptionalTerm){					
					componentCounter++;
				}
			}
		}

		//shouldn't be invoked, as every command should have a trigger word
		if(triggerWordIndex == -1){
			triggerWordIndex = 0;
		}
		
		commandsCountMap = ImmutableMap.copyOf(commandsCountPreMap);
		
		if(null != optionalTermsGroupCountPreMap){
			optionalTermsGroupCountMap = ImmutableMap.copyOf(optionalTermsGroupCountPreMap);
		}
		
		return WLCommand.create(commandsCountMap, posList, componentCounter, triggerWordIndex, 
				optionalTermsCount, optionalTermsGroupCountMap);
		
	}*/

	/**
	 * Returns probable command 
	 * 
	 */
	public enum f {
		
	}
	
	/**
	 * 
	 * @return ImmutableMap WLCommandMap
	 */
	public static Multimap<String, ImmutableWLCommand> WLCommandMap() {
		return WLCommandMap;
	}

	/**
	 * @return ImmutableMap triggerWordLookupMap
	 */
	public static Multimap<String, String> triggerWordLookupMap() {
		return triggerWordLookupMap;
	}

}
