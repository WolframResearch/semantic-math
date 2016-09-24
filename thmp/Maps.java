package thmp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

import thmp.search.CollectFreqWords;
import thmp.search.WordFrequency;

import java.util.Map.Entry;

/* contains the dictionaries and hashmaps 
 * used as vocabulary and rules in parsing
 * in ThmP1
 */

public class Maps {

	// make all maps private!

	// protected static HashMap<String, ArrayList<String>> entityMap;
	// map of structures, for all, disj, etc
	// protected static HashMap<String, Rule> structMap;
	protected static Multimap<String, Rule> structMap;

	// structMap for the second run, grammars that shouldn't be
	// used in first run, like ent_verb: there exists
	protected static HashMap<String, String> structMap2;
	//make these private!
	// probability hashmap for pairs of phrase constructs
	// vs noun_verb high prob, verb_verb low prob
	protected static Map<String, Double> probMap;

	protected static Map<String, String> anchorMap;
	// parts of speech map, e.g. "open", "adj"
	protected static Map<String, String> posMap;
	// fluff words, e.g. "the", "a"
	protected static Map<String, String> fluffMap;

	protected static Map<String, String> mathObjMap;
	// map for composite adjectives, eg positive semidefinite
	// value is regex string to be matched
	protected static Map<String, String> adjMap;

	// implmented via ImmutableMultimap. String is trigger word.
	//private static final ImmutableListMultimap<String, FixedPhrase> fixedPhraseMMap;
	private static ImmutableListMultimap<String, FixedPhrase> fixedPhraseMMap;

	// replace string with a break, usully a comma
	protected static ArrayList<String> breakList;

	// list of parts of speech, ent, verb etc
	protected static ArrayList<String> posList;
	
	static{
		buildMap();
	}
	
	public static void initializeWithResource(BufferedReader fixedPhraseBuffer, BufferedReader lexiconBuffer) throws IOException{
		readFixedPhrases(fixedPhraseBuffer);
    	readLexicon(lexiconBuffer);	 
	}
	/*
	// build fixedPhraseMap
	static {
		ImmutableListMultimap.Builder<String, FixedPhrase> fixedPhraseMMapBuilder = ImmutableListMultimap
				.<String, FixedPhrase> builder();
		// should read in from file
		File file = new File("src/thmp/data/fixedPhrases.txt");
		System.out.println("cur working dir in Maps.java: " + System.getProperty("user.dir"));
		try {
			Scanner sc = new Scanner(file);

			while (sc.hasNext()) {
				String line = sc.nextLine();
				String[] fixedPhraseData = line.split("\\s*\\|\\s*");

				if (fixedPhraseData.length < 3)
					continue;

				String trigger = fixedPhraseData[0];
				String triggerRegex = fixedPhraseData[1];
				String pos = fixedPhraseData[2];

				fixedPhraseMMapBuilder.put(trigger, new FixedPhrase(triggerRegex, pos));
			}
			sc.close();
		} catch (FileNotFoundException e) {

			e.printStackTrace();
		}

		fixedPhraseMMap = fixedPhraseMMapBuilder.build();
	} */
	
	public static void readFixedPhrases(){
		ImmutableListMultimap.Builder<String, FixedPhrase> fixedPhraseMMapBuilder = ImmutableListMultimap
				.<String, FixedPhrase> builder();
		// read in from file
		File file = new File("src/thmp/data/fixedPhrases.txt");
		try {
			Scanner sc = new Scanner(file);

			while (sc.hasNext()) {
				String line = sc.nextLine();
				String[] fixedPhraseData = line.split("\\s*\\|\\s*");

				if (fixedPhraseData.length < 3)
					continue;

				String trigger = fixedPhraseData[0];
				String triggerRegex = fixedPhraseData[1];
				String pos = fixedPhraseData[2];

				fixedPhraseMMapBuilder.put(trigger, new FixedPhrase(triggerRegex, pos));
			}
			sc.close();
		} catch (FileNotFoundException e) {

			e.printStackTrace();
		}

		fixedPhraseMMap = fixedPhraseMMapBuilder.build();
		//System.out.print("^^^^fixedPhraseMMap "+fixedPhraseMMap);
	} 

	// get fixed phrase map containing phrases such as "if and only if"
	public static ImmutableListMultimap<String, FixedPhrase> fixedPhraseMap() {
		return fixedPhraseMMap;
	}

