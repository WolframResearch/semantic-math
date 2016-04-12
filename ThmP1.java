import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.ListIterator;

/* 
 * contains hashtable of entities (keys) and properties (values)
 */

public class ThmP1 {
	
	//should all be StructH's, since these are ent's
	private HashMap<String, Struct> namesMap;

	//private static HashMap<String, ArrayList<String>> entityMap = Maps.entityMap;
	//map of structures, for all, disj,  etc
	private static HashMap<String, String> structMap;
	private static HashMap<String, String> anchorMap;
	//parts of speech map, e.g. "open", "adj"
	private static HashMap<String, String> posMap;
	//fluff words, e.g. "the", "a"
	private static HashMap<String, String> fluffMap;
		
	private static HashMap<String, String> mathObjMap;
	//map for composite adjectives, eg positive semidefinite
	//value is regex string to be matched
	private static HashMap<String, String> adjMap;
	
	
	//part of speech, last resort after looking up entity property maps
	//private static HashMap<String, String> pos;
	

	public static void buildMap(){
		Maps.buildMap();
		
		structMap = Maps.structMap;
		anchorMap = Maps.anchorMap;
		posMap = Maps.posMap;
		fluffMap = Maps.fluffMap;
		mathObjMap = Maps.mathObjMap;
		adjMap = Maps.adjMap;
	}
	
