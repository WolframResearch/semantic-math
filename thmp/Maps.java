package thmp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

/* contains the dictionaries and hashmaps 
 * used as vocabulary and rules in parsing
 * in ThmP1
 */

public class Maps {

	//protected static HashMap<String, ArrayList<String>> entityMap;
	//map of structures, for all, disj,  etc
	protected static HashMap<String, String> structMap;
	//structMap for the second run, grammars that shouldn't be 
	//used in first run, like ent_verb: there exists
	protected static HashMap<String, String> structMap2;

	//probability hashmap for pairs of phrase constructs
	//vs noun_verb high prob, verb_verb low prob
	protected static HashMap<String, Double> probMap;
	
	protected static HashMap<String, String> anchorMap;
	//parts of speech map, e.g. "open", "adj"
	protected static HashMap<String, String> posMap;
	//fluff words, e.g. "the", "a"
	protected static HashMap<String, String> fluffMap;
		
	protected static HashMap<String, String> mathObjMap;
	//map for composite adjectives, eg positive semidefinite
	//value is regex string to be matched
	protected static HashMap<String, String> adjMap;
	
	//list of parts of speech, ent, verb etc
	protected static ArrayList<String> posList;

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
		fluffMap.put("the", "the"); fluffMap.put("an", "an");
		fluffMap.put("a", "a"); fluffMap.put("moreover", "moreover");
		
		//list of parts of speech
		posList = new ArrayList<String>();
		posList.add("ent"); posList.add("verb"); posList.add("adj");
		posList.add("adverb"); posList.add("pre"); 
		
		String[] ents = {"quotient", "ideal", "filtration", "combination", "surjection",
				"presentation", "tex-module", "polynomial", "isomorphism", "composition",
				"kernel", "system", "colimit", "tex-algebra", "projection"};
		
		//most should already be filtered in entity/property
		//pos should preserve connective words, or stays or
		String[] verbs = {"present", "mean", "say", "order", "direct", "index"};
		
		String[] adjs = {"first", "successive", "some", "transitive", "reflexive", "together",
				"empty", "short", "natural"};
		
		String[] adverbs = {};
				
		String[] nouns = {"family", "notion", "permanence", "property", "inclusion", "relation",
				"row", "notion", "inclusion"};
		
		//list of all the String arrays above
		//ArrayList<String[]> posArraysList = new ArrayList<String[]>();
		//partsOfSpeech must be ordered the same way as posArraysList
		
		HashMap<String, String[]> posArraysMap = new HashMap<String, String[]>();
		posArraysMap.put("mathObj", ents); posArraysMap.put("adverb", adverbs);
		posArraysMap.put("verb", verbs); posArraysMap.put("noun", nouns);
		posArraysMap.put("adj", adjs);
				
		ArrayList<String[]> pList = new ArrayList<String[]>(); //pos List
		
		Iterator<Entry<String, String[]>> posArraysMapIter = posArraysMap.entrySet().iterator();
		
		while(posArraysMapIter.hasNext()){
			Entry<String, String[]> curEntry = posArraysMapIter.next();
			
			String curPos = curEntry.getKey();
			String[] curArray = curEntry.getValue();
			String tempPos = curPos;
			String tempWord;
			String[] tempArray;
			
			for(int j = 0; j < curArray.length; j++){
				//if _COMP				
				tempWord = curArray[j];
				tempArray = curArray[j].split("_");
				if(tempArray.length > 1 && tempArray[tempArray.length-1].equals("COMP")){
					tempPos = curPos + "_" + "COMP";
					tempWord = tempArray[0];
				}
				pList.add(new String[]{tempWord, tempPos});
				
			}
		}
		
		/* for(int i = 0; i < verbs.length; i++){
			pList.add(new String[]{verbs[i], "verb"});
		}
		
		for(int i = 0; i < adjs.length; i++){ pList.add(new String[]{adjs[i], "adj"}); }
		
		for(int i = 0; i < adverbs.length; i++){ pList.add(new String[]{adverbs[i], "adverb"}); }
		for(int i = 0; i < nouns.length; i++){ pList.add(new String[]{nouns[i], "noun"}); }
		*/
		
		posMap = new HashMap<String, String>();

