package thmp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/* 
 * contains hashtable of entities (keys) and properties (values)
 */

public class ThmP1 {

	// should all be StructH's, since these are ent's
	private static HashMap<String, Struct> namesMap;

	// private static HashMap<String, ArrayList<String>> entityMap =
	// Maps.entityMap;
	// map of structures, for all, disj, etc
	private static HashMap<String, String> structMap;
	private static HashMap<String, String> anchorMap;
	// parts of speech map, e.g. "open", "adj"
	private static HashMap<String, String> posMap;
	// fluff words, e.g. "the", "a"
	private static HashMap<String, String> fluffMap;

	private static HashMap<String, String> mathObjMap;
	// map for composite adjectives, eg positive semidefinite
	// value is regex string to be matched
	private static HashMap<String, String> adjMap;
	private static HashMap<String, Double> probMap;

	// list of parts of speech, ent, verb etc
	private static ArrayList<String> posList;

	//fluff type, skip when adding to parsed ArrayList
	private static String FLUFF = "Fluff";
	
	//private static final File unknownWordsFile;
	private static final Path unknownWordsFile = Paths.get("unknownWords.txt");
	private static final Path parsedExprFile = Paths.get("parsedExpr.txt");
	
	private static final List<String> unknownWords = new ArrayList<String>();
	private static final List<String> parsedExpr = new ArrayList<String>();
	
	// part of speech, last resort after looking up entity property maps
	// private static HashMap<String, String> pos;

	public static void buildMap() throws FileNotFoundException {
		Maps.buildMap();
		Maps.readLexicon();
		structMap = Maps.structMap;
		anchorMap = Maps.anchorMap;
		posMap = Maps.posMap;
		fluffMap = Maps.fluffMap;
		mathObjMap = Maps.mathObjMap;
		adjMap = Maps.adjMap;
		probMap = Maps.probMap;
		posList = Maps.posList;
		// list of given names, like F in "field F", for bookkeeping later
		// hashmap contains <name, entity> pairs
		// need to incorporate multi-letter names, like sigma
		namesMap = new HashMap<String, Struct>();
	}