	/**
	 * Return part of speech (pos) of word
	 * @param word word
	 * @return pos
	 */
	public static String getPos(String word){
		return posMap.get(word);
	}
	/**
	 * Read in files using stream
	 * @throws IOException
	 */
	private static void readFixedPhrases(BufferedReader fixedPhraseReader) throws IOException {
		ImmutableListMultimap.Builder<String, FixedPhrase> fixedPhraseMMapBuilder = ImmutableListMultimap
				.<String, FixedPhrase> builder();
		// should read in from file
		// File file = new File("src/thmp/data/fixedPhrases.txt");
		// System.out.println("cur working dir in Maps.java: " +
		// System.getProperty("user.dir"));
		String line;
		while ((line = fixedPhraseReader.readLine()) != null) {

			// String line = sc.nextLine();
			String[] fixedPhraseData = line.split("\\s*\\|\\s*");

			if (fixedPhraseData.length < 3)
				continue;

			String trigger = fixedPhraseData[0];
			String triggerRegex = fixedPhraseData[1];
			String pos = fixedPhraseData[2];

			fixedPhraseMMapBuilder.put(trigger, new FixedPhrase(triggerRegex, pos));
		}

		fixedPhraseMMap = fixedPhraseMMapBuilder.build();
		
	}

	/*
	 * build hashmap
	 * 
	 */
	public static void buildMap() {
		// entityMap = new HashMap<String, ArrayList<String>>();
		// ArrayList<String> ppt = new ArrayList<String>();
		// ppt.add("characteristic");
		// entityMap.put("Field", ppt);

		adjMap = new HashMap<String, String>();
		// value is regex string to be matched
		adjMap.put("semidefinite", "positive|negative");
		adjMap.put("independent", "linearly");

		fluffMap = new HashMap<String, String>();
		fluffMap.put("the", "");
		fluffMap.put("an", "");
		fluffMap.put("a", "");
		fluffMap.put("moreover", "");

		// list of parts of speech
		posList = new ArrayList<String>();
		posList.add("mathObj");
		posList.add("verb");
		posList.add("adj");
		posList.add("adverb");
		posList.add("pre");
		posList.add("vbs");
		// posList.add("noun");

		String[] ents = { "quotient", "ideal", "filtration", "combination", "surjection", "presentation", "tex-module",
				"polynomial", "isomorphism", "composition", "kernel", "system", "colimit", "tex-algebra", "projection",
				"subset" };

		// most should already be filtered in entity/property
		// pos should preserve connective words, or stays or
		String[] verbs = { "present", "mean", "say", "order", "direct", "index" };

		String[] adjs = { "first", "successive", "some", "transitive", "reflexive", "together_COMP", "empty", "short",
				"natural", "partial", "multiplicative" };

		String[] adverbs = {};

		String[] pres = { "together with" };

		String[] nouns = { "family", "notion", "permanence", "property", "inclusion", "relation", "row", "notion",
				"inclusion", "case" };

		// list of all the String arrays above
		// ArrayList<String[]> posArraysList = new ArrayList<String[]>();
		// partsOfSpeech must be ordered the same way as posArraysList

		Map<String, String[]> posArraysMap = new HashMap<String, String[]>();
		posArraysMap.put("mathObj", ents);
		posArraysMap.put("adverb", adverbs);
		posArraysMap.put("verb", verbs);
		posArraysMap.put("noun", nouns);
		posArraysMap.put("adj", adjs);
		posArraysMap.put("pre", pres);

		List<String[]> pList = new ArrayList<String[]>(); // pos List

		Iterator<Entry<String, String[]>> posArraysMapIter = posArraysMap.entrySet().iterator();

		while (posArraysMapIter.hasNext()) {
			Entry<String, String[]> curEntry = posArraysMapIter.next();

			String curPos = curEntry.getKey();
			String[] curArray = curEntry.getValue();
			String tempPos = curPos;
			String tempWord;
			String[] tempArray;

			for (int j = 0; j < curArray.length; j++) {
				// if _COMP
				tempWord = curArray[j];
				tempArray = curArray[j].split("_");
				if (tempArray.length > 1 && tempArray[tempArray.length - 1].equals("COMP")) {
					tempPos = curPos + "_" + "COMP";
					tempWord = tempArray[0];
				}
				pList.add(new String[] { tempWord, tempPos });

			}
		}

		/*
		 * for(int i = 0; i < verbs.length; i++){ pList.add(new
		 * String[]{verbs[i], "verb"}); }
		 * 
		 * for(int i = 0; i < adjs.length; i++){ pList.add(new String[]{adjs[i],
		 * "adj"}); }
		 * 
		 * for(int i = 0; i < adverbs.length; i++){ pList.add(new
		 * String[]{adverbs[i], "adverb"}); } for(int i = 0; i < nouns.length;
		 * i++){ pList.add(new String[]{nouns[i], "noun"}); }
		 */

		posMap = new HashMap<String, String>();

		for (int i = 0; i < pList.size(); i++) {
			posMap.put(pList.get(i)[0], pList.get(i)[1]);
		}

		//adds all words from the stock frequent words
		posMap.putAll(WordFrequency.trueFluffWordsPosMap());
		posMap.put("disjoint", "adj");
		posMap.put("perfect", "adj");
		posMap.put("equivalent", "adj");
		posMap.put("finite", "adj");
		posMap.put("linear", "adj");
		posMap.put("invertible", "adj");
		posMap.put("independent", "adj");
		posMap.put("many", "adj");
		posMap.put("every", "adj");
		posMap.put("same", "adj");
		posMap.put("conjugate", "adj");
		posMap.put("symmetric", "adj");
		posMap.put("equal", "adj");
		posMap.put("all", "adj"); //predet
		posMap.put("isomorphic", "adj");
		posMap.put("for", "pre_COMP"); // can be composite word, use first pos
										// if composite not in posmap
		posMap.put("measurable", "adj");
		posMap.put("nondecreasing", "adj");
		posMap.put("positive", "adj");
		posMap.put("negative", "adj");
		posMap.put("abelian", "adj");
		posMap.put("normal", "adj");
		posMap.put("cyclic", "adj");
		posMap.put("dimensional", "adj");
		posMap.put("odd", "adj");
		posMap.put("even", "adj");
		posMap.put("any", "adj");
		posMap.put("simple", "adj");
		posMap.put("unique", "adj");
		posMap.put("more", "adj_COMP");
		posMap.put("more than", "pre");
		posMap.put("nilpotent", "adj");
		posMap.put("most", "adj");
		posMap.put("nontrivial", "adj");
		posMap.put("only", "adj");
		posMap.put("commutative", "adj");
		posMap.put("canonical", "adj");
		posMap.put("exact", "adj");
		posMap.put("injective", "adj");
		posMap.put("surjective", "adj");
		posMap.put("last", "adj");

		// adverbs. Adverbs of the form "adj-ly" are detected by code
		posMap.put("there", "det");

		// adverbs qualify verbs, adj, noun phrases, determiners, clauses etc
		posMap.put("does", "verb_COMP");
		posMap.put("do", "verb_COMP");
		posMap.put("does not", "not");
		posMap.put("do not", "not");
		posMap.put("not", "adverb");

		// nouns that are not mathObj, only put not-so-relevant terms here
		posMap.put("property", "noun");
		posMap.put("form", "noun_COMP");

		// determiners qualify nouns or noun phrases
		posMap.put("each", "adj");
		posMap.put("this", "det");
		posMap.put("both", "det");
		posMap.put("no", "det");

		// parts of speech
		posMap.put("for every", "hyp");
		posMap.put("suppose", "hyp");
		posMap.put("assume", "hyp");
		posMap.put("assuming", "hyp");
		posMap.put("for all", "hyp");
		posMap.put("for each", "hyp");
		posMap.put("for any", "hyp");

		// prepositions
		posMap.put("or", "or");
		posMap.put("and", "and");
		posMap.put("at most", "pre");
		posMap.put("is", "vbs_comp");
		posMap.put("at", "pre_COMP");
		posMap.put("if", "if_COMP");
		posMap.put("if and", "if_COMP");
		posMap.put("if and only", "if_COMP");
		posMap.put("if and only if", "iff");
		posMap.put("then", "then");
		posMap.put("between", "pre");
		// between... -> between, and...->and, between_and->between_and

		posMap.put("in", "pre_comp");
		posMap.put("from", "pre");
		posMap.put("to", "pre");

		posMap.put("equal", "verb_COMP");
		posMap.put("equal to", "pre");
		posMap.put("on", "pre");
		posMap.put("let", "let");
		posMap.put("be", "be");
		posMap.put("of", "pre"); // of is primarily used as anchor
		posMap.put("over", "pre");
		posMap.put("with", "pre");
		posMap.put("by", "pre");
		posMap.put("as", "pre");
		posMap.put("such", "pre_COMP");
		posMap.put("such that", "cond");
		posMap.put("where", "hyp");
		posMap.put("which is", "hyp");
		posMap.put("which are", "hyp");
		posMap.put("that is", "hyp");
		posMap.put("that are", "hyp");

		// pronouns
		posMap.put("it", "pro");
		posMap.put("we", "pro");
		posMap.put("they", "pro");
		posMap.put("their", "poss");
		posMap.put("its", "poss");
		// relative pronouns
		posMap.put("whose", "rpro");
		posMap.put("which", "rpro_COMP");
		posMap.put("that", "rpro_COMP");
		posMap.put("whom", "rpro");

		// verbs, verbs map does not support -ing form, ie divide->dividing
		// 3rd person singular form that ends in "es" are checked with
		// the "es" stripped
		posMap.put("belong", "verb");
		posMap.put("divide", "verb");
		posMap.put("extend", "verb");
		posMap.put("exist", "verb");
		posMap.put("consist", "verb");
		posMap.put("call", "verb");
		posMap.put("contain", "verb");
		posMap.put("are", "verb"); //////////// ***
		posMap.put("have", "verb");
		posMap.put("obtain", "verb");
		posMap.put("generate", "verb");
		posMap.put("replace", "verb");
		posMap.put("act", "verb");
		posMap.put("follow", "verb");
		posMap.put("denote", "verb");
		posMap.put("define", "verb");
		posMap.put("has", "vbs");

		// special participles
		posMap.put("given", "parti_COMP");		
		posMap.put("been", "parti");
		posMap.put("written", "parti");
		posMap.put("given by", "partiby");

		// build in quantifiers into structures, forall (indicated
		// by for all, has)
		// entityMap.put("for all", null); //change null, use regex
		// entityMap.put("there exists", null);

		// map for math objects
		mathObjMap = new HashMap<String, String>();
		mathObjMap.put("characteristic", "mathObj");
		mathObjMap.put("set", "mathObj");
		mathObjMap.put("Fp", "mathObj");
		mathObjMap.put("transformation", "mathObj");
		mathObjMap.put("ring", "mathObj");
		mathObjMap.put("matrix", "mathObj");
		mathObjMap.put("function", "mathObj");
		mathObjMap.put("bilinear form", "mathObj");
		mathObjMap.put("basis", "mathObj");
		mathObjMap.put("sum", "COMP");
		mathObjMap.put("direct sum", "mathObj");
		mathObjMap.put("number", "mathObj");
		mathObjMap.put("partition", "mathObj");
		// composite math objects
		mathObjMap.put("field", "COMP");
		mathObjMap.put("space", "COMP");
		mathObjMap.put("vector space", "mathObj");
		mathObjMap.put("finite field", "mathObj");
		mathObjMap.put("vector field", "mathObj");
		mathObjMap.put("vector", "mathObj");
		mathObjMap.put("angle", "mathObj");
		mathObjMap.put("zero", "mathObj");
		mathObjMap.put("extension", "COMP"); /////////// ****
		mathObjMap.put("field extension", "mathObj");
		mathObjMap.put("element", "noun");
		mathObjMap.put("group", "COMP");
		mathObjMap.put("symmetric group", "mathObj");
		mathObjMap.put("p group", "mathObj");
		mathObjMap.put("p subgroup", "mathObj");
		mathObjMap.put("subgroup", "COMP");
		mathObjMap.put("automorphism group", "mathObj");
		mathObjMap.put("abelian group", "mathObj");
		mathObjMap.put("type", "COMP");
		mathObjMap.put("cycle type", "mathObj");
		mathObjMap.put("cycle", "mathObj");
		mathObjMap.put("decomposition", "COMP");
		mathObjMap.put("entry", "mathObj");
		mathObjMap.put("cycle decomposition", "mathObj");
		mathObjMap.put("measure", "mathObj");
		mathObjMap.put("sequence", "mathObj");
		mathObjMap.put("integer", "mathObj");
		mathObjMap.put("class", "COMP");
		mathObjMap.put("conjugacy class", "mathObj");
		mathObjMap.put("subgroup", "mathObj");
		mathObjMap.put("automorphism", "mathObj");
		mathObjMap.put("order", "mathObj");
		mathObjMap.put("conjugation", "mathObj");
		mathObjMap.put("prime", "mathObj");
		mathObjMap.put("power", "mathObj");
		mathObjMap.put("nilpotence", "mathObj");
		mathObjMap.put("map", "mathObj");
		mathObjMap.put("diagram", "mathObj_COMP");
		mathObjMap.put("commutative diagram", "mathObj");
		mathObjMap.put("module", "mathObj");

		mathObjMap.put("tex", "mathObj"); // TEMPORARY
		mathObjMap.put("li", "mathObj_COMP");
		mathObjMap.put("li la", "mathObj");
		
		// put in template matching, prepositions, of, by, with

		// conjunctions, and, or, for
		anchorMap = new HashMap<String, String>();

		// anchors, contains of, with
		anchorMap = new HashMap<String, String>();
		anchorMap.put("of", "of");
		// anchorMap.put("that is", "that is"); //careful with spaces
		// local "or", ie or of the same types
		// anchorMap.put("or", "or");

		// struct map, statement/global structures
		// can be written as,
		// structMap = new HashMap<String, String>();
		// structMap = new HashMap<String, Rule>();
		structMap = ArrayListMultimap.<String, Rule> create();
		// structMap.put("for all", "forall");

		// "is" implies assertion <--remove this case, "is" should be
		// interpreted as verb
		structMap.put("is_symb", new Rule("is", 1));
		structMap.put("is_int", new Rule("is", 1));
		structMap.put("is_ent", new Rule("is", 1));
		structMap.put("is_or", new Rule("is", 1));
		structMap.put("ent_is", new Rule("assert", 1));
		structMap.put("symb_is", new Rule("assert", 1));
		//structMap.put("if_assert", new Rule("ifstate", 1));
		structMap.put("iff_assert", new Rule("Iff", 1));
		structMap.put("if_hypo", new Rule("If", 1));
		structMap.put("then_assert", new Rule("Then", 1));

		// expression, e.g. a map from A to B
		// structMap.put("ent", new Rule("expr", 1));
		//structMap.put("ifstate_thenstate", new Rule("ifthen", 1));
		structMap.put("If_Then", new Rule("IfThen", 1));
		structMap.put("Iff_Then", new Rule("IffThen", 1));
		structMap.put("has_ent", new Rule("hasent", 1));
		structMap.put("or_ent", new Rule("orent", 1));
		structMap.put("ent_orent", new Rule("or", 1));
		structMap.put("or_symb", new Rule("orsymb", 1));
		structMap.put("symb_orsymb", new Rule("or", 1));
		structMap.put("or_assert", new Rule("orass", 1));
		structMap.put("ent_orass", new Rule("or", 1));
		structMap.put("or_is", new Rule("assert", 1));
		structMap.put("assert_orass", new Rule("or", 1));
		structMap.put("ent_ppt", new Rule("newchild", 1));
		// structMap.put("ent_ent", new Rule("ent", 1));

		// e.g. between A and B.
		// structMap.put("pre_conj", new Rule("prep", 1));
		// structMap.put("pre_disj", new Rule("prep", 1));

		// preposition_entity, eg "over field", "on ...". Create new child in
		// this case
		// latter ent child of first ent
		// structMap.put("pre_ent", new Rule("preent", 1));
		// prep stands for "pre phrase"
		structMap.put("pre_ent", new Rule("prep", 1));
		structMap.put("pre_adj", new Rule("prep", 0.8));
		structMap.put("ent_prep", new Rule("newchild", 1));
		structMap.put("pre_symb", new Rule("prep", 1));
		structMap.put("parti_prep", new Rule("phrase", 1));
		structMap.put("pre_phrase", new Rule("prep", 1));
		structMap.put("pre_np", new Rule("prep", 1));
		structMap.put("pre_expr", new Rule("prep", 1));
		structMap.put("pre_pobj", new Rule("prep", 1));
		structMap.put("csubj_prep", new Rule("csubj", 1));
		structMap.put("noun_prep", new Rule("np", 1));
		structMap.put("noun_verbphrase", new Rule("assert", 1));
		structMap.put("pre_noun", new Rule("prep", 1));
		structMap.put("gerund_verbphrase", new Rule("assert", 1));
		structMap.put("parti_assert", new Rule("hypo", .8));
		// participle: called, need to take care of "said" etc
		structMap.put("parti_ent", new Rule("partient", 1));
		structMap.put("ent_partient", new Rule("newchild", 1));
		structMap.put("ent_hypo", new Rule("newchild", .9));
		// phrases: been there, x in X,
		structMap.put("parti_adj", new Rule("phrase", 1));
		structMap.put("symb_prep", new Rule("phrase", 1));

		//////////
		structMap.put("symb_adj", new Rule("phrase", 0.7));

		//structMap.put("ent_pre", new Rule("exp1", .3));
		//structMap.put("exp1_phrase", new Rule("exp2", .1));

		// symb_adj
		// noun_symb should be combined in code

		structMap.put("pre_noun", new Rule("ppt", 1)); // nounphrase
		structMap.put("adj_symb", new Rule("phrase", 1));
		
		structMap.put("adj_noun", new Rule("phrase", 1));
		structMap.put("adj_prep", new Rule("phrase", .85));
		structMap.put("gerund_noun", new Rule("gerundp", 1)); // gerundphrase
		structMap.put("ent_gerundp", new Rule("newchild", 1));

		// involving nums
		structMap.put("pre_num", new Rule("prep", 1));

		ArrayList<String[]> entList = new ArrayList<String[]>();

		for (int i = 0; i < ents.length; i++) {
			String newEnt = ents[i];
			if (newEnt.split("_").length > 1) {
				// composite
				entList.add(new String[] { newEnt.split("_")[0], "mathObj_COMP" });
			} else {
				entList.add(new String[] { newEnt, "mathObj" });
			}
		}

		for (int i = 0; i < entList.size(); i++) {
			mathObjMap.put(entList.get(i)[0], entList.get(i)[1]);
		}

		// structMap.put("from ", "wildcard"); structMap.put("to", "wildcard");
		// structMap.put("fromto", "newchild");
		//////////// combine preposition with whatever comes
		// verb_ent, not including past tense verbs, only present tense
		structMap.put("verb_ent", new Rule("verbphrase", 1));
		structMap.put("verb_csubj", new Rule("verbphrase", 1));
		structMap.put("be_ent", new Rule("verbphrase", .7));
		structMap.put("verb_adj", new Rule("verbphrase", 1));
		structMap.put("verb_pro", new Rule("verbphrase", 1));
		structMap.put("verb_symb", new Rule("verbphrase", 1));
		structMap.put("verb_np", new Rule("verbphrase", 1));
		structMap.put("verb_prep", new Rule("verbphrase", 1));
		structMap.put("verb_num", new Rule("verbphrase", 1));
		structMap.put("verb_np", new Rule("verbphrase", 1));
		structMap.put("verb_pre", new Rule("verbphrase", 1));
		structMap.put("verb_phrase", new Rule("verbphrase", 1));
		structMap.put("verb_partient", new Rule("verbphrase", 1));
		structMap.put("verb_noun", new Rule("verbphrase", 1));
		structMap.put("det_verbphrase", new Rule("assert", 1));
		structMap.put("verb_parti", new Rule("verbphrase", 1));
		structMap.put("auxpass_pobj", new Rule("verbphrase", 1)); // passive
		structMap.put("auxpass_ent", new Rule("verbphrase", 1)); 
		structMap.put("auxpass_adj", new Rule("verbphrase", 1));	// auxiliary

		//TEMP RULE
		//structMap.put("assert_csubj", new Rule("", 1));
		
		structMap.put("vbs_ent", new Rule("verbphrase", 1));
		structMap.put("vbs_adj", new Rule("verbphrase", .9));
		structMap.put("vbs_pro", new Rule("verbphrase", 1));
		structMap.put("vbs_symb", new Rule("verbphrase", 1));
		structMap.put("vbs_np", new Rule("verbphrase", 1));
		structMap.put("vbs_prep", new Rule("verbphrase", 1));
		structMap.put("vbs_num", new Rule("verbphrase", 1));
		structMap.put("vbs_np", new Rule("verbphrase", 1));
		structMap.put("vbs_pre", new Rule("verbphrase", .7));
		structMap.put("vbs_phrase", new Rule("verbphrase", 1));
		structMap.put("vbs_partient", new Rule("verbphrase", 1));
		structMap.put("vbs_noun", new Rule("verbphrase", 1));
		structMap.put("vbs_parti", new Rule("verbphrase", 1));

		structMap.put("symb_verbphrase", new Rule("assert", 1));
		structMap.put("ent_verbphrase", new Rule("assert", 1));
		structMap.put("pro_verbphrase", new Rule("assert", 1));
		structMap.put("pro_csubj", new Rule("pobj", 1)); // could be iobj ->
															// indirect obj
		structMap.put("poss_csubj", new Rule("pobj", .95));
		structMap.put("predet_csubj", new Rule("csubj", 1));
		structMap.put("np_verbphrase", new Rule("assert", 1));
		structMap.put("symb_auxpass", new Rule("assert", .8));
		structMap.put("np_auxpass", new Rule("assert", .8));
		structMap.put("ent_auxpass", new Rule("assert", .8));
		structMap.put("pro_auxpass", new Rule("assert", .8));

		structMap.put("verb_assert", new Rule("verbphrase", 1));
		structMap.put("vbs_assert", new Rule("verbphrase", 1));
		structMap.put("hypo_assert", new Rule("assert", 1));
		structMap.put("verbphrase_prep", new Rule("verbphrase", 1));
		structMap.put("vbs_partiby", new Rule("verb", 1));
		structMap.put("partiby_ent", new Rule("phrase", 1));
		structMap.put("partiby_noun", new Rule("phrase", 1));
		structMap.put("partiby_symb", new Rule("phrase", 1));
		structMap.put("partiby_expr", new Rule("phrase", 1));
		structMap.put("be_parti", new Rule("verb", 1));
		structMap.put("be_partiby", new Rule("verb", 1));
		structMap.put("be_parti", new Rule("be_parti", .8));
		structMap.put("verb_be_parti", new Rule("verb", 1));
		structMap.put("det_verb", new Rule("assert", .5));

		structMap.put("disj_verbphrase", new Rule("assert", 1));
		structMap.put("conj_verbphrase", new Rule("assert", 1));
		structMap.put("csubj_verbphrase", new Rule("assert", 1));
		structMap.put("csubj_verb", new Rule("assert", .5));

		structMap.put("let_symb", new Rule("let", 1));
		structMap.put("be_ent", new Rule("be", 1));
		structMap.put("be_symb", new Rule("be", .7));
		structMap.put("let_assert", new Rule("letbe", 1));
		structMap.put("let_be", new Rule("letbe", 1));
		structMap.put("let_ent", new Rule("let", 1));
		structMap.put("if_assert", new Rule("If", 1));
		structMap.put("assert_If", new Rule("assert", 1));
		structMap.put("assert_hypo", new Rule("assert", 1));
		structMap.put("assert_prep", new Rule("assert", 1));
		structMap.put("assert_iff", new Rule("assert", 1));
		structMap.put("hyp_hyp", new Rule("hyp", 1));
		structMap.put("hyp_assert", new Rule("hypo", 1));
		structMap.put("hyp_ent", new Rule("hypo", 1));
		structMap.put("cond_ent", new Rule("Cond", 1));
		structMap.put("hyp_phrase", new Rule("hypo", 1));
		structMap.put("hyp_adj", new Rule("hypo", 1));
		structMap.put("hyp_symb", new Rule("hypo", 1));
		structMap.put("rpro_ent", new Rule("rproent", 1));
		structMap.put("ent_rproent", new Rule("newchild", 1));
		structMap.put("rpro_verbphrase", new Rule("phrase", 1));
		structMap.put("rpro_assert", new Rule("phrase", 1));
		structMap.put("parti_phrase", new Rule("hypo", .8));
		structMap.put("amod_noun", new Rule("csubj", 1));
		structMap.put("amod_ent", new Rule("csubj", 1)); // structMap.put("gerund_np",
															// new Rule("csubj",
															// 1));
		structMap.put("pro_ent", new Rule("csubj", 1));

		// eg "property that a is b"
		structMap.put("noun_phrase", new Rule("np", 1));
		structMap.put("ent_phrase", new Rule("newchild", 1));
		structMap.put("ent_ppt", new Rule("newchild", 1));
		structMap.put("ent_expr", new Rule("addstruct", 1)); // ent_tex. Add
																// member to
																// Struct.struct

		structMap.put("adverb_adj", new Rule("adj", 1)); /// *******
		structMap.put("adverb_verbphrase", new Rule("assert", 1));

		// grammar rules for 2nd run
		structMap2 = new HashMap<String, String>();
		structMap2.put("ent_verb", "assert");
		structMap2.put("ent_verb", "assert");

		// probability map for pair constructs.
		// need to gather/compute prob from labeled data
		// used in round 1 parsing
		// "FIRST"/"LAST" indicate positions in sentence.
		probMap = new HashMap<String, Double>();
		probMap.put("FIRST", 1.);
		probMap.put("LAST", 1.);
		// ent_
		probMap.put("mathObj_anchor", .85);
		probMap.put("mathObj_verb", .8);
		probMap.put("mathObj_pre", .8);

		// pre_
		probMap.put("pre_mathObj", .8);
		probMap.put("pre_adj", .6);
		probMap.put("pre_mathObj", .6);

		// verb_
		probMap.put("verb_mathObj", .6);
		probMap.put("vbs_mathObj", .6); // probMap.put("verb_adj", .6);

		// adj_
		probMap.put("adj_mathObj", .9);

		// for adding combinations to structMap
		/*
		 * ArrayList<String> structs = new ArrayList<String>();
		 * structs.add("has");
		 * 
		 * //for loop constructing more structs to put in maps, e.g. entityhas,
		 * entityis for(int i = 0; i < structs.size(); i++){ //entityhas, vs
		 * forall
		 * 
		 * }
		 */

	}

