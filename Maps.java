import java.util.ArrayList;
import java.util.HashMap;

/* contains the dictionaries and hashmaps 
 * used as vocabulary and rules in parsing
 * in ThmP1
 */

public class Maps {

	//protected static HashMap<String, ArrayList<String>> entityMap;
	//map of structures, for all, disj,  etc
	protected static HashMap<String, String> structMap;
	protected static HashMap<String, String> anchorMap;
	//parts of speech map, e.g. "open", "adj"
	protected static HashMap<String, String> posMap;
	//fluff words, e.g. "the", "a"
	protected static HashMap<String, String> fluffMap;
		
	protected static HashMap<String, String> mathObjMap;
	//map for composite adjectives, eg positive semidefinite
	//value is regex string to be matched
	protected static HashMap<String, String> adjMap;
	
	/* build hashmap
	 * 
	 */
	protected static void buildMap(){
		//entityMap = new HashMap<String, ArrayList<String>>();
		//ArrayList<String> ppt = new ArrayList<String>();
		//ppt.add("characteristic"); 
		//entityMap.put("Field", ppt);		
		
		adjMap = new HashMap<String, String>();
		//value is regex string to be matched
		adjMap.put("semidefinite", "positive|negative");
		adjMap.put("independent", "linearly");
		
		fluffMap = new HashMap<String, String>();
		fluffMap.put("the", "the");
		fluffMap.put("a", "a");
		
		//most should already be filtered in entity/property
		//pos should preserve connective words, or stays or
		posMap = new HashMap<String, String>();
		posMap.put("disjoint", "adj"); posMap.put("perfect", "adj");
		posMap.put("finite", "adj"); posMap.put("linear", "adj"); posMap.put("invertible", "adj");
		posMap.put("independent", "adj"); posMap.put("many", "adj");
		//posMap.put("every", "every");		
		posMap.put("same", "adj"); posMap.put("conjugate", "adj"); posMap.put("symmetric", "adj");
		posMap.put("equal", "adj"); posMap.put("all", "adj"); 
		posMap.put("for", "pre_COMP"); //can be composite word, use first pos if composite not in posmap
		posMap.put("measurable", "adj"); posMap.put("nondecreasing", "adj");
		posMap.put("positive", "adj"); posMap.put("negative", "adj");
		
		//determiners
		posMap.put("each", "det"); posMap.put("each", "det"); 
		
		//parts of speech
		posMap.put("for every", "hyp"); posMap.put("suppose", "hyp");		
		posMap.put("for all", "hyp");
		
		//prepositions
		posMap.put("or", "or"); posMap.put("and", "and"); 
		posMap.put("is", "verb"); posMap.put("at", "pre"); posMap.put("if", "if");
		posMap.put("then", "then"); posMap.put("between", "pre"); 
		//between... -> between, and...->and, between_and->between_and
		
		posMap.put("in", "pre"); posMap.put("from", "pre"); posMap.put("to", "pre");
		posMap.put("on", "pre"); posMap.put("let", "let"); posMap.put("be", "be");
		posMap.put("of", "pre"); //of is primarily used as anchor
		posMap.put("over", "pre"); posMap.put("with", "pre");
		posMap.put("by", "pre"); 
		
		//pronouns
		posMap.put("their", "pro"); posMap.put("it", "pro"); posMap.put("we", "pro"); 
		posMap.put("they", "pro"); 
		//relative pronouns
		posMap.put("whose", "rpro"); posMap.put("which", "rpro"); posMap.put("that", "rpro");
		posMap.put("whom", "rpro");
		
		//verbs, verbs map does not support -ing form, ie divide->dividing
		//3rd person singular form that ends in "es" are checked with 
		//the "es" stripped
		posMap.put("divide", "verb"); posMap.put("extend", "verb");	
		posMap.put("consist", "verb"); posMap.put("call", "verb");
		posMap.put("are", "verb"); ////////////***
		posMap.put("have", "verb"); posMap.put("obtain", "verb");
		posMap.put("replace", "verb");
		
		//build in quantifiers into structures, forall (indicated
		//by for all, has)
		//entityMap.put("for all", null); //change null, use regex
		//entityMap.put("there exists", null); 
		
		//map for math objects
		mathObjMap = new HashMap<String, String>();
		mathObjMap.put("characteristic", "mathObj"); mathObjMap.put("set", "mathObj");
		mathObjMap.put("Fp", "mathObj"); mathObjMap.put("transformation", "mathObj");
		mathObjMap.put("ring", "mathObj"); mathObjMap.put("matrix", "mathObj"); 
		mathObjMap.put("function", "mathObj");
		mathObjMap.put("basis", "mathObj"); mathObjMap.put("sum", "mathObj");
		mathObjMap.put("number", "mathObj"); mathObjMap.put("partition", "mathObj");
		//composite math objects
		mathObjMap.put("field", "COMP"); mathObjMap.put("space", "COMP");
		mathObjMap.put("vector space", "mathObj");
		mathObjMap.put("finite field", "mathObj"); mathObjMap.put("vector field", "mathObj");
		mathObjMap.put("vector", "mathObj"); mathObjMap.put("angle", "mathObj");
		mathObjMap.put("zero", "mathObj"); mathObjMap.put("extension", "COMP"); ///////////****
		mathObjMap.put("field extension", "mathObj"); mathObjMap.put("element", "mathObj");
		mathObjMap.put("group", "COMP"); mathObjMap.put("symmetric group", "mathObj");
		mathObjMap.put("type", "COMP"); mathObjMap.put("cycle type", "mathObj");
		mathObjMap.put("cycle", "mathObj"); mathObjMap.put("decomposition", "COMP");
		mathObjMap.put("entry", "mathObj"); mathObjMap.put("cycle decomposition", "mathObj");
		mathObjMap.put("measure", "mathObj"); mathObjMap.put("sequence", "mathObj");
		mathObjMap.put("integer", "mathObj"); mathObjMap.put("class", "COMP");
		mathObjMap.put("conjugacy class", "mathObj");
		
		//put in template matching, prepositions, of, by, with
		
		//conjunctions, and, or, for
		anchorMap = new HashMap<String, String>();	
		
		//anchors, contains   of, with
		anchorMap = new HashMap<String, String>();		
		anchorMap.put("of", "of");
		anchorMap.put("that is", "that is"); //careful with spaces		
		//local "or", ie or of the same types
		//anchorMap.put("or", "or"); 		
		
		//struct map, statement/global structures
		//can be written as, 
		structMap = new HashMap<String, String>();	
		//structMap.put("for all", "forall");		
		structMap.put("is", "is");  //these not necessary any more
		structMap.put("are", "are"); structMap.put("has", "has");
		
		//"is" implies assertion 
		structMap.put("is_symb", "is"); structMap.put("is_int", "is");
		structMap.put("is_ent", "is"); structMap.put("is_or", "is");
		structMap.put("ent_is", "assert");
		structMap.put("symb_is", "assert"); structMap.put("if_assert", "ifstate");	
		structMap.put("iff_assert", "ifstate");
		structMap.put("then_assert", "thenstate");
		
		//expression, e.g. a map from A to B
		//structMap.put("ent", "expr");
		structMap.put("ifstate_thenstate", "ifthen");
		structMap.put("has_ent", "hasent");	
		structMap.put("or_ent", "orent"); structMap.put("ent_orent", "or");
		structMap.put("or_symb", "orsymb"); structMap.put("symb_orsymb", "or");
		structMap.put("or_assert", "orass"); structMap.put("ent_orass", "or");
		structMap.put("or_is", "assert"); structMap.put("assert_orass", "or");
		structMap.put("ent_adj", "ent"); structMap.put("ent_ppt", "ent");
		//e.g. between A and B.
		structMap.put("pre_and", "prep"); structMap.put("pre_or", "prep");
		
		//preposition_entity, eg "over field", "on ...". Create new child in this case
		//latter ent child of first ent
		//structMap.put("pre_ent", "preent");
		//prep stands for "pre phrase"
		structMap.put("pre_ent", "prep"); structMap.put("ent_prep", "newchild");
		structMap.put("pre_symb", "prep"); structMap.put("parti_prep", "phrase");
		//participle: called, need to take care of "said" etc
		structMap.put("parti_ent", "partient"); structMap.put("ent_partient", "newchild");
		structMap.put("parti_adj", "phrase"); 
		
		//structMap.put("from   ", "wildcard"); structMap.put("to", "wildcard"); structMap.put("fromto", "newchild");
		///////////////combine preposition with whatever comes
		
		//verb_ent, not including past tense verbs, only present tense
		structMap.put("verb_ent", "verbphrase"); structMap.put("verb_adj", "verbphrase");	
		structMap.put("verb_and", "verbphrase");
		structMap.put("verb_pre", "verb"); structMap.put("verb_phrase", "verbphrase");
		structMap.put("verb_symb", "verbphrase"); structMap.put("symb_verbphrase", "assert");
		structMap.put("ent_verbphrase", "assert"); structMap.put("pro_verbphrase", "assert");
		structMap.put("verb_assert", "verbphrase");
		
		structMap.put("let_symb", "let"); structMap.put("be_ent", "be"); structMap.put("let_be", "letbe");
		structMap.put("if_assert", "If"); structMap.put("assert_If", "assert"); 
		structMap.put("hyp_assert", "hypo"); structMap.put("hyp_ent", "hypo");
		structMap.put("hyp_symb", "hypo"); structMap.put("rpro_ent", "rproent");
		structMap.put("ent_rproent", "newchild"); structMap.put("anchor_symb", "anchorphrase");
		structMap.put("ent_anchorphrase", "ent");
		
		structMap.put("adverb_adj", "adj"); ///****************
		
		//for adding combinations to structMap
		ArrayList<String> structs = new ArrayList<String>();
		structs.add("has");
		
		//for loop constructing more structs to put in maps, e.g. entityhas, entityis
		for(int i = 0; i < structs.size(); i++){
			//entityhas, vs forall
			
		}
		
	}
	
	
}