	/**
	 * 
	 * @param str
	 *            string to be tokenized
	 * @return
	 * @throws IOException 
	 */
	public static ArrayList<Struct> tokenize(String[] str) throws IOException {

		// .....change to arraylist of Pairs, create Pair class
		// LinkedHashMap<String, String> linkedMap = new LinkedHashMap<String,
		// String>();

		// list of indices of "proper" math objects, e.g. "field", but not e.g.
		// "pair"
		ArrayList<Integer> mathIndexList = new ArrayList<Integer>();
		// list of indices of anchor words, e.g. "of"
		ArrayList<Integer> anchorList = new ArrayList<Integer>();

		// list of each word with their initial type, adj, noun,
		ArrayList<Pair> pairs = new ArrayList<Pair>();
		boolean addIndex = true; // whether to add to pairIndex
		//int pairIndex = 0;
		for (int i = 0; i < str.length; i++) {

			String curWord = str[i];

			if (curWord.matches("^\\s*$"))
				continue;

			// strip away special chars '(', ')', etc
			curWord = curWord.replaceAll("\\(|\\)", "");
			//remove this and ensure curWord is used subsequently 
			//instead of str[i]
			str[i] = curWord; 

			String type = "mathObj";
			int strlen = str[i].length();

			//detect latex expressions, mark them as "mathObj" for now
			if(curWord.charAt(0) == '$'){
				String latexExpr = curWord;
				int stringLength = str.length;
				
				if(i < stringLength-1 && !curWord.matches("\\$[^$]+\\$[^\\s]*") &&
						(curWord.charAt(strlen - 1) != '$' 
						|| strlen == 2)){
					i++;
					curWord = str[i];
					if(i < stringLength-1 && curWord.equals("")){
						curWord = str[++i];
					}
					while(i < stringLength && curWord.charAt(curWord.length() - 1) != '$'){
						latexExpr += " " + curWord;
						i++;
						
						if(i == stringLength) break;
						
						curWord = i<stringLength-1 && str[i].equals("") ? str[++i] : str[i];							
						
					}
					if(i < stringLength && str[i].charAt(str[i].length() - 1) == '$')
						latexExpr += " " + str[i];
				}else if(curWord.matches("\\$[^$]\\$")){
					type = "symb";
				}
				
				Pair pair = new Pair(latexExpr, type);
				pairs.add(pair);		
				if(type.equals("mathObj"))
					mathIndexList.add(pairs.size() - 1);
				
				continue;
			}
			
			// primitive way to handle plural forms: if ends in "s"
			String singular = "";
			String singular2 = ""; // ending in "ies"
			String singular3 = ""; // ending in "es"
			if (strlen > 0 && curWord.charAt(strlen - 1) == 's') {
				singular = curWord.substring(0, strlen - 1);
			}

			if (strlen > 3 && curWord.substring(strlen - 3, strlen).equals("ies")) {
				singular2 = curWord.substring(0, strlen - 3) + 'y';
			}

			if (strlen > 2 && curWord.substring(strlen - 2, strlen).equals("es")) {
				singular3 = curWord.substring(0, strlen - 2);
			}

			if (Maps.mathObjMap.containsKey(curWord) || mathObjMap.containsKey(singular)) {

				String tempWord = mathObjMap.containsKey(singular) ? singular : curWord;
				int pairsSize = pairs.size();
				int k = 1;

				// if composite math noun, eg "finite field"
				while (i - k > -1 && mathObjMap.containsKey(str[i - k] + " " + tempWord)
						&& mathObjMap.get(str[i - k] + " " + tempWord).equals("mathObj")) {

					// remove previous pair from pairs if it has new match
					// pairs.size should be > 0, ie previous word should be
					// classified already
					if (pairs.size() > 0 && pairs.get(pairsSize - 1).word().equals(str[i - k])) {

						// remove from mathIndexList if already counted
						if (mathObjMap.containsKey(pairs.get(pairs.size() - 1).word())) {
							mathIndexList.remove(mathIndexList.size() - 1);
						}
						pairs.remove(pairsSize - 1);

						addIndex = false;
					}

					tempWord = str[i - k] + " " + tempWord;
					curWord = str[i - k] + " " + curWord;
					k++;
				}

				// if previous Pair is also an ent, fuse them
				pairsSize = pairs.size();
				if (pairs.size() > 0 && pairs.get(pairsSize - 1).pos().matches("mathObj")) {
					pairs.get(pairsSize - 1).set_word(pairs.get(pairsSize - 1).word() + " " + curWord);
					continue;
				}

				Pair pair = new Pair(curWord, "mathObj");
				pairs.add(pair);
				mathIndexList.add(pairs.size() - 1);

			}

			else if (anchorMap.containsKey(curWord)) {
				Pair pair = new Pair(curWord, "anchor");
				// anchorList.add(pairIndex);
				pairs.add(pair);

				int pairsSize = pairs.size();
				anchorList.add(pairsSize - 1);
			}
			// check part of speech
			else if (posMap.containsKey(curWord)) {

				// composite words, such as "for all",
				String temp = curWord, pos = curWord;
				String tempPos = posMap.get(temp);

				while (tempPos.length() > 4 && tempPos.substring(tempPos.length() - 4, tempPos.length()).matches("COMP|comp")
						&& i < str.length - 1) {

					curWord = temp;
					temp = temp + " " + str[i + 1];
					if (!posMap.containsKey(temp)) {
						break;
					}
					tempPos = posMap.get(temp);
					i++;
				}

				Pair pair;
				if (posMap.containsKey(temp)) {
					pos = posMap.get(temp);
					pos = pos.split("_")[0];
					pair = new Pair(temp, pos);
				} else {
					pos = posMap.get(curWord);
					pos = pos.split("_")[0];
					pair = new Pair(curWord, pos);
				}

				int pairsSize = pairs.size();

				// if adverb-adj pair, eg "clearly good"
				if (pairs.size() > 0 && posMap.get(curWord).equals("adj")
						&& pairs.get(pairsSize - 1).pos().equals("adverb")) {
					curWord = pairs.get(pairsSize - 1).word() + " " + curWord;
					// remove previous Pair
					pairs.remove(pairsSize - 1);
					pair = new Pair(curWord, "adj");
					addIndex = false;
				}

				pairs.add(pair);
			}
			// if plural form of noun
			else if (posMap.containsKey(singular) && posMap.get(singular).equals("noun")
					|| posMap.containsKey(singular2) && posMap.get(singular2).equals("noun")
					|| posMap.containsKey(singular3) && posMap.get(singular3).equals("noun")) {
				pairs.add(new Pair(curWord, "noun"));
			}
			// classify words with dashes; eg sesqui-linear
			else if (curWord.split("-").length > 1) {
				String[] splitWords = curWord.split("-");

				String lastTerm = splitWords[splitWords.length - 1];
				String lastTermS1 = singular.matches("") ? "" : singular.split("-")[splitWords.length - 1];
				String lastTermS2 = singular2.matches("") ? "" : singular2.split("-")[splitWords.length - 1];
				String lastTermS3 = singular3.matches("") ? "" : singular3.split("-")[splitWords.length - 1];

				String searchKey = "";
				if (posMap.containsKey(lastTerm))
					searchKey = lastTerm;
				else if (posMap.containsKey(lastTermS1))
					searchKey = lastTermS1;
				else if (posMap.containsKey(lastTermS2))
					searchKey = lastTermS2;
				else if (posMap.containsKey(lastTermS3))
					searchKey = lastTermS3;

				if (!searchKey.equals("")) {

					Pair pair = new Pair(curWord, posMap.get(searchKey).split("_")[0]);
					pairs.add(pair);
				} // if lastTerm is entity, eg A-module
				if (mathObjMap.containsKey(lastTerm) || mathObjMap.containsKey(lastTermS1)
						|| mathObjMap.containsKey(lastTermS2) || mathObjMap.containsKey(lastTermS3)) {

					Pair pair = new Pair(curWord, "mathObj");
					pairs.add(pair);
					mathIndexList.add(pairs.size() - 1);
				}
			}
			// check again for verbs ending in 'es' & 's'
			else if (strlen > 0 && curWord.charAt(strlen - 1) == 's'
					&& posMap.containsKey(str[i].substring(0, strlen - 1))
					&& posMap.get(str[i].substring(0, strlen - 1)).equals("verb")) {

				Pair pair = new Pair(str[i], "verb");
				pairs.add(pair);
			} else if (strlen > 1 && curWord.charAt(strlen - 1) == 's' && str[i].charAt(str[i].length() - 2) == 'e'
					&& posMap.containsKey(str[i].substring(0, strlen - 2))
					&& posMap.get(str[i].substring(0, strlen - 2)).equals("verb")) {
				Pair pair = new Pair(str[i], "verb");
				pairs.add(pair);

			}
			// adverbs that end with -ly that haven't been screened off before
			else if (strlen > 1 && curWord.substring(strlen - 2, strlen).equals("ly")) {
				Pair pair = new Pair(str[i], "adverb");
				pairs.add(pair);
			}
			// participles and gerunds. Need special list for words such as
			// "given"
			else if (strlen > 1 && curWord.substring(strlen - 2, strlen).equals("ed")
					&& (posMap.containsKey(str[i].substring(0, strlen - 2))
							&& posMap.get(str[i].substring(0, strlen - 2)).equals("verb")
							|| posMap.containsKey(str[i].substring(0, strlen - 1))
									&& posMap.get(str[i].substring(0, strlen - 1)).equals("verb"))) {

				// if next word is "by", then
				String curPos = "parti";
				int pairsSize = pairs.size();
				//if next word is "by"
				if (str.length > i + 1 && str[i + 1].equals("by")) {
					curPos = "partiby";
					curWord = curWord + " by";
					i++;
				}
				//previous word is "is, are", then group with previous word to verb
				//e.g. "is called"
				else if(pairsSize > 0 && pairs.get(pairsSize - 1).word().matches("is|are")){
					
					curWord = pairs.get(pairsSize - 1).word() + " " + curWord;
					pairs.remove(pairsSize - 1);
					
					curPos = "verb";
				}
				//if previous word is adj, "finitely presented"
				else if(pairsSize > 0 && pairs.get(pairsSize - 1).pos().equals("adverb")){
					
					curWord = pairs.get(pairsSize - 1).word() + " " + curWord;
					pairs.remove(pairsSize - 1);
					
					curPos = "adj";
				}
				// if next word is entity, then adj
				else if (str.length > i + 1 && mathObjMap.containsKey(str[i + 1])) {
					
					// combine with adverb if previous one is adverb
					if (pairsSize > 0 && pairs.get(pairsSize - 1).pos().equals("adverb")) {
						curWord = pairs.get(pairsSize - 1).word() + " " + curWord;
						pairs.remove(pairsSize - 1);
					}
					curPos = "adj";
				}

				Pair pair = new Pair(curWord, curPos);
				pairs.add(pair);
			} else if (strlen > 2 && curWord.substring(strlen - 3, strlen).equals("ing")
					&& (posMap.containsKey(curWord.substring(0, strlen - 3))
							&& posMap.get(curWord.substring(0, strlen - 3)).equals("verb")
							// verbs ending in "e"
							|| (posMap.containsKey(curWord.substring(0, strlen - 3) + 'e')
									&& posMap.get(curWord.substring(0, strlen - 3) + 'e').equals("verb")))) {
				Pair pair = new Pair(str[i], "gerund");
				pairs.add(pair);
			} else if (curWord.matches("[a-zA-Z]")) {
				// variable/symbols

				Pair pair = new Pair(str[i], "symb");
				pairs.add(pair);
			}
			// Get numbers. Incorporate written-out numbers, eg "two"
			else if (curWord.matches("^\\d+$")) {
				Pair pair = new Pair(str[i], "num");
				pairs.add(pair);
			} else if (!curWord.matches(" ")) { // try to minimize this case.

				System.out.println("word not in dictionary: " + curWord);
				pairs.add(new Pair(curWord, ""));
				
				//write unknown words to file		
				unknownWords.add(curWord);
				
				
			} else { // curWord doesn't count

				continue;
			}

			//if (addIndex) {
			//	pairIndex++;
			//}
			addIndex = true;

			int pairsSize = pairs.size();
			
			if(pairsSize > 0){
				Pair pair = pairs.get(pairsSize - 1);

				// combine "no" and "not" with verbs
				if (pair.pos().equals("verb")) {
					if (pairs.size() > 1 && (pairs.get(pairsSize - 2).word().matches("not|no")
							|| pairs.get(pairsSize - 2).pos().matches("not"))) {
						String newWord = pair.word().matches("is|are") 
								? "not" : "not " + pair.word();
						pair.set_word(newWord);
						pairs.remove(pairsSize - 2);
					}
					
					if (i + 1 < str.length && str[i + 1].matches("not|no")) {
						String newWord = pair.word().matches("is|are") 
								? "not" : "not " + pair.word();
						pair.set_word(newWord);
						i++;
					}
				}
			}

		}

		
		// If phrase isn't in dictionary, ie has type "", then use probMap to
		// postulate type, if possible
		Pair curpair;
		int len = pairs.size();

		double bestCurProb = 0;
		String prevType = "", nextType = "", tempCurType = "", bestCurType = "";

		int posListSz = posList.size();

		for (int index = 0; index < len; index++) {
			curpair = pairs.get(index);
			if (curpair.pos().equals("")) {

				prevType = index > 0 ? pairs.get(index - 1).pos() : "";
				nextType = index < len - 1 ? pairs.get(index + 1).pos() : "";

				// iterate through list of types, ent, verb etc
				for (int k = 0; k < posListSz; k++) {
					tempCurType = posList.get(k);

					prevType = index > 0 ? prevType + "_" + tempCurType : "FIRST";
					nextType = index < len - 1 ? tempCurType + "_" + nextType : "LAST";

					if (probMap.get(prevType) != null && probMap.get(nextType) != null) {
						if (probMap.get(prevType) * probMap.get(prevType) > bestCurProb) {
							bestCurProb = probMap.get(prevType) * probMap.get(prevType);
							bestCurType = tempCurType;
						}
					}
				}
				pairs.get(index).set_pos(bestCurType);

				if (bestCurType.equals("ent"))
					mathIndexList.add(index);
			}
		}

		// map of math entities, has mathObj + ppt's
		ArrayList<StructH<HashMap<String, String>>> mathEntList = new ArrayList<StructH<HashMap<String, String>>>();

		// second run, combine adj with math ent's
		for (int j = 0; j < mathIndexList.size(); j++) {

			int index = mathIndexList.get(j);
			String mathObj = pairs.get(index).word();
			pairs.get(index).set_pos(String.valueOf(j));

			StructH<HashMap<String, String>> tempStructH = new StructH<HashMap<String, String>>("ent");
			HashMap<String, String> tempMap = new HashMap<String, String>();
			tempMap.put("name", mathObj);

			//if next pair is also ent, and is latex expression
			if(j < mathIndexList.size()-1 && mathIndexList.get(j+1) == index+1){
				Pair nextPair = pairs.get(index+1);
				String name = nextPair.word();
				if(name.contains("$")){
					tempMap.put("tex", name);
					nextPair.set_pos(String.valueOf(j));
					mathIndexList.remove(j+1);
				}								
			}
			
			// look right two places in pairs, if symbol found, add it to namesMap
			// if it's the given name for an ent.
			int pairsSize = pairs.size();
			if (index + 1 < pairsSize && pairs.get(index + 1).pos().equals("symb")) {
				pairs.get(index + 1).set_pos(String.valueOf(j));
				String givenName = pairs.get(index + 1).word();
				tempMap.put("called", givenName);
				namesMap.put(givenName, tempStructH);

			} else if ((pairsSize + 2 < pairsSize && pairs.get(index + 2).pos().equals("symb"))) {
				pairs.get(index + 2).set_pos(String.valueOf(j));
				String givenName = pairs.get(index + 2).word();
				tempMap.put("called", givenName);
				namesMap.put(givenName, tempStructH);
			}
			// look left one place
			if (index > 0 && pairs.get(index - 1).pos().equals("symb")) {
				pairs.get(index - 1).set_pos(String.valueOf(j));
				String givenName = pairs.get(index - 1).word();
				tempMap.put("called", givenName);
				namesMap.put(givenName, tempStructH);
			}
			
			//combine nouns with ent's right after, ie noun_ent
			if (index > 0 && pairs.get(index - 1).pos().matches("noun")) {
				pairs.get(index - 1).set_pos(String.valueOf(j));
				String prevNoun = pairs.get(index-1).word();				
				tempMap.put("name", tempMap.get("name") + " " + prevNoun);
			}

			// look to left and right
			int k = 1;
			// combine multiple adjectives into entities
			// ...get more than adj ... multi-word descriptions
			// set the pos as the current index in mathEntList
			// adjectives or determiners
			while (index - k > -1 && pairs.get(index - k).pos().matches("adj|det|num")) {

				String curWord = pairs.get(index - k).word();
				// look for composite adj (two for now)
				if (index - k - 1 > -1 && adjMap.containsKey(curWord)) {
					// if composite adj
					////////// develop adjMap further
					if (pairs.get(index - k - 1).word().matches(adjMap.get(curWord))) {
						curWord = pairs.get(index - k - 1).word() + " " + curWord;
						// mark pos field to indicate entity
						pairs.get(index - k).set_pos(String.valueOf(j));
						k++;
					}
				}

				tempMap.put(curWord, "ppt");
				// mark the pos field in those absorbed pairs as index in
				// mathEntList
				pairs.get(index - k).set_pos(String.valueOf(j));
				k++;
			}

			// combine multiple adj connected by "and/or"
			while (index - k - 1 > -1 && pairs.get(index - k).pos().matches("or|and")) {
				if (pairs.get(index - k - 1).pos().equals("adj")) {
					// set pos() of or/and to the right index
					pairs.get(index - k).set_pos(String.valueOf(j));
					String curWord = pairs.get(index - k - 1).word();
					tempMap.put(curWord, "ppt");
					pairs.get(index - k - 1).set_pos(String.valueOf(j));
				}
				k++;
			}

			// look forwards
			k = 1;
			while (index + k < pairs.size() && pairs.get(index + k).pos().matches("adj|num")) {
				/// implement same as above

				tempMap.put(pairs.get(index + k).word(), "ppt");
				pairs.get(index + k).set_pos(String.valueOf(j));
				k++;
			}

			tempStructH.set_struct(tempMap);
			mathEntList.add(tempStructH);
		}		
		
		// combine anchors into entities. Such as "of," "has"
		for (int j = anchorList.size() - 1; j > -1; j--) {
			int index = anchorList.get(j);
			String anchor = pairs.get(index).word();

			// combine entities, like in case of "of"
			switch (anchor) {
			case "of":
				// the expression before this anchor is an entity
				if (index > 0  && index + 1 < pairs.size()) {
					
					Pair nextPair = pairs.get(index + 1);
					Pair prevPair = pairs.get(index - 1);

					// ent of ent
					if (prevPair.pos().matches("\\d+$") && nextPair.pos().matches("\\d+$")) {
						int mathObjIndex = Integer.valueOf(prevPair.pos());
						StructH<HashMap<String, String>> tempStruct = mathEntList.get(mathObjIndex);
						
						pairs.get(index).set_pos(nextPair.pos());
						Struct childStruct = mathEntList.get(Integer.valueOf(nextPair.pos()));
						tempStruct.add_child(childStruct, "of");
						// set to null instead of removing, to keep indices
						// right
						mathEntList.set(Integer.valueOf(nextPair.pos()), null);

					} // "noun of ent". 
					else if(prevPair.pos().matches("noun") && nextPair.pos().matches("\\d+$")) {
						int mathObjIndex = Integer.valueOf(nextPair.pos());
						//Combine the something into the ent
						StructH<HashMap<String, String>> tempStruct = mathEntList.get(mathObjIndex);
						
						String entName = tempStruct.struct().get("name");
						tempStruct.struct().put("name", prevPair.word() + " of " + entName);
						
						pairs.get(index).set_pos(nextPair.pos());
						prevPair.set_pos(nextPair.pos());						
						
						
					} //special case: "of form"
					
					//if verb_of: "consists of" -> verb 
					else if(prevPair.pos().matches("verb")){
						String prevWord = prevPair.word();
						prevPair.set_word(prevWord + " of");
						pairs.remove(index);
					}					
					else {
						// set anchor to its normal part of speech word, like
						// "of" to pre
						
						pairs.get(index).set_pos(posMap.get(anchor));
					}

				} // if the previous token is not an ent
				else {
					// set anchor to its normal part of speech word, like "of"
					// to pre
					pairs.get(index).set_pos(posMap.get(anchor));
				}

				break;
			}

		}

		// arraylist of structs
		ArrayList<Struct> structList = new ArrayList<Struct>();

		ListIterator<Pair> pairsIter = pairs.listIterator();

		String prevPos = "-1";
		// use anchors (of, with) to gather terms together into entities
		while (pairsIter.hasNext()) {
			Pair curPair = pairsIter.next();

			if (curPair.pos() == null)
				continue;

			if (curPair.pos().matches("^\\d+$")) {

				if (curPair.pos().equals(prevPos)) {
					continue;
				}

				StructH<HashMap<String, String>> curStruct = mathEntList.get(Integer.valueOf(curPair.pos()));

				if (curStruct != null) {
					structList.add(curStruct);
				}

				prevPos = curPair.pos();

			} else {
				// current word hasn't classified into an ent

				int structListSize = structList.size();

				// combine adverbs into verbs/adjectives, look 2 phrases before
				if (curPair.pos().equals("adverb")) {

					if (structListSize > 1 && structList.get(structListSize - 2).type().matches("verb")) {
						StructA<?, ?> verbStruct = (StructA<?, ?>) structList.get(structListSize - 2);
						// verbStruct should not have prev2, also prev2 type
						// should be String
						verbStruct.set_prev2(curPair.word());
						continue;
					} else if (structListSize > 0 && structList.get(structListSize - 1).type().equals("verb")) {
						StructA<?, ?> verbStruct = (StructA<?, ?>) structList.get(structListSize - 1);
						verbStruct.set_prev2(curPair.word());
						continue;
					}
				}

				String curWord = curPair.word();

				// is leaf if prev2 is empty string ""
				StructA<String, String> newStruct = new StructA<String, String>(curWord, "", curPair.pos());

				if (curPair.pos().equals("adj")) {
					if (structListSize > 0 && structList.get(structListSize - 1).type().equals("adverb")) {
						Struct adverbStruct = structList.get(structListSize - 1);
						newStruct.set_prev2(adverbStruct);
						// remove the adverb Struct
						structList.remove(structListSize - 1);
					}
				}

				structList.add(newStruct);

			}

			// use last noun as entity, if bunch of different nouns in sequence
			// unless compound noun

			// if property found, combine to make entity property
			// ...try multiple entities instead of first one found
			// add to list of properties
			// if adjective, group with nearest entity
			// after going through entities in sentence first
			// also templating, "is" is a big hint word
			// add as property

		}

		return structList;
	}