	/**
	 * reads in list of words not in dictionary yet, puts them in appropriate
	 * dictionaries according to classification in the text
	 * 
	 * @throws FileNotFoundException
	 */
	public static void readLexicon() throws FileNotFoundException {
		File file = new File("src/thmp/data/lexicon.txt");
		Scanner sc = new Scanner(file);
		String[] lineAr;		

		while (sc.hasNextLine()) {
			String pos = "", word = "", replacement = "";
			
			String nextLine = sc.nextLine();
			lineAr = nextLine.split(" ");
			if (lineAr.length < 2)
				continue;
			else if (lineAr.length == 2) {
				word = lineAr[0];
				pos = lineAr[1];
			}
			// compound first word, e.g "comm alg"
			else {
				//fluff
				if(nextLine.charAt(0) == '"'){
					Pattern pattern = Pattern.compile("\"(.*)\" ([^\\s]+) \"(.*)\"");
					Matcher matcher = pattern.matcher(nextLine);
					// what about partial finds?
					if (matcher.find()) {
						word = matcher.group(1);
						pos = matcher.group(2);
						replacement = matcher.group(3);
					}
				}else{
					//composite words
					//System.out.println("lineAr "  + Arrays.toString(lineAr));
					pos = lineAr[lineAr.length-1];
					
					for(int i = 0; i < lineAr.length - 1; i++){
						word += lineAr[i] + " ";
					}
					word = word.trim();
					//System.out.println("WORD" + word);
				}				
			}

			// format: "new_word pos"
			// if fluff word: "new_word fluff replacement", eg "to be" fluff
			// "as"
			switch (pos) {
			case "ent":
				mathObjMap.put(word, "mathObj");
				break;
			case "ent_comp":
				mathObjMap.put(word, "mathObj_COMP");
				break;
			case "fluff":
				replacement = replacement.equals("") ? word : replacement;
				fluffMap.put(word, replacement);
				break;
			default:
				posMap.put(word, pos);
			}
		}
		sc.close();
	}