	//keywords: given, with, 
	// has, is, 
	//if ... then
	public ArrayList<Struct> tokenize(String[] str){
		//go through list twice, build hashmaps second time		
		
		//tokenize first. compound words?
		//find the prepositions
		
		//find if, conditionals, 
		//first run through sentence, find structure
		//if.. then, something... is/has...
		
		//EntityProperty's found so far
		//...will include properties like symbol name in hashtable
		//hashmap contains, e.g. (char, p)

		//....let ... be, given... 
		//.....change to arraylist of Pairs, create Pair class
		//LinkedHashMap<String, String> linkedMap = new LinkedHashMap<String, String>();
		
		//list of indices of "proper" math objects, e.g. "field", but not e.g. "pair" 
		ArrayList<Integer> mathIndexList = new ArrayList<Integer>();
		//list of indices of anchor words, e.g. "of"
		ArrayList<Integer> anchorList = new ArrayList<Integer>();
		
		//list of given names, like F in "field F", for bookkeeping later
		//hashmap contains <name, entity> pairs
		//need to incorporate multi-letter names, like sigma
		namesMap = new HashMap<String, Struct>();

		//list of each word with their initial type, adj, noun, 
		ArrayList<Pair> pairs = new ArrayList<Pair>();		
		boolean addIndex = true; //whether to add to pairIndex
		int pairIndex = 0;
		for(int i = 0; i < str.length; i++){	
			
			String curWord = str[i];
			int strlen = str[i].length();
						
			//primitive way to handle plural forms: if ends in "s"
			String singular = "";
			if(strlen > 0 && curWord.charAt(strlen-1) == 's'){
				singular = curWord.substring(0, strlen-1);
			}
			
			if(mathObjMap.containsKey(curWord) || 
					mathObjMap.containsKey(singular)){				
				int k = 1;
				
				//if composite math noun, eg "finite field"
				while(i-k > -1 && mathObjMap.containsKey(str[i-k] +" "+ curWord) &&
						mathObjMap.get(str[i-k] +" "+ curWord).equals("mathObj")){
					
					//remove previous pair from pairs if it has new match
					//pairs.size should be > 0, ie previous word should be classified already
					if(pairs.size() > 0 && 
							pairs.get(pairs.size()-1).word().equals(str[i-k])){
						
						//remove from mathIndexList if already counted
						if(mathObjMap.containsKey(pairs.get(pairs.size()-1).word())){
							mathIndexList.remove(mathIndexList.size()-1);
						}
						pairs.remove(pairs.size()-1);
						
						addIndex = false;
					}
					
					curWord = str[i-k] + " " + curWord;
					k++;
				}
				Pair pair = new Pair(curWord, "mathObj");
				pairs.add(pair);
				mathIndexList.add(pairs.size()-1);
				
			}
			
			else if(anchorMap.containsKey(curWord)){
				Pair pair = new Pair(curWord, "anchor");
				anchorList.add(pairIndex);
				pairs.add(pair);
			}
			//if adjective
			else if(posMap.containsKey(curWord)){
								
				//composite words, such as "for all",
				String temp = curWord, pos = curWord;
				String tempPos = posMap.get(temp);
				
				while(tempPos.length() > 4 &&
						tempPos.substring(tempPos.length()-4, tempPos.length()).equals("COMP") 
						&& i < str.length-1){
					
					curWord = temp;
					temp = temp + " " + str[i+1];
					if(!posMap.containsKey(temp)){
						break;
					}
					tempPos = posMap.get(temp);
					i++;
				}				
				
				Pair pair;
				if(posMap.containsKey(temp)){
					pos = posMap.get(temp);
					pair = new Pair(temp, pos);

				}else{
					pos = posMap.get(curWord);
					pair = new Pair(curWord, pos);
				}
				
				int pairsSize = pairs.size();
				//if adverb-adj pair, eg clearly good
				if(pairs.size() > 0 && posMap.get(curWord).equals("adj") && 
						pairs.get(pairsSize-1).pos().equals("adverb") ){
					curWord = pairs.get(pairsSize-1).word() + " " + curWord;
					//remove previous Pair
					pairs.remove(pairsSize - 1);
					pair = new Pair(curWord, "adj");
				}
				
				pairs.add(pair);
			}
			//check again for verbs ending in 'es' & 's'			
			else if(strlen > 0 && curWord.charAt(strlen-1) == 's' &&
					posMap.containsKey(str[i].substring(0, strlen-1)) &&
					posMap.get(str[i].substring(0, strlen-1)).equals("verb")){
					
				Pair pair = new Pair(str[i], "verb");
				pairs.add(pair);					
			}
			else if(strlen > 0 && curWord.charAt(strlen-1) == 's' &&
					str[i].charAt(str[i].length()-2) == 'e' &&
					posMap.containsKey(str[i].substring(0, strlen-2)) &&
					posMap.get(str[i].substring(0, strlen-2)).equals("verb")){
				Pair pair = new Pair(str[i], "verb");
				pairs.add(pair);					
				
			}
			//adverbs that end with -ly that haven't been screened off before
			else if(strlen > 1 && curWord.substring(strlen-2, strlen).equals("ly")){
				Pair pair = new Pair(str[i], "adverb");
				pairs.add(pair);
			}
			//participles and gerunds. Need special list for words such as "given"
			else if(strlen > 1 && curWord.substring(strlen-2, strlen).equals("ed")
					&& posMap.containsKey(str[i].substring(0, strlen-2))
					&& posMap.get(str[i].substring(0, strlen-2)).equals("verb") ){
				//if next word is "by"
				
				Pair pair = new Pair(str[i], "parti");
				pairs.add(pair);
			}
			else if(strlen > 2 && curWord.substring(strlen-3, strlen).equals("ing")					
					&& (posMap.containsKey(curWord.substring(0, strlen-3))
							&& posMap.get(curWord.substring(0, strlen-3)).equals("verb") 
							//verbs ending in "e"
							|| (posMap.containsKey(curWord.substring(0, strlen-3)+'e') 
									&& posMap.get(curWord.substring(0, strlen-3)+'e').equals("verb")))){
				Pair pair = new Pair(str[i], "gerund");
				pairs.add(pair);
			}
			else if(curWord.matches("[a-zA-Z]")){
				//variable/symbols
	
				Pair pair = new Pair(str[i], "symb");
				pairs.add(pair);
				
			}
			//Get numbers. Incorporate written-out numbers, eg "two"
			else if(curWord.matches("^//d+$")){
				Pair pair = new Pair(str[i], "num");
				pairs.add(pair);
			}else{ //try to minimize this case.
				
				System.out.println("word not in dictionary: " + str[i]);
				pairs.add(new Pair(curWord, ""));
			}
			
			if(addIndex){
				pairIndex++;
			}
			addIndex = true;
		}
		
		//map of math entities, has mathObj + ppt's
		ArrayList<StructH<HashMap<String, String>>> mathEntList = 
				new ArrayList<StructH<HashMap<String, String>>>();
		
		//second run, combine adj with math nouns
		for(int j = 0; j < mathIndexList.size(); j++){
			
			int index = mathIndexList.get(j);
			String mathObj = pairs.get(index).word();
			pairs.get(index).set_pos(String.valueOf(j));
			
			StructH<HashMap<String, String>> tempStructH =
					new StructH<HashMap<String, String>>("ent");
			HashMap<String, String> tempMap = new HashMap<String, String>();
			tempMap.put("name", mathObj);
			
			//look right two places in pairs, if symbol found, add it to namesMap
			//if it's the given name for an ent. 
			int pairsSize = pairs.size();
			if(index + 1 < pairsSize && pairs.get(index+1).pos().equals("symb")){
				pairs.get(index+1).set_pos(String.valueOf(j));
				String givenName = pairs.get(index+1).word();
				tempMap.put("called", givenName);
				namesMap.put(givenName, tempStructH);
				
			}else if((pairsSize+2 < pairsSize && pairs.get(index+2).pos().equals("symb"))){
				pairs.get(index+2).set_pos(String.valueOf(j));
				String givenName = pairs.get(index+2).word();
				tempMap.put("called", givenName);
				namesMap.put(givenName, tempStructH);
			}			
			
			//look to left and right
			int k = 1;			
			//combine multiple adjectives into entities
			//...get more than adj ... multi-word descriptions
			//set the pos as the current index in mathEntList		
			//adjectives or determiners
			while(index-k > -1 && pairs.get(index - k).pos().matches("adj|det") ){
				String curWord = pairs.get(index - k).word();
				//look for composite adj (two for now)
				if(index-k-1 > -1 && adjMap.containsKey(curWord)){
					//if composite adj
					//////////develop adjMap further
					if(pairs.get(index-k-1).word().matches(adjMap.get(curWord))){
						curWord = pairs.get(index-k-1).word() + " " + curWord;
						//mark pos field to indicate entity
						pairs.get(index - k).set_pos(String.valueOf(j));
						k++;
					}
				}
				
				tempMap.put(curWord, "ppt");				
				//mark the pos field in those absorbed pairs as index in mathEntList				
				pairs.get(index - k).set_pos(String.valueOf(j));				
				k++;
			}	
			
			//combine multiple adj connected by "and/or"
			while(index-k-1 > -1 && pairs.get(index - k).pos().matches("or|and")){
				if(pairs.get(index - k - 1).pos().equals("adj")){
					//set pos() of or/and to the right index
					pairs.get(index - k).set_pos(String.valueOf(j));
					String curWord = pairs.get(index - k - 1).word();
					tempMap.put(curWord, "ppt");	
					pairs.get(index - k - 1).set_pos(String.valueOf(j));	
				}
				k++;
			}
			
			//look forwards
			k = 1;
			while(index+k < pairs.size() && pairs.get(index + k).pos().equals("adj") ){				
				///implement same as above
				
				tempMap.put(pairs.get(index - k).word(), "ppt");
				pairs.get(index + k).set_pos(String.valueOf(j));
				k++;
			}			
			
			tempStructH.set_struct(tempMap);
			mathEntList.add(tempStructH);
		}
		
		//combine anchors into entities. Such as "of," "has"
		for(int j = 0; j < anchorList.size(); j++){
			int index = anchorList.get(j);
			String anchor = pairs.get(index).word();			
			
			//the expression before this anchor is an entity
			if(index > 0 && pairs.get(index - 1).pos().matches("\\d+$") ){
				int mathObjIndex = Integer.valueOf(pairs.get(index - 1).pos());
				StructH<HashMap<String, String>> tempStruct = mathEntList.get(mathObjIndex);

				//combine entities, like in case of "of"
				switch(anchor){
				case "of":	
					//ent of ent
					if(index + 1 < pairs.size()){
						Pair nextPair = pairs.get(index + 1);
						if(nextPair.pos().matches("\\d+$")){
							pairs.get(index).set_pos(nextPair.pos());
							Struct childStruct = mathEntList.get(Integer.valueOf(nextPair.pos()));	
							tempStruct.add_child(childStruct, "of"); 
							//set to null instead of removing, to keep indices right
							mathEntList.set(Integer.valueOf(nextPair.pos()), null);
						}
					}
	
					break;				
				}
				
			}//if the previous token is not an ent
			else{
				//set anchor to its normal part of speech word, like "of" to pre				
				pairs.get(index).set_pos(posMap.get(anchor));
			}

		} 		
		
		//arraylist of structs
		ArrayList<Struct> structList = new ArrayList<Struct>();		

		//Iterator<StructH<HashMap<String, String>>> mathEntIter = mathEntList.iterator();
		ListIterator<Pair> pairsIter = pairs.listIterator();
		
		String prevPos = "-1";
		//use anchors (of, with) to gather terms together into entities
		while(pairsIter.hasNext() ){						
			Pair curPair = pairsIter.next();
			
			if(curPair.pos().matches("^\\d+$")){
				
				if(curPair.pos().equals(prevPos)){
					continue;
				}
				
				StructH<HashMap<String, String>> curStruct = 
						mathEntList.get(Integer.valueOf(curPair.pos()));
				
				if(curStruct != null){					
					structList.add(curStruct);
				}
				
				prevPos = curPair.pos();
				
			}else{
				//current word hasn't classified into an ent
				//////
				
				int structListSize = structList.size();
				
				//combine adverbs into verbs/adjectives, look 2 phrases before
				if(curPair.pos().equals("adverb")){					
					
					if(structListSize > 1 && structList.get(structListSize-2).type().matches("verb")){
						StructA<?, ?> verbStruct = (StructA<?, ?>)structList.get(structListSize-2);
						//verbStruct should not have prev2, also prev2 type should be String
						verbStruct.set_prev2(curPair.word());
						continue;
					}
					else if(structListSize > 0 && structList.get(structListSize-1).type().equals("verb")){
						StructA<?, ?> verbStruct = (StructA<?, ?>)structList.get(structListSize-1);
						verbStruct.set_prev2(curPair.word());						
						continue;
					}					
				}
				
				String curWord = curPair.word();
				
				//is leaf if prev2 is empty string ""
				StructA<String, String> newStruct = 
						new StructA<String, String>(curWord, "", curPair.pos());
				
				if(curPair.pos().equals("adj")){
					if(structListSize > 0 && structList.get(structListSize-1).type().equals("adverb")){
						Struct adverbStruct = structList.get(structListSize-1);
						//adverbStruct.set_prev2(curPair.word());
						newStruct.set_prev2(adverbStruct);
						//remove the adverb Struct
						structList.remove(structListSize - 1); 
					}
				}												
				
				structList.add(newStruct);
				
			}
			
			//add entity to entitiesFound
			//empty ppt map, to be added to later
			
			//use last noun as entity, if bunch of different nouns in sequence
			//unless compound noun
			
			//look in hash map the longest possible entity (most descriptive)
			//like field vs field extension					
			
			//if property found, combine to make entity property
			//...try multiple entities instead of first one found
			//add to list of properties 
			//if adjective, group with nearest entity
			//after going through entities in sentence first
			//also templating, "is" is a big hint word
			//add as property					
			
			//tokens.add("");
			
		}
		
		//categorize each term		
		
		//have to look ahead
		//return arraylist of hashmaps
		return structList;
	}
	