	/*
	 * Takes in LinkedHashMap of entities/ppt, and connectives parse using
	 * structMap, and obtain sentence structures Chart parser.
	 */
	public static void parse(ArrayList<Struct> inputList) {
		int len = inputList.size();

		// first Struct
		Struct firstEnt = null;
		boolean foundFirstEnt = false;
		// track the most recent entity for use for pronouns
		Struct recentEnt = null;
		// index for recentEnt, ensure we don't count later structs
		// for pronouns
		int recentEntIndex = -1;

		ArrayList<ArrayList<Struct>> mx = new ArrayList<ArrayList<Struct>>(len);

		for (int l = 0; l < len; l++) {
			mx.add(new ArrayList<Struct>(len));
			for (int i = 0; i < len; i++) {
				mx.get(l).add(null);
			}
		}

		boolean skipCol = false;
		outerloop: for (int j = 0; j < len; j++) {

			skipCol = false;
			// fill in diagonal elements
			mx.get(j).set(j, inputList.get(j));

			for (int i = j - 1; i >= 0; i--) {
				for (int k = j - 1; k >= i; k--) {
					// pairs (i,k), and (k+1,j)

					Struct struct1 = mx.get(i).get(k);
					Struct struct2 = mx.get(k + 1).get(j);

					if (struct1 == null || struct2 == null) {
						continue;
					}

					// combine/reduce types, like or_ppt, for_ent, in_ent
					String type1 = struct1.type();
					String type2 = struct2.type();

					// for types such as conj_verbphrase
					String[] split1 = type1.split("_");

					if (split1.length > 1) {
						type1 = split1[1];
					}

					String[] split2 = type2.split("_");

					if (split2.length > 1) {
						type2 = split2[1];
					}
					
					//if recentEntIndex < j, it was deliberately skipped 
					//in a previous pair when it was the 2nd struct 
					if (type1.equals("ent") && (!(recentEntIndex < j) || !foundFirstEnt) ) {
						if (!foundFirstEnt) {
							firstEnt = struct1;
							foundFirstEnt = true;
						}
						recentEnt = struct1;
						recentEntIndex = j;
					}
					
					// if pronoun, now refers to most recent ent
					// should refer to ent that's the object of previous assertion,
					// sentence, or "complete" phrase
					// Note that different pronouns might need diferent rules 
					if (type1.equals("pro") && struct1.prev2() != null && struct1.prev2().equals("")) {
						if (recentEnt != null && recentEntIndex < j) {
							String tempName = recentEnt.struct().get("name");
							// if(recentEnt.struct().get("called") != null )
							// tempName = recentEnt.struct().get("called");
							String name = tempName != null ? tempName : "";
							struct1.set_prev2(name);
						}
					}

					
					if (type2.equals("ent") && !(type1.matches("verb|pre"))) {
						if (!foundFirstEnt) {
							firstEnt = struct1;
							foundFirstEnt = true;
						}
						recentEnt = struct2;
						recentEntIndex = j;
					}

					// look up combined in struct table, like or_ent
					// get value as name for new hash table, table with prev field
					// new type? entity, with extra ppt
					// name: or. combined ex: or_adj (returns ent), or_ent (ent)
					String combined = type1 + "_" + type2;
					
					// handle pattern ent_of_symb
					if (combined.matches("ent_pre") && struct2.prev1() != null
							&& struct2.prev1().toString().matches("of") && j + 1 < len
							&& inputList.get(j + 1).type().equals("symb")) {
						// create new child
						struct1.add_child(inputList.get(j + 1), "of");

						mx.get(i).set(j + 1, struct1);
						skipCol = true;
					}
					else if(combined.equals("pro_verb")){
						if(struct1.prev1().equals("we") && struct2.prev1().equals("say")){								
							struct1.set_type(FLUFF);
							mx.get(i).set(j, struct1);
						}
					}
					
					// handle "is called" -- "verb_parti", also "is defined"
					// for definitions
					else if (combined.matches("verb_parti") && struct1.prev1().toString().matches("is|are|be")
							&& struct2.prev1().toString().matches("called|defined|said|denoted")) {
						String called = "";
						int l = j + 1;
						// whether definition has started, ie "is called
						// subgroup of G"
						boolean defStarted = false;
						while (l < len) {

							Struct nextStruct = inputList.get(l);
							if (!nextStruct.type().matches("pre|prep|be|verb")) {
								defStarted = true;

								if (nextStruct instanceof StructA) {
									called += nextStruct.prev1();
								} else {
									called += nextStruct.struct().get("name");
								}

								if (l != len - 1)
									called += " ";

							}
							// reached end of newly defined word, now more usual
							// sentence
							// ie move from "subgroup" to "of G"
							else if (defStarted) {
								// remove last added space
								called = called.trim();
								break;
							}
							l++;
						}

						// ******* be careful using first ent
						//record the symbol/given name associated to an ent
						if (firstEnt != null) {
							StructA<Struct, String> parentStruct = new StructA<Struct, String>(firstEnt, called, "def");

							mx.get(0).set(len - 1, parentStruct);

							// add to mathObj map
							int q = 0;
							String[] calledArray = called.split(" ");
							String curWord = "";

							while (q < calledArray.length - 1) {
								curWord += calledArray[q];
								// if(q != calledArray.length - 1)
								curWord += " ";

								mathObjMap.put(curWord, "COMP");
								q++;
							}
							curWord += calledArray[calledArray.length - 1];
							mathObjMap.put(curWord, "mathObj");
							
							// recentEnt is defined to be "called"
							namesMap.put(called, recentEnt);
							break outerloop;
						}
					}

					// search for tokens larger than immediate ones
					// in case A_or_A or A_and_A set the mx element right below
					// to null
					// to set precedence, so A won't be grouped to others later
					if (i > 0 && i + 1 < len
							&& (type1.matches("or|and") && type2.equals(mx.get(i - 1).get(i - 1).type()))) {
						String newType = type1.matches("or") ? "disj" : "conj";
						// type is expression, eg "a and b"
						// new type: conj_verbphrase
						StructA<Struct, Struct> parentStruct = new StructA<Struct, Struct>(mx.get(i - 1).get(i - 1),
								struct2, newType + "_" + type2);

						// set parent struct in row above
						mx.get(i - 1).set(j, parentStruct);
						// set the next token to "", so not classified again
						// with others
						// mx.get(i+1).set(j, null);
						break;
					} else if ((type1.matches("or|and"))) {
						int l = 2;
						boolean stopLoop = false;
						while (i - l > -1 && i + 1 < len) {
							if (mx.get(i - l).get(i - 1) != null && type2.equals(mx.get(i - l).get(i - 1).type())) {
								String newType = type1.matches("or") ? "disj" : "conj";
								// type is expression, eg "a and b"
								StructA<Struct, Struct> parentStruct = new StructA<Struct, Struct>(
										mx.get(i - l).get(i - 1), struct2, newType + "_" + type2);

								mx.get(i - l).set(j, parentStruct);
								// mx.get(i+1).set(j, null);
								stopLoop = true;
								break;
							}

							if (stopLoop)
								break;
							l++;
						}
					}					

					// reduce
					if (structMap.containsKey(combined)) {
						String newType = structMap.get(combined);

						// newChild means to fuse second entity into first one

						if (newType.equals("newchild")) {
							// struct1 should be of type StructH to receive a
							// child
							// assert ensures rule correctness
							assert struct1 instanceof StructH;

							// get a (semi)deep copy of this StructH, since
							// later-added children may not
							// get used eventually, ie hard to remove children
							// added during mx building
							// that are not picked up by the eventual parse
							Struct newStruct = struct1.copy();
							
							// update firstEnt so to have the right children
							if (firstEnt == struct1) {
								firstEnt = newStruct;
							}
							
							// add to child relation, usually a preposition, eg
							// "from", "over"
							// could also be verb, "consist", "lies"
							String childRelation = mx.get(k + 1).get(k + 1).prev1().toString();
							if (struct1 instanceof StructH) {
								// why does this cast not trigger unchecked warning		
								// cause wildcard!
								((StructH<?>) newStruct).add_child(struct2, childRelation);
							}							
							
							recentEnt = newStruct;
							recentEntIndex = j;

							mx.get(i).set(j, newStruct);

						} else if(newType.equals("noun")){
							if(type1.matches("adj") && type2.matches("noun")){
								//combine adj and noun
								String adj = (String)struct1.prev1();
								struct2.set_prev1(adj + " " + struct2.prev1());
								mx.get(i).set(j, struct2);
							}
						}
						
						else {
							
							// if symbol and a given name to some entity
							// use "called" to relate entities
							if (type1.equals("symb") && struct1.prev1() instanceof String) {
								String entKey = (String) struct1.prev1();
								if (namesMap.containsKey(entKey)) {
									Struct entity = namesMap.get(entKey);
									struct1.set_prev2(entity.struct().get("name"));

								}
							}

							// update struct2 with name if applicable
							// type could have been stripped down from conj_symb
							if (type2.equals("symb") && struct2.prev1() instanceof String) {
								
								String entKey = (String) struct2.prev1();
								
								if (namesMap.containsKey(entKey)) {
									Struct entity = namesMap.get(entKey);
									struct2.set_prev2(entity.struct().get("name"));

								}								

							}

							// add to namesMap if letbe defines a name for an
							// ent
							if (newType.equals("letbe") && mx.get(i + 1).get(k) != null
									&& mx.get(k + 2).get(j) != null) {
								// temporary patch Rewrite StructA to avoid cast
								// assert(struct1 instanceof StructA);
								// assert(struct2 instanceof StructA);
								// get previous nodes

								if (mx.get(i + 1).get(k).type().equals("symb")
										&& mx.get(k + 2).get(j).type().equals("ent")) {

									namesMap.put(mx.get(i + 1).get(k).prev1().toString(), mx.get(k + 2).get(j));

									mx.get(k + 2).get(j).struct().put("called",
											mx.get(i + 1).get(k).prev1().toString());
								}
							}

							// create new StructA and put in mx
							StructA<Struct, Struct> parentStruct = new StructA<Struct, Struct>(struct1, struct2,
									newType);

							mx.get(i).set(j, parentStruct);

						}
						// found a grammar rule match, move on to next mx column
						break;
					}

				}

			}
			if (skipCol)
				j++;
		}

		// string together the parsed pieces
		LinkedList<Struct> parsedStructList = new LinkedList<Struct>();
		int i = 0, j = len - 1;
		while (j > -1) {
			i = 0;
			while (mx.get(i).get(j) == null) {
				i++;
				// some diagonal elements can be set to null on purpose
				if (i >= j) {
					break;
				}
			}
			
			Struct tempStruct = mx.get(i).get(j);
			
			//if not null or fluff
			if(tempStruct != null && !tempStruct.type().equals(FLUFF)){
				parsedStructList.add(0, tempStruct);
			}
			// a singleton on the diagonal
			if (i == j) {
				j--;
			} else {
				j = i - 1;
			}
		}

		// if not full parse, try to make into full parse by fishing out the
		// essential sentence structure, and discarding the phrases still not
		// labeled after 2nd round
		parse2(parsedStructList);

		// print out the system
		int parsedStructListSize = parsedStructList.size();
		for (int k = 0; k < parsedStructListSize; k++) {
			dfs(parsedStructList.get(k));
			if (k < parsedStructListSize - 1)
				System.out.print(" -- ");
		}
		System.out.println();

		for (int k = 0; k < parsedStructListSize; k++) {
			Struct head = parsedStructList.get(k);
			
			System.out.println("WL: ");
			String parsedString = ParseToWL.parseToWL(head);
			parsedExpr.add(parsedString);
			System.out.println();
		}
		
	}

