package thmp.parse;

import java.util.ArrayList;
import java.util.EnumMap;
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

import thmp.exceptions.IllegalWLCommandException;
import thmp.parse.RelationVec.RelationType;
import thmp.parse.WLCommand.ImmutableWLCommand;
import thmp.parse.WLCommand.OptionalPosTerm;
import thmp.parse.WLCommand.PosTerm;
import thmp.parse.WLCommand.WLCommandComponent;
import thmp.parse.WLCommand.ImmutableWLCommand.Builder;
import thmp.parse.WLCommand.PosTerm.PBuilder;
import thmp.parse.WLCommand.PosTerm.PosTermConnotation;
import thmp.parse.WLCommand.PosTerm.PositionInStructTree;
import thmp.utils.FileUtils;

/**
 * list of WLCommand's. Creates WLCommands using data here and stores them in an
 * immutable Multimap, keys being trigger words.
 * Commands/grammar rules should be added *judiciously*, for best parse efficiency. 
 * When possible, bundle newly desired patterns into existing rules. Math theorems 
 * in general follow a limited set of sentence structures, and hence should not require many
 * rules.
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
	public static final int WL_DIRECTIVE_INDEX = -1;
	/* e.g. "[", "~" */
	public static final int AUXINDEX = -2;
	private static final int DEFAULT_POSITION_IN_MAP = Integer.MIN_VALUE;
	private static final boolean FOOD_PARSE = FileUtils.isFoodParse();
	
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
		//need to put all vbs in here programmatically.
		//keys can be types.
		triggerWordLookupMapBuilder.put("be", "is");
		triggerWordLookupMapBuilder.put("are", "is");
		triggerWordLookupMapBuilder.put("is not", "is");
		triggerWordLookupMapBuilder.put("are not", "is");
		//triggerWordLookupMapBuilder.put("has", "have");
		triggerWordLookupMapBuilder.put("has", "is");
		triggerWordLookupMapBuilder.put("has no", "is");
		
		triggerWordLookupMapBuilder.put("exists", "exist");
		//triggerWordLookupMapBuilder.put("have", "is");
		
		//for "hyp ent" sentences
		triggerWordLookupMapBuilder.put("take", "for");
		triggerWordLookupMapBuilder.put("fix", "for");
		triggerWordLookupMapBuilder.put("fix", "let");		
		//keep this map entry, relevant for picking up definitions
		//and detecting hypothesis
		triggerWordLookupMapBuilder.put("take", "let");
		//for patterns such as "let $x > 1$"
		triggerWordLookupMapBuilder.put("let", "if");
		triggerWordLookupMapBuilder.put("letbe", "let"); 
		//triggerWordLookupMapBuilder.put("let", "if"); June 28
		//triggerWordLookupMapBuilder.put("suppose", "if"); June 28
		//triggerWordLookupMapBuilder.put("if", "let");//don't need this, there's dedicated command
		triggerWordLookupMapBuilder.put("suppose", "let");
		triggerWordLookupMapBuilder.put("suppose that", "if");
		triggerWordLookupMapBuilder.put("suppose that", "let");
		triggerWordLookupMapBuilder.put("where", "if");
		triggerWordLookupMapBuilder.put("for any", "for every");
		triggerWordLookupMapBuilder.put("for all", "for every");
		//triggerWordLookupMapBuilder.put("if and only if", "let");
		triggerWordLookupMapBuilder.put("assume", "if");
		triggerWordLookupMapBuilder.put("assume", "let");
		triggerWordLookupMapBuilder.put("is included", "is contained");
		triggerWordLookupMapBuilder.put("are included", "is contained");
		triggerWordLookupMapBuilder.put("are contained", "is contained");
		triggerWordLookupMapBuilder.put("is said to be", "is called");
		
		
		triggerWordLookupMap = triggerWordLookupMapBuilder.build();

		// builder for WLComamndMap,
		ImmutableMultimap.Builder<String, ImmutableWLCommand> wLCommandMapBuilder = ImmutableMultimap.builder();
		
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
		
		/**
		 * Optional terms before the trigger word should be accompanied by Negative terms, to avoid inadvertently 
		 * adding structs before the trigger to to PosTerms after the trigger term. <--need to make this more robust! Jan 2017.
		 */
		/*****General-scope commands******/
		//triggered by types. Add such judiciously.
		//e.g. $f$ maps $X$ to $Y$.
		/**Working command: putToWLCommandMapBuilder(wLCommandMapBuilder, "verb", new PBuilder("det|symb|ent|pro|noun", null, true), 
				//new PBuilder("pro", "we", WLCommand.PosTermType.NEGATIVE),
				new PBuilder("verb|vbs", null, WLCommand.PosTermType.NEGATIVE), 
				new PBuilder(" ~"), new PBuilder("Connective").makeExprHead(),
				new PBuilder("["), new PBuilder("verb", null, true, true, false).makeExprHeadArg(), new PBuilder("]~ "),
				new PBuilder("verb|vbs", null, WLCommand.PosTermType.NEGATIVE), new PBuilder(" {", "OPT"), 
				new PBuilder("symb|ent|noun|adj|prep|phrase|qualifier", null, true, false, false).addRelationType(RelationType._IS),
				new PBuilder(", {\"Qualifiers\"->", "OPT"), 
				//the relation should incorporate several types. 
				new PBuilder("prep|qualifier", null, true, false, "OPT").addRelationType(RelationType.IS_), new PBuilder("}}", "OPT")
				);*/
		//for verbphrases whose verbs don't fall under "is", "has", etc
		putToWLCommandMapBuilder(wLCommandMapBuilder, "verbphrase", new PBuilder("det|symb|ent|pro|noun", null, true)
				.addRelationType(RelationType._IS), 
				//new PBuilder("pro", "we", WLCommand.PosTermType.NEGATIVE),
				new PBuilder(" ~"), new PBuilder("HasProperty").makeExprHead(), new PBuilder("~ "),
				new PBuilder("is|are", null, WLCommand.PosTermType.NEGATIVE),
				new PBuilder("verbphrase", null, true, true, false).addRelationType(RelationType.IS_), 
				new PBuilder(" {", "OPT"),
				//new PBuilder("symb|ent|noun|adj|prep|phrase|qualifier", null, true, false, false).addRelationType(RelationType._IS),
				new PBuilder(", {", "OPT"), new PBuilder("Qualifiers", "OPT").makeOptionalTermHead(), new PBuilder("->", "OPT"),
				//the relation should incorporate several types. 
				new PBuilder("prep|qualifier", null, true, false, "OPT").addRelationType(RelationType.IS_), 
				new PBuilder("}}", "OPT")
				);
		/* e.g. "Fix a prime $p$"*/
		putToWLCommandMapBuilder(wLCommandMapBuilder, "verb", new PBuilder("{"),
				//new PBuilder(null, "then", false, false, "OPT"),
				new PBuilder("^(?!hyp)verb$", null, true, true, false).setPositionInStructTree(PositionInStructTree.FIRST),
				new PBuilder(" ~"), new PBuilder("Action").makeExprHead(), new PBuilder("~ "), //new PBuilder(", "), 
				new PBuilder(" {", "OPT"), 
				new PBuilder("symb|ent|noun", null, true, false, false).addRelationType(RelationType._IS),
				//new PBuilder(", {\"Qualifiers\"->", "OPT"), //new PBuilder("Qualifiers", "OPT").makeOptionalTermHead(),
				new PBuilder(", {", "OPT"),
				new PBuilder("Qualifiers", "OPT").makeOptionalTermHead(), new PBuilder("->", "OPT"),
				//the relation should incorporate several types. 
				new PBuilder("prep|qualifier", null, true, false, "OPT").addRelationType(RelationType.IS_), new PBuilder("}}", "OPT"),
				new PBuilder("}")
				);
		
		if(FOOD_PARSE){
			//e.g. "cook until fragrant", or  "cook over fire"
			putToWLCommandMapBuilder(wLCommandMapBuilder, "verb", new PBuilder("{"),
					new PBuilder("^(?!hyp)verb$", null, true, true, false).setPositionInStructTree(PositionInStructTree.FIRST),
					new PBuilder(" ~"), new PBuilder("Action").makeExprHead(), new PBuilder("~ "), //new PBuilder(", "), 
					new PBuilder("adj|prep", null, true, false, false).addRelationType(RelationType._IS).setPositionInStructTree(PositionInStructTree.LAST),
					new PBuilder("}")
					);
			//e.g. "drain and set aside"
			putToWLCommandMapBuilder(wLCommandMapBuilder, "verb", 
					//new PBuilder(null, "then", false, false, "OPT"),
					new PBuilder("^(?!hyp)verb$", null, true, true, false).setPositionInStructTree(PositionInStructTree.FIRST)
					.setPositionInStructTree(PositionInStructTree.LAST)
					//new PBuilder(" ~"), new PBuilder("Action").makeExprHead(), new PBuilder("~ "), //new PBuilder(", "), 
					//new PBuilder("adj", null, true, false, false).addRelationType(RelationType._IS)					
					);
			//e.g. "lightly dust with flour"
			putToWLCommandMapBuilder(wLCommandMapBuilder, "verb", new PBuilder("{"),
					new PBuilder("adj|adverb", null, true, true, false).setPositionInStructTree(PositionInStructTree.FIRST),
					new PBuilder("^(?!hyp)verb$", null, true, true, false),
					new PBuilder(" ~"), new PBuilder("Action").makeExprHead(), new PBuilder("~ "),
					new PBuilder(" {", "OPT"), 
					new PBuilder("symb|ent|noun", null, true, false, false).addRelationType(RelationType._IS),
					new PBuilder(", {", "OPT"),
					new PBuilder("Qualifiers", "OPT").makeOptionalTermHead(), new PBuilder("->", "OPT"),
					//the relation should incorporate several types. 
					new PBuilder("prep|qualifier", null, true, false, "OPT").addRelationType(RelationType.IS_), new PBuilder("}}", "OPT"),
					new PBuilder("}")
					);
		
		}
		/*putToWLCommandMapBuilder(wLCommandMapBuilder, "fix", new PBuilder("{"), //test rule!!
				new PBuilder("verb", null, true, true, false).setPositionInStructTree(PositionInStructTree.FIRST),
				new PBuilder(", "), new PBuilder(" {", "OPT"), 
				new PBuilder("ent", null, true, false, false).addRelationType(RelationType._IS),
				new PBuilder(", {", "OPT"), new PBuilder("Qualifiers", "OPT").makeOptionalTermHead(),
				//the relation should incorporate several types. 
				new PBuilder("prep|qualifier", null, true, false, "OPT").addRelationType(RelationType.IS_), new PBuilder("}}", "OPT"),
				new PBuilder("}")
				);*/
		//e.g. "The field extension $F/Q$ splits."
		putToWLCommandMapBuilder(wLCommandMapBuilder, "verbAlone", new PBuilder("symb|ent|pro|noun", null, true).addRelationType(RelationType._IS), 
				new PBuilder(null, "we", WLCommand.PosTermType.NEGATIVE), new PBuilder(" ~"), new PBuilder("HasProperty").makeExprHead(),
				new PBuilder("~ {"), new PBuilder("verbAlone", "^(?:(?!exist)).+", true, true, false).addRelationType(RelationType.IS_), 
				new PBuilder(" ,", "OPT"), new PBuilder("prep|qualifier", null, true, false, "OPT").addRelationType(RelationType.IS_), new PBuilder("}"));	
		
		/*e.g. If $x > y$.*/
		putToWLCommandMapBuilder(wLCommandMapBuilder, "texAssert", new PBuilder("hyp|if|then|let", null,//"^(?!for every|for all|for any).*$", 
				/*this regex is not perfect, ie "for allj" returns false rather than true*/ false, false, "OPT"), 
				new PBuilder("texAssert", null, true, true, false));
		
		//$A$ is $B$ is $C$. This case is covered in the code by converting ent to texAssert
		/*putToWLCommandMapBuilder(wLCommandMapBuilder, "if", addCommand(new PBuilder("assert", null, true), new PBuilder("hyp|if", null, false, true, false), 
				new PBuilder("ent|symb", null, true, RelationType.IF), new PBuilder("verb|vbs", null, WLCommand.PosTermType.NEGATIVE)));*/

		/*****More specific commands******/
		//putToWLCommandMapBuilder(wLCommandMapBuilder, "element", addCommand(
			//	new String[] { "symb|ent, , true", "\\[Element]", ", element, trigger", "pre, of, false", "symb|ent, , true" }));			
		putToWLCommandMapBuilder(wLCommandMapBuilder, "element", new PBuilder("symb|ent", null, true).addRelationType(RelationType._IS), 
				new PBuilder("\\["), new PBuilder("Element").makeExprHead(), new PBuilder("]"),
				new PBuilder(null, "element", false, true, false), new PBuilder("pre", "of", false), 
				new PBuilder("symb|ent", null, true).addRelationType(RelationType.IS_));		
		
		//e.g. "is given by an element of the ring"
		/*putToWLCommandMapBuilder(wLCommandMapBuilder, "element", addCommand(
				new String[] { "parti, , false", ", element, trigger", "x", "\\[Element]", "pre, of, false", "symb|ent, , true" }));*/
		putToWLCommandMapBuilder(wLCommandMapBuilder, "element", new PBuilder("parti", null, false), new PBuilder(null, "element", false, true, false),
				new PBuilder("\\["), new PBuilder("Element").makeExprHead(), new PBuilder("]"),
				new PBuilder("pre", "of", false), new PBuilder("symb|ent|noun", null, true, RelationType._IS_));		
		
		/*putToWLCommandMapBuilder(wLCommandMapBuilder, "given", addCommand(
				new String[] { "parti, given, trigger", "symb|ent, , true" }));*/
		putToWLCommandMapBuilder(wLCommandMapBuilder, "given", new PBuilder("parti", "given", false, true, false), 
				new PBuilder("symb|ent", null, true).addRelationType(RelationType.IF).addRelationType(RelationType._IS_));
		
		/*putToWLCommandMapBuilder(wLCommandMapBuilder, "exist", addCommand(
				new String[] { ", there, false", "Exists[", ", exists*, trigger",  "ent|symb|phrase|noun, , true", "]"}));*/
		putToWLCommandMapBuilder(wLCommandMapBuilder, "there", new PBuilder(null, "there", false, true, false), 
				new PBuilder("Exist").makeExprHead(), new PBuilder("["),
				new PBuilder(null, "exists*|is|are", false), new PBuilder("ent|symb|phrase|noun", null, true, RelationType.EXIST),
				 new PBuilder("]" ));
		putToWLCommandMapBuilder(wLCommandMapBuilder, "exist", new PBuilder("Exist").makeExprHead(), new PBuilder("["),
				new PBuilder("ent|symb|phrase|noun", null, true, RelationType.EXIST),
				new PBuilder(null, "exists*", false, true, false).setPositionInStructTree(PositionInStructTree.LAST), 
				new PBuilder("]" ));
		
		//putToWLCommandMapBuilder(wLCommandMapBuilder, "is", addCommand(new String[] { "symb|ent|pro, , true", "verb|vbs|be, is|are|be, trigger",
			//	"\\[Element]", "symb|ent|phrase, , true, TriggerMathObj" }));
		/*putToWLCommandMapBuilder(wLCommandMapBuilder, "is", addCommand(new PBuilder("symb|ent|pro", null, true), 
				new PBuilder("verb|vbs|be", "is|are|be", false, true, false), new PBuilder("\\[Element]"),
				new PBuilder("symb|ent|phrase", null, true, false, true) ));*/
		
		//putToWLCommandMapBuilder(wLCommandMapBuilder, "derivative",
			//	addCommand(new String[] { ", derivative, trigger", "Derivative[", "pre, of, false", "symb|ent, , true", "]" }));
		putToWLCommandMapBuilder(wLCommandMapBuilder, "derivative", new PBuilder(null, "derivative", false, true, false), 
				new PBuilder("Derivative["), new PBuilder("pre", "of", false), new PBuilder("symb|ent", null, true), 
				new PBuilder(", ", "OPT"), new PBuilder("prep|qualifier", null, true, false, "OPT"), new PBuilder("]") );
		//putToWLCommandMapBuilder(wLCommandMapBuilder, "log",
			//addCommand(new String[] { ", log, trigger", "Log[", "pre, of, false", "symb|ent, , true", "]" }));
		putToWLCommandMapBuilder(wLCommandMapBuilder, "log", new PBuilder(null, "log", false, true, false), 
				new PBuilder("Log["), new PBuilder("pre", "of", false), 
				new PBuilder("symb|ent", null, true), new PBuilder(", ", "OPT"),
				new PBuilder("prep|qualifier", null, true, false, "OPT"), new PBuilder("]") );
		//putToWLCommandMapBuilder(wLCommandMapBuilder, "union",
			//addCommand(new String[] { ", union, trigger", "Union[", "pre, of, false", "symb|ent, , true", "]" }));
		/*putToWLCommandMapBuilder(wLCommandMapBuilder, "union", addCommand(new PBuilder(null, "union", false, true, false), 
				new PBuilder("Union["), new PBuilder("pre", "of", false), 
				new PBuilder("symb|ent", null, true), new PBuilder("]") ));*/
		
		// label a term to use to trigger a mathObj, communicate to posList,
		// posList dynamically builds command
		// using TriggerMathObj.
		//putToWLCommandMapBuilder(wLCommandMapBuilder, "is", addCommand(new String[] { "symb|ent, , true", "verb|vbs, is|are|be, trigger",
			//	"\\[Element]", "symb|ent, , true" }));
		
		//putToWLCommandMapBuilder(wLCommandMapBuilder, "subset",
			//	addCommand(new String[] { ", subset, trigger", "Subset[", "pre, of, false", "symb|ent, , true", "]" }));
		// e.g. "$Z$ is a closed subset of $X$."
		/*putToWLCommandMapBuilder(wLCommandMapBuilder, "subset", addCommand(
				new PBuilder(null, "subset", false, true, false), 
				new PBuilder("Subset["), new PBuilder("pre", "of", false), 
				new PBuilder("symb|ent", null, true).addRelationType(RelationType._IS_), new PBuilder("]") ));*/
		
		// trigger TriggerMathObj
		//***action*** commands
		putToWLCommandMapBuilder(wLCommandMapBuilder, "is", new PBuilder("symb|ent|pro|noun|det", null, true, false, false, //PosTermConnotation.DEFINED,
				RelationType._IS), 
				new PBuilder("verb|vbs|be", "is|are|be|has no|has", false, true, false), new PBuilder(" ~"), new PBuilder("HasProperty").makeExprHead(), //new PBuilder(" \\[Element] "),
				new PBuilder("~ "), //negative term, to stop command if encountered
				new PBuilder("adj", null, WLCommand.PosTermType.NEGATIVE), //new PBuilder("{", "OPT1"), 
				//new PBuilder("pre", null, true, false, "OPT1"),
				new PBuilder("symb|ent|phrase", null, true, false, false, RelationType.IS_).makePropertyTerm()//, new PBuilder("}", "OPT1")
				); // PosTermConnotation.DEFINING,
		//merge these two?!
		//e.g. "$X$ is connected", "$F$ is isomorphic to ..."
		putToWLCommandMapBuilder(wLCommandMapBuilder, "is", new PBuilder("symb|ent|pro|noun|det", null, true, RelationType._IS), 
				new PBuilder("verb|vbs|be|has no|has", "is|are|be", false, true, false), new PBuilder(" ~"),
				new PBuilder("HasProperty").makeExprHead(), new PBuilder("~ "), new PBuilder("{", "OPT"), 
				new PBuilder("adj|ent|phrase|noun|prep|qualifier", null, true, false, false, RelationType.IS_).makePropertyTerm(),
				new PBuilder(", {\"Qualifiers\"->", "OPT"), 
				new PBuilder("prep", null, true, false, "OPT").addRelationType(RelationType.IS_), new PBuilder("}}", "OPT")
				);
		
		/*putToWLCommandMapBuilder(wLCommandMapBuilder, "is", addCommand(new PBuilder("symb|ent|pro|noun", null, true, RelationType._IS), 
				new PBuilder("verb|vbs|be", "is|are|be", false, true, false), 
				new PBuilder("~HasProperty~"), new PBuilder("adj|phrase|noun|prep", null, true, false, true, RelationType.IS_),
				new PBuilder(", {Qualifier->"), 
				new PBuilder("prep", null, true).addRelationType(RelationType.IS_), new PBuilder("}")
				));*/
		//e.g. "$X$ is in $Y$"
		/*putToWLCommandMapBuilder(wLCommandMapBuilder, "is", addCommand(new PBuilder("symb|ent|pro|noun", null, true, RelationType._IS), 
				new PBuilder("verb|vbs|be", "is|are|be", false, true, false), 
				new PBuilder("~HasProperty~"), new PBuilder("{"), new PBuilder("pre", null, true),
				new PBuilder("symb|ent|noun|phrase", null, true, false, false, RelationType.IS_), new PBuilder("}")
				));*/
		//e.g. "R is of finite type"
		putToWLCommandMapBuilder(wLCommandMapBuilder, "is", new PBuilder("symb|ent|pro|noun|det", null, true, RelationType._IS), 
				new PBuilder("verb|vbs|be", "is|are|be", false, true, false), new PBuilder(" ~"),
				new PBuilder("HasProperty").makeExprHead(), new PBuilder("~ "), new PBuilder("pre", "of", false),
				new PBuilder("noun|ent", null, true, false, false, RelationType.IS_) );
		
		//negative of above
		//putToWLCommandMapBuilder(wLCommandMapBuilder, "is not", addCommand(new String[] { "symb|ent|pro, , true", "verb|vbs|be, is not|are not|be not, trigger",
				//"Not[\\[Element]]", "symb|ent|adj|phrase, , true, TriggerMathObj" }));
		/*putToWLCommandMapBuilder(wLCommandMapBuilder, "is not", new PBuilder("symb|ent|pro|noun|det", null, true, RelationType._IS), 
				new PBuilder("verb|vbs|be", "not is|is not|are not|not are|be not|not be", false, true, false), 
				new PBuilder("Not[\\[Element]]"), new PBuilder("symb|ent|adj|phrase", null, true, false, true, RelationType.IS_) );*/
		
		//putToWLCommandMapBuilder(wLCommandMapBuilder, "act", addCommand(new String[] { "Action[", "symb|ent|pro, , true", ", act|acts, trigger",
				//";", "symb|ent|pro, , true, TriggerMathObj", ";", ", by, false, OPT", "ent, , true, OPT", "]" }));		
		putToWLCommandMapBuilder(wLCommandMapBuilder, "act", new PBuilder("Action").makeExprHead(), new PBuilder("["), 
				new PBuilder("symb|ent|pro", null, true), new PBuilder(null, "act|acts", false, true, false), new PBuilder(","),
				new PBuilder("symb|ent|pro", null, true, false, true), new PBuilder(",", "OPT"), 
				new PBuilder(null, "by", false, false, "OPT"), new PBuilder(null, null, true, false, "OPT"), new PBuilder("]") );	
		//$f$ maps $x$ to $y$
		putToWLCommandMapBuilder(wLCommandMapBuilder, "map", new PBuilder("Map").makeExprHead(), new PBuilder("["), 
				new PBuilder("symb|ent|pro", null, true), 
				new PBuilder("verb|vbs", "map|maps", false, true, false), new PBuilder(","),
				new PBuilder("symb|ent|pro", null, true, false, true), new PBuilder(","), new PBuilder(null, "to", false), 
				new PBuilder("symb|ent|pro", null, true, false, true), new PBuilder("]") );
		
		//if_assert. As well as " if  ", etc
		//putToWLCommandMapBuilder(wLCommandMapBuilder, "if", addCommand(new String[] { "if|If|let, , trigger", "assert, , true" }));
		//June 29 
		putToWLCommandMapBuilder(wLCommandMapBuilder, "if", new PBuilder("if|If|hyp|let", null, false, true, false), 
				new PBuilder("assert|texAssert", null, true).addRelationType(RelationType.IF));
		
		//putToWLCommandMapBuilder(wLCommandMapBuilder, "then", new PBuilder("then|Then", null, false, true, false), 
			//	new PBuilder("assert|texAssert", null, true) );
		//e.g. "if $D$ is a divisor".
		putToWLCommandMapBuilder(wLCommandMapBuilder, "if", new PBuilder("if|hyp", null, false, true, false), 
				new PBuilder("symb|ent|noun", null, true, false, false, PosTermConnotation.DEFINED,
						RelationType._IS).addRelationType(RelationType.IF), 
				new PBuilder("verb|vbs", "is|are", false, false, false), new PBuilder("\\["), 
				new PBuilder("Element").makeExprHead(), new PBuilder("]"),
				new PBuilder("symb|ent|phrase", null, true, false, false, PosTermConnotation.DEFINING, RelationType.IS_)
				.addRelationType(RelationType.IF),
				new PBuilder("prep", null, false, false, "OPT")
				);
		//"let A be B"; "suppose A is B"
		putToWLCommandMapBuilder(wLCommandMapBuilder, "let", new PBuilder("let|hyp", null, false, true, false), 
				new PBuilder("symb|ent|pro|noun", null, true, false, false, PosTermConnotation.DEFINED,
						RelationType._IS).addRelationType(RelationType.IF), 
				new PBuilder("verb|vbs|be", "is|are|be|to be", false, false, false), new PBuilder("\\["), 
				new PBuilder("Element").makeExprHead(), new PBuilder("]"),
				new PBuilder("symb|ent|phrase", null, true, false, true, PosTermConnotation.DEFINING, RelationType.IS_)
				.addRelationType(RelationType.IF), new PBuilder("prep", null, false, false, "OPT")
				);
		
		//putToWLCommandMapBuilder(wLCommandMapBuilder, "equal to", addCommand(new String[] { "symb|ent, , true",
		//"==", "equal to, , trigger", "symb|ent|phrase, , true, TriggerMathObj" }));
		putToWLCommandMapBuilder(wLCommandMapBuilder, "equal to", new PBuilder("symb|ent", null, true, RelationType._IS_), 
				new PBuilder("==").makeExprHead(), new PBuilder("equal to", null, false, true, false), 
				new PBuilder("symb|ent|phrase", null, true, false, true, RelationType._IS_) );
		
		//putToWLCommandMapBuilder(wLCommandMapBuilder, "auxpass", addCommand(new String[] { "ent, , true",
			//	"auxpass, , trigger_true", "ent|csubj, , true" }));
		putToWLCommandMapBuilder(wLCommandMapBuilder, "auxpass", new PBuilder("ent|symb", null, true), new PBuilder(" ~"),
				new PBuilder("Connective").makeExprHead(), new PBuilder("["),
				new PBuilder("auxpass", null, true, true, false).makeExprHeadArg(), new PBuilder("]~ "),
				new PBuilder("ent|csubj|prep", null, true).addRelationType(RelationType._IS));
		
		//definitions: e.g. "denote by $F$ a field", but note that "call this field $F$" should have different order as to which
		//is the variable and which is being named. The integers indicate their relative order (order in relation to each other)
		//in the posTermList. So the connotation is the connotation of the term in that slot in the final WLCommand order, 
		//*not* the connotation of the term picked up in the sentence in that slot.
		putToWLCommandMapBuilder(wLCommandMapBuilder, "denote by", new PBuilder("def", null, false, true, false), 
				new PBuilder("ent|symb", null, true, false, false, 1, PosTermConnotation.DEFINING).addRelationType(RelationType._IS), 
				new PBuilder("~"), new PBuilder("Named").makeExprHead(), new PBuilder("~"), 
				new PBuilder("ent|symb", null, true, false, false, 0, PosTermConnotation.DEFINED)
				.addRelationType(RelationType.IS_) );
		
		//definitions: e.g. "$F$ denotes a field"
		putToWLCommandMapBuilder(wLCommandMapBuilder, "denote", 
				new PBuilder("ent|symb", null, true, false, false, 1, PosTermConnotation.DEFINING).addRelationType(RelationType._IS), 
				new PBuilder("verb", null, false, true, false), new PBuilder("~"), new PBuilder("Named").makeExprHead(), new PBuilder("~"), 
				new PBuilder("ent|symb", null, true, false, false, 0, PosTermConnotation.DEFINED)
				.addRelationType(RelationType.IS_) );
		
		//"define $F$ to be a field";
		putToWLCommandMapBuilder(wLCommandMapBuilder, "define", new PBuilder("verb", null, false, true, false),
				new PBuilder("ent|symb", null, true, false, false, PosTermConnotation.DEFINED).addRelationType(RelationType._IS), 
				new PBuilder(null, "as|to be|by", false), new PBuilder("~"), new PBuilder("DefinedBy").makeExprHead(), new PBuilder("~"), 
				new PBuilder("ent|symb", null, true, false, false, PosTermConnotation.DEFINING)
				.addRelationType(RelationType.IS_));
		
		//auxpass, eg "is called"
		//putToWLCommandMapBuilder(wLCommandMapBuilder, "is called", addCommand(new String[] { "symb|ent|pro, , true", "auxpass, is called, trigger",
				//"\\[Element]", "symb|ent|adj|phrase, , true, TriggerMathObj" }));
		putToWLCommandMapBuilder(wLCommandMapBuilder, "is called", new PBuilder("symb|ent|pro", null, true, false, false,
				PosTermConnotation.DEFINED).addRelationType(RelationType._IS), 
				new PBuilder("auxpass", null, false, true, false), new PBuilder(" \\["),
				new PBuilder("Element").makeExprHead(), new PBuilder("] "),
				new PBuilder("symb|ent|adj|phrase", null, true, false, false, PosTermConnotation.DEFINING,
						RelationType._IS) );
		
		// ***Hypothesis commands***
		//for every $x$
		//putToWLCommandMapBuilder(wLCommandMapBuilder, "for every", addCommand(new String[] { ", for every|for any, trigger", "\\[ForAll][",
		//	 "ent|symb, , true", "]" }));
		putToWLCommandMapBuilder(wLCommandMapBuilder, "for every", //new PBuilder("assert", null, true, false, "OPT"),
				new PBuilder(null, "for every|for any|for all", false, true, false), new PBuilder("Forall").makeExprHead(),
				new PBuilder("["), new PBuilder("ent|symb|texAssert", null, true, RelationType._IS).addRelationType(RelationType.IF), 
				new PBuilder("]") );
		//head struct?! // parsing "verb ent", for general verb, "take a ..."
		putToWLCommandMapBuilder(wLCommandMapBuilder, "for",
				new PBuilder("hyp", null, false, true, false).setPositionInStructTree(PositionInStructTree.FIRST), 
				new PBuilder("ent|symb", null, true, RelationType.IF).setPositionInStructTree(PositionInStructTree.LAST));
		
		//putToWLCommandMapBuilder(wLCommandMapBuilder, "suppose", addCommand(new String[] { "hyp, , trigger",
			//"assert, , true" })); 
		//note that "hyp" also includes "which is...", which can occur in statements, and not just hypotheses!
		putToWLCommandMapBuilder(wLCommandMapBuilder, "if and only if", new PBuilder("hyp|iff|Iff", null, false, true, false), 
				new PBuilder("assert", null, true).addRelationType(RelationType.IF) );
		//putToWLCommandMapBuilder(wLCommandMapBuilder, "if and only if", addCommand(new PBuilder("hyp|iff|Iff", null, false, true, false), 
			//	new PBuilder("assert", null, true) ));
		//head struct?! HERE
		//putToWLCommandMapBuilder(wLCommandMapBuilder, "consider", addCommand(new String[] { "verb, , trigger", "ent|phrase, , true" }));
		putToWLCommandMapBuilder(wLCommandMapBuilder, "consider", new PBuilder("verb", null, false, true, false), 
				new PBuilder("ent|phrase", null, true, RelationType.IF).addRelationType(RelationType._IS_) );
		
		//e.g. "we have ..."
		//putToWLCommandMapBuilder(wLCommandMapBuilder, "have", addCommand(new String[] { "pro, we, false", "verb, have, trigger", ", , true"}));
		putToWLCommandMapBuilder(wLCommandMapBuilder, "have", new PBuilder("pro", "we", false), new PBuilder("verb", "have", false, true, false), 
				new PBuilder(null, null, true) );
		//"we have "A \subset B"
		putToWLCommandMapBuilder(wLCommandMapBuilder, "have", new PBuilder("pro", "we", true),
				new PBuilder("verb", "have", false, true, false), new PBuilder(null, "that", false, false, "OPT"), 
				new PBuilder("assert|ent", null, true) );
		// "A has property B", eg "chains of ideals have same length"
		//putToWLCommandMapBuilder(wLCommandMapBuilder, "have", addCommand(new String[] { "ent|symb|pro, , true", "verb, have|has, trigger", "\\HasProperty[", ", , true", "]"}));
		putToWLCommandMapBuilder(wLCommandMapBuilder, "have", new PBuilder("ent|symb|pro|noun", null, true).addRelationType(RelationType._IS),
				new PBuilder("verb", "have|has", false, true, false), new PBuilder(" ~"), new PBuilder("HasProperty").makeExprHead(),
				new PBuilder("~ "), new PBuilder(null, null, true).addRelationType(RelationType.IS_) );
				
		/*
		putToWLCommandMapBuilder(wLCommandMapBuilder, "is contained", addCommand(new String[] { "symb|ent|pro, , true", "auxpass, , trigger",
				"\\[Element]", "symb|ent|adj|phrase, , true, TriggerMathObj" }));		
		
		putToWLCommandMapBuilder(wLCommandMapBuilder, "at most", addCommand(new String[] { "symb|ent|pro, , true", "verb|vbs|be, is|are|be, false",
				"<=", "pre, at most, trigger", "symb|ent, , true, TriggerMathObj" }));				
		*/
		
		// label string if to be used as trigger ent/symb, then use these words
		// as trigger system
		// function with radius of convergence
		
		// logical operators
		
		WLCommandMap = wLCommandMapBuilder.build();

	}
	
	private static void putToWLCommandMapBuilder(ImmutableMultimap.Builder<String, ImmutableWLCommand> 
		WLCommandMapBuilder, String triggerWord, PBuilder ... pBuilderAr){
		//this way can also catch exceptions such as IllegalStateException
		
		ImmutableWLCommand wlCommand = addCommand(triggerWord, pBuilderAr);
		WLCommandMapBuilder.put(triggerWord, wlCommand);
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
		return WL_DIRECTIVE_INDEX;
	}	
	
	private static ImmutableWLCommand addCommand(PBuilder ... pBuilderAr){
		return addCommand("", pBuilderAr);
	}
	
	private static ImmutableWLCommand addCommand(String triggerWord, PBuilder ... pBuilderAr) {
		
		//triggerWord = null == triggerWord ? "" : triggerWord;
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
		
		//the scope of this try is too big!
		try{
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
				//should only count nontrivial (non-AUX) optional terms!
				if(AUXINDEX != curPosTerm.positionInMap()){
					optionalTermsCount++;
				}
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
			throw new IllegalWLCommandException("Trigger word index in command " + posList + " is negative!");
			//throw new IllegalArgumentException("Trigger word index in a command cannot be negative!");
		}
		
		//check integrity of WLCommand, e.g. the custom-specified positionInMap entered are correct
		for(PosTerm posTerm : posList){
			
			WLCommandComponent commandComponent = posTerm.commandComponent();
			Integer count = commandsCountPreMap.get(commandComponent);
			
			if(null != count && count <= posTerm.positionInMap()){
				
				throw new IllegalWLCommandException("Index of PosTerm " + posTerm 
						+ " in command " + posList + " is out of bounds!");
			}
		}
		
		commandsCountMap = ImmutableMap.copyOf(commandsCountPreMap);
		posTermList = ImmutableList.copyOf(posList);
		//if(posTermList == null) throw new RuntimeException();

		if(null != optionalTermsGroupCountPreMap){
			optionalTermsGroupCountMap = ImmutableMap.copyOf(optionalTermsGroupCountPreMap);
		}
		/*(Map<WLCommandComponent, Integer> commandsCountMap, 
		ImmutableList<PosTerm> posList, int componentCount, int triggerWordIndex,
		int optionalTermsCount, ImmutableMap<Integer, Integer> optionalTermsMap){*/
		}catch(IllegalWLCommandException e){
			//but this would lead to NPE down the road! better not add to multimap at all,
			//or have EMPTY_COMMAND.
			e.printStackTrace();
			return null;
		}
		
		ImmutableWLCommand.Builder immutableWLCommandBuilder = new ImmutableWLCommand.Builder(triggerWord, 
				commandsCountMap, posTermList, 
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