	/**
	 * Reads from BufferedReader
	 * @throws FileNotFoundException
	 */
	private static void readLexicon(BufferedReader lexiconReader) throws IOException {
		String[] lineAr;
		String pos = "", word = "", replacement = "";
		String nextLine;
		
		while ((nextLine = lexiconReader.readLine()) != null) {
			lineAr = nextLine.split(" ");
			if (lineAr.length < 2)
				continue;
			else if (lineAr.length == 2) {
				word = lineAr[0];
				pos = lineAr[1];
			}
			// compound first word, e.g "comm alg"
			else {
				Pattern pattern = Pattern.compile("\"(.*)\" ([^\\s]+) \"(.*)\"");
				Matcher matcher = pattern.matcher(nextLine);
				// what about partial finds?
				if (matcher.find()) {
					word = matcher.group(1);
					pos = matcher.group(2);
					replacement = matcher.group(3);
				}

			}

			// format: "new_word pos"
			// if fluff word: "new_word fluff replacement", eg "to be" fluff
			// "as"
			switch (pos) {
			case "ent":
				mathObjMap.put(word, "mathObj");
				break;
			case "ent_comp":
				mathObjMap.put(word, "mathObj_COMP");
				break;
			case "fluff":
				replacement = replacement.equals("") ? word : replacement;
				fluffMap.put(word, replacement);
				break;
			default:
				posMap.put(word, pos);
			}
		}
	}
}