	//or, of are names, their types are struct
	//use arraylist of hashmaps. each hashmap has name (field, or), 
	//type (entity, struct, ppt), 
	//called (F), 
	//child,  ppt (char p, alg closed, in G), 
	
	/* Takes in LinkedHashMap of entities/ppt, and connectives
	 * parse using structMap, get big structures such as "is", "has"
	 * Chart parser.
	 */
	public void parse(ArrayList<Struct> inputList ){
		int len = inputList.size();
		
		//track the most recent entity for use for pronouns		
		Struct recentEnt = null;
		
		ArrayList<ArrayList<Struct>> mx = 
				new ArrayList<ArrayList<Struct>>(len);	
		
		for(int l = 0; l < len; l++){
				mx.add(new ArrayList<Struct>(len));
				for(int i = 0; i < len; i++){
					mx.get(l).add(null);
				}
		}
		
		for(int j = 0; j < len; j++){
			
			//fill in diagonal elements
			mx.get(j).set(j, inputList.get(j));
			
			for(int i = j - 1; i >= 0; i--){
				for(int k = j-1; k >= i; k--){
					// i,k, and k+1,j
					
					Struct struct1 = mx.get(i).get(k);
					Struct struct2 = mx.get(k+1).get(j);
					
					if(struct1 == null || struct2 == null){
						continue;
					}					
					
					//look ahead 2 places for "is," "has" or general verb 
					//precedence rules for or/and etc
					/* 
					if(struct1.type().matches("or|and") ){						
						for(int l = 1; l < 3; l++){
							if(j+l < len && inputList.get(j+l).type().matches("is|has") ){
								continue;
							}
						}
					}  */
					
					//combine/reduce types, like or_ppt, for_ent, in_ent
					String type1 = struct1.type();
					String type2 = struct2.type();
					
					if(type1.equals("ent")){
						recentEnt = struct1;
					}
					
					//if pronoun, refers to most recent ent
					if(type1.equals("pro") && struct1.prev2() != null &&
							struct1.prev2().equals("")){
						if(recentEnt != null){
							String tempName = recentEnt.struct().get("name");
							String name = tempName != null ? tempName : "";
							struct1.set_prev2(name);
						}						
					}
					
					if(type2.equals("ent")){
						recentEnt = struct2;
					}
					//look up combined in struct table, like or_ent
					//get value as name for new hash table, table with prev field
					//new type? entity, with extra ppt
					//name: or. combined ex: or_adj (returns ent), or_ent (ent)
					String combined = type1 + "_" + type2;
					
					//////////////search for tokens larger than immediate ones
					//in case A_or_A or A_and_A set the mx element right below to null
					//to set precedence, so A won't be grouped to others later
					if(i > 0 && i + 1 < len 
							&& (type1.matches("or|and") && type2.equals(mx.get(i-1).get(i-1).type()) )){	
						//type is expression, eg "a and b"
						StructA<Struct, Struct> parentStruct = 
								new StructA<Struct, Struct>(mx.get(i-1).get(i-1), struct2, type1);
							
						mx.get(i-1).set(j, parentStruct);
						mx.get(i+1).set(j, null);
						break;
					}
					
					if(structMap.containsKey(combined)){
						String newType = structMap.get(combined);
				
						//newChild means to fuse second entity into first one
						
						if(newType.equals("newchild")){
							//struct1 should be of type StructH to receive a child
							//assert ensures rule correctness
							assert struct1 instanceof StructH;
							
							//get a deep copy of this StructH, since added children may not 
							//get used eventually
							Struct newStruct = struct1.copy();
							//add to child relation, usually a preposition, eg "from", "over"
							//could also be verb, "consist", "lies"
							String childRelation = mx.get(k+1).get(k+1).prev1().toString();
							if(struct1 instanceof StructH){
								//why does this cast not trigger unchecked warning??
								((StructH<?>)newStruct).add_child(struct2, childRelation);
							}
							//////////////////////////////////
							recentEnt = newStruct;
							
							mx.get(i).set(j, newStruct);
							
						}else{
							//symbol only occurs in StructA /////remove downcast!
							//if symbol and a given name to some entity
							//use "called" to relate entities
							if(type1.equals("symb") ){
								String entKey = (String)struct1.prev1();
								if(namesMap.containsKey(entKey)){
									Struct entity = namesMap.get(entKey);
									struct1.set_prev2(entity.struct().get("type"));
									
									//modify type of struct1 and add struct to mx
									struct1.set_type(entity.struct().get("type"));
								}
							}
							//add to namesMap if letbe defines a name for an ent
							if(newType.equals("letbe") && mx.get(i+1).get(k) != null && mx.get(k+2).get(j) != null){
								//temporary patch!   Rewrite StructA to avoid cast
								//assert(struct1 instanceof StructA); assert(struct2 instanceof StructA);
								//get previous nodes								
								
								if( mx.get(i+1).get(k).type().equals("symb") &&
										mx.get(k+2).get(j).type().equals("ent") ){

									namesMap.put(mx.get(i+1).get(k).prev1().toString(), 
											mx.get(k+2).get(j) );
									
									mx.get(k+2).get(j).
									struct().put("called", mx.get(i+1).get(k).prev1().toString());
								}
							}
							
							//create new StructA and put in mx
							StructA<Struct, Struct> parentStruct = 
									new StructA<Struct, Struct>(struct1, struct2, newType);
								
							mx.get(i).set(j, parentStruct);
							
						}
						//found a grammar rule match, move on to next mx column
						break;
					}
					
				}
				
			}			
		}
		
		//string together the most parsed pieces
		LinkedList<Struct> parsedStructList = new LinkedList<Struct>();
		int i = 0, j = len - 1;
		while(j > -1){
			i = 0;
			while(mx.get(i).get(j) == null){
				i++;
				//some diagonal elements can be set to null on purpose 
				if(i >= j){
					continue;
				}
			}
			
			parsedStructList.add(0, mx.get(i).get(j));
			//a singleton on the diagonal
			if(i == j){
				j--;
			}else{
				j = i - 1;
			}
		}
		
		//print out the system
		for(int k = 0; k < parsedStructList.size(); k++){
			dfs(parsedStructList.get(k));		
			System.out.print(" -- ");
		}
		System.out.println();
		
	}
	