		for(int i = 0; i < pList.size(); i++){
			posMap.put(pList.get(i)[0], pList.get(i)[1]);
		}
		
		posMap.put("disjoint", "adj"); posMap.put("perfect", "adj"); posMap.put("equivalent", "adj");
		posMap.put("finite", "adj"); posMap.put("linear", "adj"); posMap.put("invertible", "adj");
		posMap.put("independent", "adj"); posMap.put("many", "adj");
		posMap.put("every", "adj");		
		posMap.put("same", "adj"); posMap.put("conjugate", "adj"); posMap.put("symmetric", "adj");
		posMap.put("equal", "adj"); posMap.put("all", "adj"); posMap.put("isomorphic", "adj");
		posMap.put("for", "pre_COMP"); //can be composite word, use first pos if composite not in posmap
		posMap.put("measurable", "adj"); posMap.put("nondecreasing", "adj");
		posMap.put("positive", "adj"); posMap.put("negative", "adj"); posMap.put("abelian", "adj");
		posMap.put("normal", "adj"); posMap.put("cyclic", "adj"); posMap.put("dimensional", "adj");
		posMap.put("odd", "adj"); posMap.put("even", "adj"); posMap.put("any", "adj");
		posMap.put("simple", "adj"); posMap.put("unique", "adj"); posMap.put("more", "adj_COMP");
		posMap.put("more than", "pre"); posMap.put("nilpotent", "adj"); posMap.put("most", "adj"); 
		posMap.put("nontrivial", "adj"); posMap.put("only", "adj"); posMap.put("commutative", "adj"); 
		posMap.put("canonical", "adj"); posMap.put("exact", "adj"); posMap.put("injective", "adj"); 
		posMap.put("surjective", "adj"); posMap.put("last", "adj"); 
		
		//adverbs. Adverbs of the form "adj-ly" are detected by code
		posMap.put("there", "det");
		
		//adverbs qualify verbs, adj, noun phrases, determiners, clauses etc
		posMap.put("does", "verb_COMP"); posMap.put("do", "verb_COMP"); posMap.put("does not", "not");
		posMap.put("do not", "not"); posMap.put("not", "adverb");
		
		//nouns that are not mathObj, only put not-so-relevant terms here
		posMap.put("property", "noun"); posMap.put("form", "noun_COMP");  
		
		//determiners qualify nouns or noun phrases
		posMap.put("each", "det"); posMap.put("this", "det"); posMap.put("both", "det"); 
		posMap.put("no", "det");
		
		//parts of speech
		posMap.put("for every", "hyp"); posMap.put("suppose", "hyp"); posMap.put("assume", "hyp");		
		posMap.put("for all", "hyp");
		
		//prepositions
		posMap.put("or", "or"); posMap.put("and", "and"); posMap.put("at most", "pre");
		posMap.put("is", "verb"); posMap.put("at", "pre_COMP"); posMap.put("if", "if_COMP");
		posMap.put("if and", "if_COMP"); posMap.put("if and only", "if_COMP"); 
		posMap.put("if and only if", "iff");
		posMap.put("then", "then"); posMap.put("between", "pre"); 
		//between... -> between, and...->and, between_and->between_and
		
		posMap.put("in", "pre"); posMap.put("from", "pre"); posMap.put("to", "pre");
		
		posMap.put("equal", "verb_COMP"); posMap.put("equal to", "pre");
		posMap.put("on", "pre"); posMap.put("let", "let"); posMap.put("be", "be");
		posMap.put("of", "pre"); //of is primarily used as anchor
		posMap.put("over", "pre"); posMap.put("with", "pre");
		posMap.put("by", "pre"); posMap.put("as", "pre"); posMap.put("such", "pre_COMP"); 
		posMap.put("such that", "hyp"); posMap.put("so", "pre"); posMap.put("where", "hyp");
		posMap.put("which is", "hyp"); posMap.put("which are", "hyp"); posMap.put("that is", "hyp");
		posMap.put("that are", "hyp");
		
		//pronouns
		posMap.put("their", "pro"); posMap.put("it", "pro"); posMap.put("we", "pro"); 
		posMap.put("they", "pro"); 
		//relative pronouns
		posMap.put("whose", "rpro"); posMap.put("which", "rpro_COMP"); posMap.put("that", "rpro_COMP");
		posMap.put("whom", "rpro"); 
		