	/**
	 * write unknown words to file to classify them
	 * @throws IOException
	 */
	public static void writeUnknownWordsToFile() throws IOException{
		Files.write(unknownWordsFile, unknownWords, Charset.forName("UTF-8"));
	}
	
	/**
	 * write unknown words to file to classify them
	 * @throws IOException
	 */
	public static void writeParsedExprToFile() throws IOException{
		Files.write(parsedExprFile, parsedExpr, Charset.forName("UTF-8"));
	}
	
	/**
	 * if not full parse, try to make into full parse by fishing out the
	 * essential sentence structure, and discarding the phrases still not
	 * labeled after 2nd round
	 *
	 * @param parsedStructList
	 *            is list output by first round of parsing
	 */
	public static void parse2(List<Struct> inputList) {

	}

	public static void dfs(Struct struct) {
		// don't like instanceof here
		if (struct instanceof StructA) {

			System.out.print(struct.type());

			System.out.print("[");
			// don't know type at compile time
			if (struct.prev1() instanceof Struct) {
				dfs((Struct) struct.prev1());
			}
			
			// if(struct.prev2() != null && !struct.prev2().equals(""))
			// System.out.print(", ");
			if (((StructA<?, ?>) struct).prev2() instanceof Struct) {
				// avoid printing is[is], ie case when parent has same type as
				// child
				System.out.print(", ");
				dfs((Struct) struct.prev2());
			}

			if (struct.prev1() instanceof String) {
				System.out.print(struct.prev1());
			}
			if (struct.prev2() instanceof String) {
				if (!struct.prev2().equals(""))
					System.out.print(", ");
				System.out.print(struct.prev2());
			}

			System.out.print("]");
		} else if (struct instanceof StructH) {

			System.out.print(struct.toString());

			ArrayList<Struct> children = struct.children();
			ArrayList<String> childRelation = struct.childRelation();

			if (children == null || children.size() == 0)
				return;

			System.out.print("[");
			for (int i = 0; i < children.size(); i++) {
				System.out.print(childRelation.get(i) + " ");
				dfs(children.get(i));
			}
			System.out.print("]");
		}
	}

