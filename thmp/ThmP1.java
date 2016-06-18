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
import java.util.Iterator;
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
	private static HashMap<String, Rule> structMap;
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
	// split a sentence into parts, separated by commas, semicolons etc
	// private String[] subSentences;

	// list of parts of speech, ent, verb etc
	private static ArrayList<String> posList;

	// fluff type, skip when adding to parsed ArrayList
	private static String FLUFF = "Fluff";

	// private static final File unknownWordsFile;
	private static final Path unknownWordsFile = Paths.get("src/thmp/data/unknownWords.txt");
	private static final Path parsedExprFile = Paths.get("src/thmp/data/parsedExpr.txt");

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
	 * Tokenizes by splitting into comma-separated strings
	 * 
	 * @param str
	 *            A full sentence.
	 * @return
	 */
	/*
	 * public static void process(String sentence) throws IOException{ //can't
	 * just split! Might be in latex expression String[] subSentences =
	 * sentence.split(",|;|:"); int subSentLen = subSentences.length; for(int i
	 * = 0; i < subSentLen; i++){ parse(tokenize(subSentences[i])); }
	 * System.out.println(); }
	 */

	/**
	 * 
	 * @param str
	 *            string to be tokenized
	 * @return
	 * @throws IOException
	 */
	public static ArrayList<Struct> tokenize(String sentence) throws IOException {

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
		// unfortunate naming
		String[] str = sentence.split(" ");

		// int pairIndex = 0;
		for (int i = 0; i < str.length; i++) {

			String curWord = str[i];

			if (curWord.matches("^\\s*,*$"))
				continue;

			// strip away special chars '(', ')', etc ///should not
			// remove........
			// curWord = curWord.replaceAll("\\(|\\)", "");
			// remove this and ensure curWord is used subsequently
			// instead of str[i]
			str[i] = curWord;

			String type = "mathObj";
			int wordlen = str[i].length();

			// detect latex expressions, mark them as "mathObj" for now
			if (curWord.charAt(0) == '$') {
				String latexExpr = curWord;
				int stringLength = str.length;

				if (i < stringLength - 1 && !curWord.matches("\\$[^$]+\\$[^\\s]*")
						&& (curWord.charAt(wordlen - 1) != '$' || wordlen == 2 || wordlen == 1)) {
					i++;
					curWord = str[i];
					if (i < stringLength - 1 && curWord.equals("")) {
						curWord = str[++i];
					}

					else if (curWord.matches("[^$]*\\$.*")) {
						latexExpr += " " + curWord;
					} else {
						while (i < stringLength && curWord.length() > 0
								&& curWord.charAt(curWord.length() - 1) != '$') {
							latexExpr += " " + curWord;
							i++;

							if (i == stringLength)
								break;

							curWord = i < stringLength - 1 && str[i].equals("") ? str[++i] : str[i];

						}
					}

					if (i < stringLength) {
						int tempWordlen = str[i].length();

						if (tempWordlen > 0 && str[i].charAt(tempWordlen - 1) == '$')
							latexExpr += " " + str[i];
					}

					if (latexExpr.matches("[^=]+=.+|[^\\\\cong]+\\\\cong.+")) {
						type = "assert";
					}
				} else if (curWord.matches("\\$[^$]\\$")) {
					type = "symb";
				}
				// go with the pos of the last word
				else if (curWord.matches("\\$[^$]+\\$[^-\\s]*-[^\\s]*")) {
					String[] curWordAr = curWord.split("-");
					String tempWord = curWordAr[curWordAr.length - 1];
					String tempPos = posMap.get(tempWord);
					if (tempPos != null) {
						type = tempPos;
					}
				}

				Pair pair = new Pair(latexExpr, type);
				pairs.add(pair);
				if (type.equals("mathObj"))
					mathIndexList.add(pairs.size() - 1);

				continue;
			}

			// primitive way to handle plural forms: if ends in "s"
			String singular = "";
			String singular2 = ""; // ending in "ies"
			String singular3 = ""; // ending in "es"
			if (wordlen > 0 && curWord.charAt(wordlen - 1) == 's') {
				singular = curWord.substring(0, wordlen - 1);
			}

			if (wordlen > 3 && curWord.substring(wordlen - 3, wordlen).equals("ies")) {
				singular2 = curWord.substring(0, wordlen - 3) + 'y';
			}

			if (wordlen > 2 && curWord.substring(wordlen - 2, wordlen).equals("es")) {
				singular3 = curWord.substring(0, wordlen - 2);
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

				while (tempPos.length() > 4
						&& tempPos.substring(tempPos.length() - 4, tempPos.length()).matches("COMP|comp")
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
				// And combine adj_adj to adj, eg right exact
				if (pairs.size() > 0 && posMap.get(curWord).equals("adj")) {
					if (pairs.get(pairsSize - 1).pos().matches("adverb|adj")) {
						curWord = pairs.get(pairsSize - 1).word() + " " + curWord;
						// remove previous Pair
						pairs.remove(pairsSize - 1);
						pair = new Pair(curWord, "adj");
						addIndex = false;
					}

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
			else if (wordlen > 0 && curWord.charAt(wordlen - 1) == 's'
					&& posMap.containsKey(str[i].substring(0, wordlen - 1))
					&& posMap.get(str[i].substring(0, wordlen - 1)).equals("verb")) {

				Pair pair = new Pair(str[i], "verb");
				pairs.add(pair);
			} else if (wordlen > 1 && curWord.charAt(wordlen - 1) == 's' && str[i].charAt(str[i].length() - 2) == 'e'
					&& posMap.containsKey(str[i].substring(0, wordlen - 2))
					&& posMap.get(str[i].substring(0, wordlen - 2)).equals("verb")) {
				Pair pair = new Pair(str[i], "verb");
				pairs.add(pair);

			}
			// adverbs that end with -ly that haven't been screened off before
			else if (wordlen > 1 && curWord.substring(wordlen - 2, wordlen).equals("ly")) {
				Pair pair = new Pair(str[i], "adverb");
				pairs.add(pair);
			}
			// participles and gerunds. Need special list for words such as
			// "given"
			else if (wordlen > 1 && curWord.substring(wordlen - 2, wordlen).equals("ed")
					&& (posMap.containsKey(str[i].substring(0, wordlen - 2))
							&& posMap.get(str[i].substring(0, wordlen - 2)).equals("verb")
							|| posMap.containsKey(str[i].substring(0, wordlen - 1))
									&& posMap.get(str[i].substring(0, wordlen - 1)).equals("verb"))) {

				// if next word is "by", then
				String curPos = "parti";
				int pairsSize = pairs.size();
				// if next word is "by"
				if (str.length > i + 1 && str[i + 1].equals("by")) {
					curPos = "partiby";
					curWord = curWord + " by";
					// if previous word is a verb, combine to form verb
					if (pairsSize > 0 && pairs.get(pairsSize - 1).pos().matches("verb|vbs")) {
						curWord = pairs.get(pairsSize - 1).pos() + " " + curWord;
						curPos = "verb";
						pairs.remove(pairsSize - 1);
					}
					i++;
				}
				// previous word is "is, are", then group with previous word to
				// verb
				// e.g. "is called"
				else if (pairsSize > 0 && pairs.get(pairsSize - 1).word().matches("is|are")) {

					curWord = pairs.get(pairsSize - 1).word() + " " + curWord;
					pairs.remove(pairsSize - 1);

					curPos = "verb";
				}
				// if previous word is adj, "finitely presented"
				else if (pairsSize > 0 && pairs.get(pairsSize - 1).pos().equals("adverb")) {

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
			} else if (wordlen > 2 && curWord.substring(wordlen - 3, wordlen).equals("ing")
					&& (posMap.containsKey(curWord.substring(0, wordlen - 3))
							&& posMap.get(curWord.substring(0, wordlen - 3)).matches("verb|vbs")
							// verbs ending in "e"
							|| (posMap.containsKey(curWord.substring(0, wordlen - 3) + 'e')
									&& posMap.get(curWord.substring(0, wordlen - 3) + 'e').matches("verb|vbs")))) {
				String curType = "gerund";
				if (i < str.length - 1 && posMap.containsKey(str[i + 1]) && posMap.get(str[i + 1]).equals("pre")) {
					// eg "consisting of" functions as pre,
					curWord = curWord + " " + str[++i];
					curType = "pre";
				}
				Pair pair = new Pair(curWord, curType);
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

				// write unknown words to file
				unknownWords.add(curWord);

			} else { // curWord doesn't count

				continue;
			}

			// if (addIndex) {
			// pairIndex++;
			// }
			addIndex = true;

			int pairsSize = pairs.size();

			if (pairsSize > 0) {
				Pair pair = pairs.get(pairsSize - 1);

				// combine "no" and "not" with verbs
				if (pair.pos().matches("verb|vbs")) {
					if (pairs.size() > 1 && (pairs.get(pairsSize - 2).word().matches("not|no")
							|| pairs.get(pairsSize - 2).pos().matches("not"))) {
						String newWord = pair.word().matches("is|are") ? "not" : "not " + pair.word();
						pair.set_word(newWord);
						pairs.remove(pairsSize - 2);
					}

					if (i + 1 < str.length && str[i + 1].matches("not|no")) {
						String newWord = pair.word().matches("is|are") ? "not" : "not " + pair.word();
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
		String prevType = "", nextType = "", tempCurType = "", tempPrevType = "", tempNextType = "", bestCurType = "";

		int posListSz = posList.size();

		for (int index = 0; index < len; index++) {
			curpair = pairs.get(index);
			if (curpair.pos().equals("")) {

				prevType = index > 0 ? pairs.get(index - 1).pos() : "";
				nextType = index < len - 1 ? pairs.get(index + 1).pos() : "";

				prevType = prevType.equals("anchor") ? "pre" : prevType;
				nextType = nextType.equals("anchor") ? "pre" : nextType;

				// iterate through list of types, ent, verb etc
				for (int k = 0; k < posListSz; k++) {
					tempCurType = posList.get(k);

					// FIRST/LAST indicate positions
					tempPrevType = index > 0 ? prevType + "_" + tempCurType : "FIRST";
					tempNextType = index < len - 1 ? tempCurType + "_" + nextType : "LAST";

					if (probMap.get(tempPrevType) != null && probMap.get(tempNextType) != null) {
						double score = probMap.get(tempPrevType) * probMap.get(tempNextType);
						if (score > bestCurProb) {
							bestCurProb = score;
							bestCurType = tempCurType;
						}
					}
				}
				pairs.get(index).set_pos(bestCurType);

				if (bestCurType.equals("mathObj"))
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

			// if next pair is also ent, and is latex expression
			if (j < mathIndexList.size() - 1 && mathIndexList.get(j + 1) == index + 1) {
				Pair nextPair = pairs.get(index + 1);
				String name = nextPair.word();
				if (name.contains("$")) {
					tempMap.put("tex", name);
					nextPair.set_pos(String.valueOf(j));
					mathIndexList.remove(j + 1);
				}
			}

			// look right one place in pairs, if symbol found, add it to
			// namesMap
			// if it's the given name for an ent.
			// Combine gerund with ent
			int pairsSize = pairs.size();
			if (index + 1 < pairsSize && pairs.get(index + 1).pos().equals("symb")) {
				pairs.get(index + 1).set_pos(String.valueOf(j));
				String givenName = pairs.get(index + 1).word();
				tempMap.put("called", givenName);
				// do not overwrite previously named symbol
				// if(!namesMap.containsKey(givenName))
				// namesMap.put(givenName, tempStructH);

			} /*
				 * else if ((index + 2 < pairsSize && pairs.get(index +
				 * 2).pos().equals("symb"))) { pairs.get(index +
				 * 2).set_pos(String.valueOf(j)); String givenName =
				 * pairs.get(index + 2).word(); tempMap.put("called",
				 * givenName); namesMap.put(givenName, tempStructH); }
				 */
			// look left one place
			if (index > 0 && pairs.get(index - 1).pos().equals("symb")) {
				pairs.get(index - 1).set_pos(String.valueOf(j));
				String givenName = pairs.get(index - 1).word();
				// combine the symbol with ent's name together
				tempMap.put("name", givenName + " " + tempMap.get("name"));

			}
			// combine nouns with ent's right after, ie noun_ent
			else if (index > 0 && pairs.get(index - 1).pos().matches("noun")) {
				pairs.get(index - 1).set_pos(String.valueOf(j));
				String prevNoun = pairs.get(index - 1).word();
				tempMap.put("name", prevNoun + " " + tempMap.get("name"));
			}
			// and combine ent_noun together
			else if (index + 1 < pairsSize && pairs.get(index + 1).pos().matches("noun")) {
				pairs.get(index + 1).set_pos(String.valueOf(j));
				String prevNoun = pairs.get(index + 1).word();
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
			// hacky way: check if index-k-2 is a verb, only combine adj's if
			// not
			// eg " "
			if (index - k - 2 > -1 && pairs.get(index - k).pos().matches("or|and")
					&& pairs.get(index - k - 1).pos().equals("adj")) {
				String tempPos = posMap.get(pairs.get(index - k - 2).word());
				if (tempPos != null && !tempPos.matches("verb|vbs|verb_comp|vbs_comp")) {
					// set pos() of or/and to the right index
					pairs.get(index - k).set_pos(String.valueOf(j));
					String curWord = pairs.get(index - k - 1).word();
					tempMap.put(curWord, "ppt");
					pairs.get(index - k - 1).set_pos(String.valueOf(j));

				}
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
				if (index > 0 && index + 1 < pairs.size()) {

					Pair nextPair = pairs.get(index + 1);
					Pair prevPair = pairs.get(index - 1);
					// should handle later with grammar rules in mx!
					// ent of ent
					if (prevPair.pos().matches("\\d+$") && nextPair.pos().matches("\\d+$")) {
						int mathObjIndex = Integer.valueOf(prevPair.pos());
						StructH<HashMap<String, String>> tempStruct = mathEntList.get(mathObjIndex);

						pairs.get(index).set_pos(nextPair.pos());
						Struct childStruct = mathEntList.get(Integer.valueOf(nextPair.pos()));
						tempStruct.add_child(childStruct, "of");
						// set to null instead of removing, to keep indices
						// right. If nextPair.pos != prevPair.pos().
						if (nextPair.pos() != prevPair.pos())
							mathEntList.set(Integer.valueOf(nextPair.pos()), null);

					} // "noun of ent".
					else if (prevPair.pos().matches("noun") && nextPair.pos().matches("\\d+$")) {
						int mathObjIndex = Integer.valueOf(nextPair.pos());
						// Combine the something into the ent
						StructH<HashMap<String, String>> tempStruct = mathEntList.get(mathObjIndex);

						String entName = tempStruct.struct().get("name");
						tempStruct.struct().put("name", prevPair.word() + " of " + entName);

						pairs.get(index).set_pos(nextPair.pos());
						prevPair.set_pos(nextPair.pos());

					} // special case: "of form"

					// if verb_of: "consists of" -> verb
					else if (prevPair.pos().matches("verb|vbs")) {
						String prevWord = prevPair.word();
						prevPair.set_word(prevWord + " of");
						pairs.remove(index);
					} else {
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

				// combine adverbs into verbs/adjectives, look 2 words before
				if (curPair.pos().equals("adverb")) {

					if (structListSize > 1 && structList.get(structListSize - 2).type().matches("verb|vbs")) {
						StructA<?, ?> verbStruct = (StructA<?, ?>) structList.get(structListSize - 2);
						// verbStruct should not have prev2, also prev2 type
						// should be String
						verbStruct.set_prev2(curPair.word());
						continue;
					} else if (structListSize > 0 && structList.get(structListSize - 1).type().matches("verb|vbs")) {
						StructA<?, ?> verbStruct = (StructA<?, ?>) structList.get(structListSize - 1);
						verbStruct.set_prev2(curPair.word());
						continue;
					}
				}

				String curWord = curPair.word();

				// is leaf of prev2 is empty string ""
				StructA<String, String> newStruct = new StructA<String, String>(curWord, "", curPair.pos());

				if (curPair.pos().equals("adj")) {
					if (structListSize > 0 && structList.get(structListSize - 1).type().equals("adverb")) {
						Struct adverbStruct = structList.get(structListSize - 1);
						newStruct.set_prev2(adverbStruct);
						// remove the adverb Struct
						structList.remove(structListSize - 1);
					}
				}

				/*
				 * else if (curPair.pos().equals("pre"))
				 * {////////////////////////////// if (structListSize > 0 &&
				 * structList.get(structListSize - 1).type().equals("gerund")) {
				 * Struct gerundStruct = structList.get(structListSize - 1);
				 * 
				 * newStruct.set_prev2(adverbStruct); //////////// // remove the
				 * adverb Struct structList.remove(structListSize - 1); } }
				 */

				// combine det into nouns and verbs, change
				else if (curPair.pos().equals("noun") && structListSize > 0
						&& structList.get(structListSize - 1).type().equals("det")) {
					String det = (String) structList.get(structListSize - 1).prev1();
					newStruct.set_prev2(det);
					structList.remove(structListSize - 1);
				}

				structList.add(newStruct);

			}

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

		// A matrix of List's. Dimenions of first two Lists are same: square
		// matrix
		ArrayList<ArrayList<StructList>> mx = new ArrayList<ArrayList<StructList>>(len);

		for (int l = 0; l < len; l++) {
			ArrayList<StructList> tempList = new ArrayList<StructList>();

			for (int i = 0; i < len; i++) {
				// initialize Lists now so no need to repeatedly check if null
				// later
				// but does use more space! Need to revisist.
				tempList.add(new StructList());
			}

			mx.add(tempList);
			/*
			 * mx.add(new ArrayList<Struct>(len));
			 * 
			 * for (int i = 0; i < len; i++) { // add len number of null's
			 * mx.get(l).get(i) .add(null); }
			 */
		}

		boolean skipCol = false;
		// which row to start at for the next column
		int startRow = -1;
		outerloop: for (int j = 0; j < len; j++) {

			// fill in diagonal elements
			// ArrayList<Struct> diagonalStruct = new ArrayList<Struct>();

			// mx.get(j).set(j, diagonalStruct);
			Struct diagonalStruct = inputList.get(j);
			diagonalStruct.set_structList(mx.get(j).get(j));

			mx.get(j).get(j).add(diagonalStruct);

			// mx.get(j).set(j, inputList.get(j));

			// startRow should actually *always* be < j
			int i = j - 1;
			if (startRow != -1 && startRow < j) {
				if (startRow == 0) {
					startRow = -1;
					continue;
				}
				i = startRow - 1;
				startRow = -1;
			}
			for (; i >= 0; i--) {

				for (int k = j - 1; k >= i; k--) {
					// pairs are (i,k), and (k+1,j)

					StructList structList1 = mx.get(i).get(k);
					StructList structList2 = mx.get(k + 1).get(j);

					// Struct struct1 = mx.get(i).get(k);
					// Struct struct2 = mx.get(k + 1).get(j);

					if (structList1 == null || structList2 == null || structList1.size() == 0
							|| structList2.size() == 0) {
						continue;
					}

					// need to refactor to make methods more modular!

					Iterator<Struct> structList1Iter = structList1.structList().iterator();
					Iterator<Struct> structList2Iter = structList2.structList().iterator();

					while (structList1Iter.hasNext()) {

						Struct struct1 = structList1Iter.next();

						while (structList2Iter.hasNext()) {
							Struct struct2 = structList2Iter.next();

							// combine/reduce types, like or_ppt, for_ent,
							// in_ent
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

							// if recentEntIndex < j, it was deliberately
							// skipped
							// in a previous pair when it was the 2nd struct
							if (type1.equals("ent") && (!(recentEntIndex < j) || !foundFirstEnt)) {
								if (!foundFirstEnt) {
									firstEnt = struct1;
									foundFirstEnt = true;
								}
								recentEnt = struct1;
								recentEntIndex = j;
							}

							// if pronoun, now refers to most recent ent
							// should refer to ent that's the object of previous
							// assertion,
							// sentence, or "complete" phrase
							// Note that different pronouns might need diferent
							// rules
							if (type1.equals("pro") && struct1.prev1() instanceof String
									&& ((String) struct1.prev1()).matches("it|they") && struct1.prev2() != null
									&& struct1.prev2().equals("")) {
								if (recentEnt != null && recentEntIndex < j) {
									String tempName = recentEnt.struct().get("name");
									// if(recentEnt.struct().get("called") !=
									// null )
									// tempName =
									// recentEnt.struct().get("called");
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
							// get value as name for new hash table, table with
							// prev field
							// new type? entity, with extra ppt
							// name: or. combined ex: or_adj (returns ent),
							// or_ent (ent)
							String combined = type1 + "_" + type2;

							// handle pattern ent_of_symb
							if (combined.matches("ent_pre") && struct2.prev1() != null
									&& struct2.prev1().toString().matches("of") && j + 1 < len
									&& inputList.get(j + 1).type().equals("symb")) {
								// create new child
								struct1.add_child(inputList.get(j + 1), "of");

								// mx.get(i).set(j + 1, struct1);
								mx.get(i).get(j + 1).add(struct1);
								skipCol = true;
								startRow = i;
							} else if (combined.equals("pro_verb")) {
								if (struct1.prev1().equals("we") && struct2.prev1().equals("say")) {
									struct1.set_type(FLUFF);
									// mx.get(i).set(j, struct1);
									mx.get(i).get(j).add(struct1);
								}
							}

							if (combined.equals("adj_ent")) {
								// update struct
								if (struct2 instanceof StructH) {
									// should be StructH
									Struct newStruct = struct2.copy();
									String newPpt = "";
									if (struct1.type().equals("conj_adj")) {
										if (struct1.prev1() instanceof Struct) {
											newPpt += ((Struct) struct1.prev1()).prev1();
										}
										if (struct1.prev2() instanceof Struct) {
											newPpt += ((Struct) struct1.prev2()).prev1();
										}
									} else {
										// check if String and cast instead
										newPpt += struct1.prev1();
									}
									newStruct.struct().put(newPpt, "ppt");
									// mx.get(i).set(j, newStruct);
									mx.get(i).get(j).add(newStruct);
									continue outerloop;
								}
							}

							// handle "is called" -- "verb_parti", also "is
							// defined"
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
									// reached end of newly defined word, now
									// more usual sentence
									// ie move from "subgroup" to "of G"
									else if (defStarted) {
										// remove last added space
										called = called.trim();
										break;
									}
									l++;
								}

								// ******* be careful using first ent
								// record the symbol/given name associated to an
								// ent
								if (firstEnt != null) {
									StructA<Struct, String> parentStruct = new StructA<Struct, String>(firstEnt, called,
											"def", mx.get(0).get(len - 1));

									// mx.get(0).set(len - 1, parentStruct);
									mx.get(0).get(len - 1).add(parentStruct);

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
									continue outerloop;
								}
							}

							// search for tokens larger than immediate ones
							// in case A_or_A or A_and_A set the mx element
							// right below
							// to null
							// to set precedence, so A won't be grouped to
							// others later
							// and if the next word is a verb, it is not
							// singular
							// ie F and G is isomorphic

							//////// %%%%%%%%%%% NPE for "subset F of G and
							//////// subset G of H"
							// because G gets null'ed out after "of". Need
							//////// better strategy!
							// should probably be j? j is column #.

							// iterate through the List at position (i-1, i-1)
							if (i > 0 && i + 1 < len) {

								/*
								 * List<Struct> iMinusOneStructList = mx.get(i -
								 * 1).get(i - 1).structList();
								 * 
								 * if (type1.matches("or|and") &&
								 * iMinusOneStructList.size() > 0) {
								 * 
								 * // Iterator<Struct> iMinusOnestructIter = //
								 * mx.get(i-1).get(i-1).iterator();
								 * 
								 * for (Struct iMinusOneStruct :
								 * iMinusOneStructList) {
								 * 
								 * if(type1.matches("and"))
								 * System.out.print("debug"); if
								 * (type2.equals(iMinusOneStruct.type())) {
								 * 
								 * // In case of conj, only proceed if // next
								 * // word is not a singular verb. // Single
								 * case with complicated // logic, // so it's
								 * easier more readable to // write // if(this
								 * case){ // }then{do_something} // over if(not
								 * this // case){do_something} Struct nextStruct
								 * = j + 1 < len ? inputList.get(j + 1) : null;
								 * if (nextStruct != null && type1.equals("and")
								 * && nextStruct.prev1() instanceof String &&
								 * isSingularVerb((String) nextStruct.prev1()))
								 * {
								 * 
								 * // skip rest in this case } else {
								 * 
								 * String newType = type1.equals("or") ? "disj"
								 * : "conj"; // type is expression, eg "a and //
								 * b" // new type: conj_verbphrase
								 * 
								 * StructA<Struct, Struct> parentStruct = new
								 * StructA<Struct, Struct>( iMinusOneStruct,
								 * struct2, newType + "_" + type2,
								 * mx.get(i-1).get(j));
								 * 
								 * // set parent struct in row // above //
								 * mx.get(i - 1).set(j, // parentStruct);
								 * mx.get(i - 1).get(j).add(parentStruct); //
								 * set the next token to "", so // not //
								 * classified again // with others //
								 * mx.get(i+1).set(j, null); // already
								 * classified, no need // to // keep reduce with
								 * mx // manipulations break; } } } // this case
								 * can be combined with if // statement //
								 * above, use single while loop } else
								 */
								if (type1.matches("or|and")) {
									int l = 1;
									boolean stopLoop = false;

									searchConjLoop: while (i - l > -1 && i + 1 < len) {

										List<Struct> structArrayList = mx.get(i - l).get(i - 1).structList();

										int structArrayListSz = structArrayList.size();
										if (structArrayListSz == 0)
											continue;

										// iterate over Structs at (i-l, i-1)
										for (int p = 0; p < structArrayListSz; p++) {
											Struct p_struct = structArrayList.get(p);
											if (type2.equals(p_struct.type())) {
												
												// In case of conj, only proceed if // next
												  // word is not a singular verb. // Single case with complicated 
												// logic, // so it's easier more readable to // write // if(this
												//case){ // }then{do_something} // over if(not
												 // this // case){do_something} 
												 Struct nextStruct = j + 1 < len ? inputList.get(j + 1) : null;
												  if (nextStruct != null && type1.equals("and")
												  && nextStruct.prev1() instanceof String &&
												  isSingularVerb((String) nextStruct.prev1())){
													 
												 }else{
												
												String newType = type1.matches("or") ? "disj" : "conj";
												// type is expression, eg "a and
												// b".
												// Come up with a scoring system
												// for and/or!
												//should work a score in to conj/disj! The longer the conj/disj the higher
												StructA<Struct, Struct> parentStruct = new StructA<Struct, Struct>(
														p_struct, struct2, newType + "_" + type2, mx.get(i - l).get(j));

												//parentStruct.set_maxDownPathScore();
												
												mx.get(i - l).get(j).add(parentStruct);
												// mx.get(i - l).set(j,
												// parentStruct);
												// mx.get(i+1).set(j, null);
												stopLoop = true;
												break searchConjLoop;
												 }
											}
										}
										// if (stopLoop)
										// break;
										l++;
									}

								}
							}

							// potentially change assert to latex expr
							if (type2.equals("assert") && struct2.prev1() instanceof String
									&& ((String) struct2.prev1()).charAt(0) == '$'
									&& !structMap.containsKey(combined)) {
								struct2.set_type("expr");
								combined = type1 + "_" + "expr";
							}
							// update namesMap
							if (type1.equals("ent") && struct1 instanceof StructH) {
								String called = struct1.struct().get("called");
								if (called != null)
									namesMap.put(called, struct1);
							}

							// reduce if structMap contains combined
							if (structMap.containsKey(combined)) {
								reduce(mx, combined, struct1, struct2, firstEnt, recentEnt, recentEntIndex, i, j, k,
										type1, type2);
							}

						} // loop listIter1 ends here
					} // loop listIter2 ends here

					// loop for (int k = j - 1; k >= i; k--) { ends here
				}

			}
			// if (skipCol)
			// j++;
		}

		// string together the parsed pieces
		// ArrayList (better at get/set) or LinkedList (better at add/remove)?
		// iterating over all headStruct
		StructList headStructList = mx.get(0).get(len - 1);
		int headStructListSz = headStructList.size();
		System.out.println("headStructListSz " + headStructListSz);

		if (headStructListSz > 0) {
			//System.out.println("index of highest score: " + ArrayDFS(headStructList));
			for (int u = 0; u < headStructListSz; u++) {
				Struct uHeadStruct = headStructList.structList().get(u);
				
				dfs(uHeadStruct);
				System.out.println(uHeadStruct.maxDownPathScore());
				System.out.println(uHeadStruct.numUnits());
			}
		} 
		// if no full parse:
		else {
			List<StructList> parsedStructList = new ArrayList<StructList>();

			int i = 0, j = len - 1;
			while (j > -1) {
				i = 0;
				while (mx.get(i).get(j).size() == 0) {
					i++;
					// some diagonal elements can be set to null on purpose
					if (i >= j) {
						break;
					}
				}

				StructList tempStructList = mx.get(i).get(j);

				// if not null or fluff.
				// What kind of fluff can trickle down here??
				// for the check !tempStruct.type().equals(FLUFF)
				if (tempStructList.size() > 0) {
					parsedStructList.add(0, tempStructList);
				}
				// a singleton on the diagonal
				if (i == j) {
					j--;
				} else {
					j = i - 1;
				}
			}

			// if not full parse, try to make into full parse by fishing out the
			// essential sentence structure, and discarding the phrases still
			// not
			// labeled after 2nd round
			// parse2(parsedStructList);

			// print out the system
			int parsedStructListSize = parsedStructList.size();

			for (int k = 0; k < parsedStructListSize; k++) {
				//int highestScoreIndex = ArrayDFS(parsedStructList.get(k));
				int highestScoreIndex = 0;
				
				Struct kHeadStruct = parsedStructList.get(k).structList().get(highestScoreIndex);
				dfs(kHeadStruct);
				if (k < parsedStructListSize - 1)
					System.out.print(" ,  ");
			}
		}
		// print out scores
		/*
		 * StructList headStructList = mx.get(0).get(len-1); int
		 * headStructListSz = headStructList.size(); System.out.println(
		 * "headStructListSz " + headStructListSz ); for(int u = 0; u <
		 * headStructListSz; u++){
		 * System.out.println(headStructList.structList().get(u) ); }
		 */

		/*  Don't delete this part!
		 * System.out.println("\nWL: "); StructList headStructList = mx.get(len
		 * - 1).get(len - 1); //should pick out best parse before WL and just
		 * parse to WL for that particular parse!
		 * 
		 * for(int q = 0; q < headStructList.size(); q++){ for (int k = 0; k <
		 * parsedStructListSize; k++) {
		 * 
		 * Struct head = parsedStructList.get(k);
		 * 
		 * String parsedString = ParseToWL.parseToWL(head);
		 * parsedExpr.add(parsedString); System.out.print(parsedString +
		 * " \n ** "); } System.out.println("WL parse " + k + " done\n"); }
		 * System.out.println("%%%%%\n");
		 */
	}

	/**
	 * Returns true iff word is a singular verb (meaning associated to singular
	 * subj, eg "gives")
	 * 
	 * @param word
	 *            word to be checked
	 * @return
	 */
	private static boolean isSingularVerb(String word) {
		// did not find singular verbs that don't end in "s"
		int wordLen = word.length();

		if (wordLen < 2 || word.charAt(wordLen - 1) != 's')
			return false;

		// strip away 's'
		String pos = posMap.get(word.substring(0, wordLen - 2));
		if (pos != null && pos.matches("verb|verb_comp")) {
			return true;
		}
		// strip away es if applicable
		else if (wordLen > 2 && word.charAt(wordLen - 2) == 'e') {
			pos = posMap.get(word.substring(0, wordLen - 3));
			if (pos != null && pos.matches("verb|verb_comp"))
				return true;
		}
		// could be special singular form, eg "is"
		else if ((pos = posMap.get(word)) != null && pos.matches("vbs|vbs_comp")) {
			return true;
		}
		return false;
	}

	/**
	 * reduce if structMap contains combined
	 */
	public static void reduce(ArrayList<ArrayList<StructList>> mx, String combined, Struct struct1, Struct struct2,
			Struct firstEnt, Struct recentEnt, int recentEntIndex, int i, int j, int k, String type1, String type2) {
		
		Rule newRule = structMap.get(combined);
		String newType = newRule.relation();
		double newScore = newRule.prob();
		double newDownPathScore = struct1.maxDownPathScore() * struct2.maxDownPathScore() * newScore;
		
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

			List<Struct> kPlus1StructArrayList = mx.get(k + 1).get(k + 1).structList();

			// diagonal element can have only 1 Struct in its structList
			String childRelation = kPlus1StructArrayList.get(0).prev1().toString();

			// String childRelation = mx.get(k + 1).get(k +
			// 1).prev1().toString();

			if (struct1 instanceof StructH) {
				// why does this cast not trigger unchecked warning
				// Because wildcard!
				((StructH<?>) newStruct).add_child(struct2, childRelation);
			}

			recentEnt = newStruct;
			recentEntIndex = j;
						
			struct2.set_maxDownPathScore( newDownPathScore );
			
			// mx.get(i).set(j, newStruct);
			mx.get(i).get(j).add(newStruct);

		} else if (newType.equals("noun")) {
			if (type1.matches("adj") && type2.matches("noun")) {
				// combine adj and noun
				String adj = (String) struct1.prev1();
				struct2.set_prev1(adj + " " + struct2.prev1());
				struct2.set_maxDownPathScore( struct2.maxDownPathScore()*newScore );
				// mx.get(i).set(j, struct2);
				mx.get(i).get(j).add(struct2);
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

			// add to namesMap if letbe defines a name for an ent
			if (newType.equals("letbe") && mx.get(i + 1).get(k).size() > 0 && mx.get(k + 2).get(j).size() > 0) {
				// temporary patch Rewrite StructA to avoid cast
				// assert(struct1 instanceof StructA);
				// assert(struct2 instanceof StructA);
				// get previous nodes

				// now need to iterate through structList's for these two
				// Structs
				List<Struct> tempSymStructList = mx.get(i + 1).get(k).structList();
				List<Struct> tempEntStructList = mx.get(k + 2).get(j).structList();

				ploop: for (int p = 0; p < tempSymStructList.size(); p++) {
					Struct tempSymStruct = tempSymStructList.get(p);

					if (tempSymStruct.type().equals("symb")) {

						for (int q = 0; q < tempSymStructList.size(); q++) {

							Struct tempEntStruct = tempEntStructList.get(q);
							if (tempEntStruct.type().equals("ent")) {

								// assumes that symb is a leaf struct! Need to
								// make more robust
								namesMap.put(tempSymStruct.prev1().toString(), tempEntStruct);

								tempEntStruct.struct().put("called", tempSymStruct.prev1().toString());
								break ploop;
							}
						}
					}
				}
			}

			// create new StructA and put in mx, along with score for
			// struct1_struct2 combo
			double parentDownPathScore = struct1.maxDownPathScore() * struct2.maxDownPathScore() * newScore;
			int parentNumUnits = struct1.numUnits() + struct2.numUnits();
			StructA<Struct, Struct> parentStruct = new StructA<Struct, Struct>(struct1, struct2, newType, newScore,
					mx.get(i).get(j), parentDownPathScore, parentNumUnits);
			
			// mx.get(i).set(j, parentStruct);
			mx.get(i).get(j).add(parentStruct);
		}
		// found a grammar rule match, move on to next mx column
		// *****actually, should keep going and keep scores!
		// break;

	}

	/**
	 * Entry point for depth first first. Initialize score mx of List's of
	 * MatrixPathNodes
	 * 
	 * @param structList,
	 *            the head, at mx position (len-1, len-1)
	 * @return
	 */
	public static int ArrayDFS(StructList structList) {
		// ArrayList<MatrixPathNode> mxPathNodeList = new
		// ArrayList<MatrixPathNode>();
		// fill in the list of MatrixPathNode's from structList

		// highest score encountered so far in list
		double highestDownScore = 0;
		int highestDownScoreIndex = 0;

		List<Struct> structArList = structList.structList();

		int structListSz = structArList.size();

		for (int i = 0; i < structListSz; i++) {
			Struct curStruct = structArList.get(i);

			double ownScore = curStruct.score();
			// create appropriate right and left Node's

			MatrixPathNode curMxPathNode = new MatrixPathNode(curStruct);

			// put path into corresponding Struct, the score along the path
			// down from here
			double pathScore = ArrayDFS(curMxPathNode);
			curStruct.set_maxDownPathScore(pathScore);

			if (pathScore > highestDownScore) {
				highestDownScore = pathScore;
				highestDownScoreIndex = i;
			}
			System.out.println("pathScore: " + pathScore);
		}

		structList.set_highestDownScoreIndex(highestDownScoreIndex);
		return highestDownScoreIndex;
	}

	/**
	 * depth-first-search with arrays construct Keep track of path via tree of
	 * MatrixPathNode's, and the scores thus far through the tree
	 * 
	 * @param mxPathNode
	 *            MatrixPathNode, corresponding to current Struct
	 * @return score
	 */
	// combine iteration of arraylist and recursion
	public static double ArrayDFS(MatrixPathNode mxPathNode) {

		Struct mxPathNodeStruct = mxPathNode.curStruct();
		StructList structList = mxPathNodeStruct.StructList();

		// highest down score encountered so far in list
		double highestDownScore = 0;
		// System.out.println(mxPathNodeStruct);
		// if structList == null at this point, it means the Struct is leaf, so
		// can return
		if (structList == null)
			return 1;

		int highestDownScoreIndex = structList.highestDownScoreIndex();
		// Iterator<Struct> structListIter = structList.iterator();

		// maintain index in list of highest score
		// don't iterate through if scores already computed
		if (highestDownScoreIndex == -1) {
			List<Struct> structArList = structList.structList();

			int structListSz = structArList.size();
			// int tempHighestDownScoreIndex = 0;

			// score so far along this path corresponding to this Node (not
			// Struct!)
			// double scoreSoFar = mxPathNode.scoreSoFar();

			for (int i = 0; i < structListSz; i++) {

				Struct struct = structArList.get(i);

				double structScore = struct.score();
				// highest score down from this Struct
				double tempDownScore = structScore;

				// don't like instanceof here
				if (struct instanceof StructA) {

					// System.out.print(struct.type());
					// System.out.print("[");
					// don't know type at compile time
					if (struct.prev1() instanceof Struct) {
						// create new MatrixPathNode,
						// int index, double ownScore, double scoreSoFar,

						// leftMxPathNode corresponds to Struct struct.prev1()
						MatrixPathNode leftMxPathNode = new MatrixPathNode((Struct) struct.prev1());
						tempDownScore *= ArrayDFS(leftMxPathNode);
					}

					//
					if (struct.prev2() instanceof Struct) {
						// System.out.print(", ");
						// dfs((Struct) struct.prev2());
						// construct new MatrixPathNode for right child
						MatrixPathNode rightMxPathNode = new MatrixPathNode((Struct) struct.prev2());
						tempDownScore *= ArrayDFS(rightMxPathNode);
					}

					// reached leaf. Add score to mxPathNode being passed in,
					// return own score
					if (struct.prev1() instanceof String) {
						// System.out.print(struct.prev1());
						// do nothing because String leaves don't count towards
						// score
					}
					if (struct.prev2() instanceof String) {
						
						// if (!struct.prev2().equals(""))
						// System.out.print(", ");
						// System.out.print(struct.prev2());
					}

					// System.out.print("]");
				} else if (struct instanceof StructH) {

					// System.out.print(struct.toString());
					ArrayList<Struct> childrenStructList = struct.children();

					if (childrenStructList != null && childrenStructList.size() > 0){
						//return 1;
					
					// System.out.print("[");
					for (int j = 0; j < childrenStructList.size(); j++) {
						// System.out.print(childRelation.get(j) + " ");
						// dfs(childrenStructList.get(j));

						Struct childStruct = childrenStructList.get(j);

						//double curStructScore = childStruct.score();
						// create MatrixPathNode for each child Struct
						MatrixPathNode childMxPathNode = new MatrixPathNode(childStruct);

						tempDownScore *= ArrayDFS(childMxPathNode);
					}
				}
					// System.out.print("]");
				}
				if (tempDownScore > highestDownScore) {
					highestDownScore = tempDownScore;
					highestDownScoreIndex = i;
				}

				mxPathNodeStruct.set_maxDownPathScore(tempDownScore);

			} // end for loop through structList

			// set highestDownScoreIndex
			structList.set_highestDownScoreIndex(highestDownScoreIndex);
		} else {
			highestDownScoreIndex = structList.highestDownScoreIndex();
			highestDownScore = structList.structList().get(highestDownScoreIndex).maxDownPathScore();

		}
		return highestDownScore;
	}

	/**
	 * write unknown words to file to classify them
	 * 
	 * @throws IOException
	 */
	public static void writeUnknownWordsToFile() throws IOException {
		Files.write(unknownWordsFile, unknownWords, Charset.forName("UTF-8"));
	}

	/**
	 * write unknown words to file to classify them
	 * 
	 * @throws IOException
	 */
	public static void writeParsedExprToFile() throws IOException {
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
	 * 
	 * @param str
	 *            is string of all input to be processed.
	 * @return array of sentence Strings
	 */
	public static String[] preprocess(String inputStr) {

		ArrayList<String> sentenceList = new ArrayList<String>();

		String[] wordsArray = inputStr.replaceAll("([^.,!:]*)([.|,|:|!]{1})", "$1 $2").split("\\s+");
		int wordsArrayLen = wordsArray.length;

		// use StringBuilder!
		StringBuilder sentenceBuilder = new StringBuilder();
		// String newSentence = "";
		String curWord;

		boolean inTex = false; // in latex expression?
		boolean madeReplacement = false;
		boolean toLowerCase = true;
		boolean inParen = false; // in parenthetical remark?

		for (int i = 0; i < wordsArrayLen; i++) {

			curWord = wordsArray[i];

			if (!inTex) {
				if (inParen || curWord.matches("\\([^)]*\\)")) {
					continue;
				} else if (curWord.matches("\\([^)]*")) {
					inParen = true;
					continue;
				} else if (inParen && curWord.matches("[^)]*)")) {
					inParen = false;
					continue;
				}
			}

			if (!inTex && curWord.matches("\\$.*") && !curWord.matches("\\$[^$]+\\$.*")) {
				inTex = true;
			} else if (inTex && curWord.contains("$")) {
				// }else if(curWord.matches("[^$]*\\$|\\$[^$]+\\$.*") ){
				inTex = false;
				toLowerCase = false;
			} else if (curWord.matches("\\$[^$]+\\$.*")) {
				toLowerCase = false;
			}

			// fluff phrases all start in posMap
			String curWordLower = curWord.toLowerCase();
			if (posMap.containsKey(curWordLower)) {
				String pos = posMap.get(curWordLower);
				String[] posAr = pos.split("_");
				String tempWord = curWord;

				int j = i;
				// potentially a fluff phrase
				if (posAr[posAr.length - 1].equals("comp") && j < wordsArrayLen - 1) {
					// keep reading in string characters, until there is no
					// match
					tempWord += " " + wordsArray[++j];

					while (posMap.containsKey(tempWord.toLowerCase()) && j < wordsArrayLen - 1) {
						tempWord += " " + wordsArray[++j];
					}

					String replacement = fluffMap.get(tempWord.toLowerCase());
					if (replacement != null) {
						sentenceBuilder.append(" " + replacement);
						madeReplacement = true;
						i = j;
					}
					// curWord += wordsArray[++i];
				}
			}

			// if composite fluff word ?? already taken care of
			if (!madeReplacement && !curWord.matches("\\.|,|!") && !fluffMap.containsKey(curWord.toLowerCase())) {
				// if (!curWord.matches("\\.|,|!") &&
				// !fluffMap.containsKey(curWord)){
				if (inTex || !toLowerCase) {
					sentenceBuilder.append(" " + curWord);
					toLowerCase = true;
				} else
					sentenceBuilder.append(" " + curWord.toLowerCase());
			}
			madeReplacement = false;

			if (curWord.matches("\\.|,|!") || i == wordsArrayLen - 1) {

				if (!inTex) {
					sentenceList.add(sentenceBuilder.toString());
					sentenceBuilder.setLength(0);
				} else {
					sentenceBuilder.append(curWord);
				}
			}

		}
		return sentenceList.toArray(new String[0]);
	}

}