		//verbs, verbs map does not support -ing form, ie divide->dividing
		//3rd person singular form that ends in "es" are checked with 
		//the "es" stripped
		posMap.put("divide", "verb"); posMap.put("extend", "verb");	posMap.put("exist", "verb");
		posMap.put("consist", "verb"); posMap.put("call", "verb"); posMap.put("contain", "verb");
		posMap.put("are", "verb"); ////////////***
		posMap.put("have", "verb"); posMap.put("obtain", "verb"); posMap.put("generate", "verb");
		posMap.put("replace", "verb"); posMap.put("act", "verb"); posMap.put("follow", "verb"); 
		posMap.put("denote", "verb"); posMap.put("define", "verb"); posMap.put("has", "verb");
		
		
		//special participles
		posMap.put("given", "hyp"); posMap.put("been", "parti"); 
		
		//build in quantifiers into structures, forall (indicated
		//by for all, has)
		//entityMap.put("for all", null); //change null, use regex
		//entityMap.put("there exists", null); 
		
		//map for math objects
		mathObjMap = new HashMap<String, String>();
		mathObjMap.put("characteristic", "mathObj"); mathObjMap.put("set", "mathObj");
		mathObjMap.put("Fp", "mathObj"); mathObjMap.put("transformation", "mathObj");
		mathObjMap.put("ring", "mathObj"); mathObjMap.put("matrix", "mathObj"); 
		mathObjMap.put("function", "mathObj"); mathObjMap.put("bilinear form", "mathObj");
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
		mathObjMap.put("p group", "mathObj"); mathObjMap.put("p subgroup", "mathObj");
		mathObjMap.put("subgroup", "COMP");
		mathObjMap.put("automorphism group", "mathObj"); mathObjMap.put("abelian group", "mathObj");
		mathObjMap.put("type", "COMP"); mathObjMap.put("cycle type", "mathObj");
		mathObjMap.put("cycle", "mathObj"); mathObjMap.put("decomposition", "COMP");
		mathObjMap.put("entry", "mathObj"); mathObjMap.put("cycle decomposition", "mathObj");
		mathObjMap.put("measure", "mathObj"); mathObjMap.put("sequence", "mathObj");
		mathObjMap.put("integer", "mathObj"); mathObjMap.put("class", "COMP");
		mathObjMap.put("conjugacy class", "mathObj"); mathObjMap.put("subgroup", "mathObj");
		mathObjMap.put("automorphism", "mathObj"); mathObjMap.put("order", "mathObj");
		mathObjMap.put("conjugation", "mathObj"); mathObjMap.put("prime", "mathObj"); 
		mathObjMap.put("power", "mathObj"); mathObjMap.put("nilpotence", "mathObj"); 
		mathObjMap.put("map", "mathObj"); mathObjMap.put("diagram", "mathObj_COMP");
		mathObjMap.put("commutative diagram", "mathObj"); mathObjMap.put("module", "mathObj"); 
		
		mathObjMap.put("tex", "mathObj"); //TEMPORARY
		
		//put in template matching, prepositions, of, by, with
		
		//conjunctions, and, or, for
		anchorMap = new HashMap<String, String>();	
		
		//anchors, contains   of, with
		anchorMap = new HashMap<String, String>();		
		anchorMap.put("of", "of");
		//anchorMap.put("that is", "that is"); //careful with spaces		
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
		structMap.put("ent_ent", "ent");
		
		//e.g. between A and B.
		//structMap.put("pre_conj", "prep"); structMap.put("pre_disj", "prep");
		
		//preposition_entity, eg "over field", "on ...". Create new child in this case
		//latter ent child of first ent
		//structMap.put("pre_ent", "preent");
		//prep stands for "pre phrase"
		structMap.put("pre_ent", "prep"); structMap.put("ent_prep", "newchild");
		structMap.put("pre_symb", "prep"); structMap.put("parti_prep", "phrase");
		structMap.put("pre_phrase", "prep"); structMap.put("pre_nounphrase", "prep");
		structMap.put("noun_prep", "nounphrase"); structMap.put("noun_verbphrase", "assert");
		structMap.put("pre_noun", "prep"); structMap.put("gerund_verbphrase", "assert");
		//participle: called, need to take care of "said" etc
		structMap.put("parti_ent", "partient"); structMap.put("ent_partient", "newchild");
		//phrases: been there, x in X, 
		structMap.put("parti_adj", "phrase"); structMap.put("symb_prep", "phrase");
		