	/**
	 * Preprocess. Remove fluff words. the, a, an
	 * @param str is string of all input to be processed
	 * @return array of sentence Strings
	 */
	public static String[] preprocess(String inputStr) {
		
		ArrayList<String> sentenceList = new ArrayList<String>();
		String[] wordsArray = inputStr.split(" ");
		int wordsArrayLen = wordsArray.length;
		
		//use StringBuilder!
		StringBuilder sentenceBuilder = new StringBuilder();
		//String newSentence = "";		
		String curWord;
		
		boolean inTex = false; //in latex expression?
		boolean madeReplacement = false;
		
		for (int i = 0; i < wordsArrayLen; i++) {
			
			curWord = wordsArray[i];
			
			if(!inTex && curWord.matches("\\$.*") && !curWord.matches("\\$[^$]+\\$.*")){				
				inTex = true;
			}else if(inTex && curWord.contains("$")){
			//}else if(curWord.matches("[^$]*\\$|\\$[^$]+\\$.*") ){
				inTex = false;
			}

			//fluff phrases all start in posMap			
			if(posMap.containsKey(curWord)){
				String pos = posMap.get(curWord);
				String[] posAr = pos.split("_");
				String tempWord = curWord;
				
				int j = i;
					//potentially a fluff phrase
				if(posAr[posAr.length-1].equals("comp") && j < wordsArrayLen-1){
					//keep reading in string characters, until there is no match				
					tempWord += " " + wordsArray[++j];
						
					while(posMap.containsKey(tempWord) && j < wordsArrayLen-1){
						tempWord += " " + wordsArray[++j];
					}
						
					String replacement = fluffMap.get(tempWord);
					if(replacement != null){
						sentenceBuilder.append(replacement);
						madeReplacement = true;
						i = j;
					}
						//curWord += wordsArray[++i];
				}				
			}
			
			//if composite fluff word
			if (!madeReplacement && !fluffMap.containsKey(curWord)){
			
				sentenceBuilder.append(" " + curWord);
			}
			
			if(curWord.matches("[^.,!]*[.|,|!]{1}") || i == wordsArrayLen-1){
				
				if(!inTex){
					sentenceList.add(sentenceBuilder.toString());
					sentenceBuilder.setLength(0);
				}
			}			
			
		}
		return sentenceList.toArray(new String[0]);
	}

}