	public void dfs(Struct struct){
		//don't like instanceof here
		if(struct instanceof StructA){
			
			System.out.print(struct.type());
			
			System.out.print("[");
			//don't know type at compile time			
			if(struct.prev1() instanceof Struct){
				//((StructA<Struct, ?>)struct).prev1().type();
				//if(!((StructA<Struct, ?>)struct).prev1().type().equals(struct.type())){
				
				dfs((Struct)struct.prev1());				
			}			
			
			//if(struct.prev2() != null && !struct.prev2().equals(""))
			//	System.out.print(", ");
			
			if(((StructA<?, ?>)struct).prev2() instanceof Struct){
				//avoid printing is[is], ie case when parent has same type as child
				//if(!((StructA<?, Struct>)struct).prev2().type().equals(struct.type()))
				System.out.print(", ");
				dfs((Struct)struct.prev2());
			}			
			
			if(struct.prev1() instanceof String){
				System.out.print(struct.prev1());
			}
			if(struct.prev2() instanceof String){
				if(!struct.prev2().equals(""))
					System.out.print(", ");
				System.out.print(struct.prev2());
			} 
			
			System.out.print("]");
		}else if(struct instanceof StructH){
			
			System.out.print(struct.toString());
			
			ArrayList<Struct> children = ((StructH<?>)struct).children();
			ArrayList<String> childRelation = ((StructH<?>)struct).childRelation();
			
			if(children == null || children.size() == 0)
				return;
			
			System.out.print("[");
			for(int i = 0; i < children.size(); i++){
				System.out.print(childRelation.get(i) + " ");
				dfs(children.get(i));
			}
			System.out.print("]");
		}
	}		
	
	/*
	 * Preprocess. Remove fluff words. The, a, 
	 * Remove endings, -ing, -s
	 */
	public String[] preprocess(String[] str){
		ArrayList<String> wordList = new ArrayList<String>();
		for(int i = 0; i < str.length; i++){
			if(!fluffMap.containsKey(str[i])){
				wordList.add(str[i]);
			}
		}		
		return wordList.toArray(new String[0]);
	}
	
	
}