		structMap.put("pre_noun", "ppt"); //nounphrase
		structMap.put("adj_noun", "noun");
		
		//involving nums
		structMap.put("pre_num", "prep");		
		
		
		ArrayList<String[]> entList = new ArrayList<String[]>();
		
		for(int i = 0; i < ents.length; i++){
			if(ents[i].split("_").length > 1){
				//composite
				entList.add(new String[]{ents[i].split("_")[0], "mathObj_COMP"});
			}else{
				entList.add(new String[]{ents[i], "mathObj"});
			}
		}
		
		for(int i = 0; i < entList.size(); i++){
			mathObjMap.put(entList.get(i)[0], entList.get(i)[1]);
		}
		
		//structMap.put("from   ", "wildcard"); structMap.put("to", "wildcard"); structMap.put("fromto", "newchild");
		////////////combine preposition with whatever comes				
		//verb_ent, not including past tense verbs, only present tense
		structMap.put("verb_ent", "verbphrase"); structMap.put("verb_adj", "verbphrase");
		structMap.put("verb_np", "verbphrase"); structMap.put("verb_prep", "verbphrase");
		structMap.put("verb_num", "verbphrase"); structMap.put("verb_nounphrase", "verbphrase");
		structMap.put("verb_pre", "verbphrase"); structMap.put("verb_phrase", "verbphrase");
		structMap.put("verb_partient", "verbphrase"); structMap.put("verb_noun", "verbphrase");
		structMap.put("det_verbphrase", "assert");
		structMap.put("verb_symb", "verbphrase"); structMap.put("symb_verbphrase", "assert");
		structMap.put("ent_verbphrase", "assert"); structMap.put("pro_verbphrase", "assert");
		structMap.put("nounphrase_verbphrase", "assert");
		structMap.put("verb_assert", "verbphrase"); structMap.put("verbphrase_prep", "verbphrase");
		structMap.put("disj_verbphrase", "assert"); structMap.put("conj_verbphrase", "assert");
		
		structMap.put("let_symb", "let"); structMap.put("be_ent", "be"); structMap.put("let_be", "letbe");
		structMap.put("let_ent", "let");
		structMap.put("if_assert", "If"); structMap.put("assert_If", "assert");
		structMap.put("assert_iff", "assert"); 
		structMap.put("hyp_assert", "hypo"); structMap.put("hyp_ent", "hypo");
		structMap.put("hyp_phrase", "hypo");
		structMap.put("hyp_symb", "hypo"); structMap.put("rpro_ent", "rproent");
		structMap.put("ent_rproent", "newchild"); structMap.put("rpro_verbphrase", "phrase");
		structMap.put("rpro_assert", "phrase"); 
		
		//eg "property that a is b"
		structMap.put("noun_phrase", "nounphrase"); structMap.put("ent_phrase", "newchild");
		structMap.put("ent_ppt", "newchild");
		
		structMap.put("adverb_adj", "adj"); ///*******		
		structMap.put("adverb_verbphrase", "assert");
		
		//grammar rules for 2nd run
		structMap2 = new HashMap<String, String>();
		structMap2.put("ent_verb", "assert"); structMap2.put("ent_verb", "assert");
		
		//probability map for pair constructs. 
		//need to gather/compute prob from labeled data
		//used in round 1 parsing
		probMap = new HashMap<String, Double>();
		probMap.put("FIRST", 1.); probMap.put("LAST", 1.); probMap.put("ent_anchor", .85);
		probMap.put("ent_verb", .8); probMap.put("ent_pre", .8);
		probMap.put("pre_ent", .8); probMap.put("verb_ent", .6);
		probMap.put("adj_ent", .9);
		
		//for adding combinations to structMap
		/*
		ArrayList<String> structs = new ArrayList<String>();
		structs.add("has");
		
		//for loop constructing more structs to put in maps, e.g. entityhas, entityis
		for(int i = 0; i < structs.size(); i++){
			//entityhas, vs forall
			
		}  */
		
		
		
	}
	
	
}
